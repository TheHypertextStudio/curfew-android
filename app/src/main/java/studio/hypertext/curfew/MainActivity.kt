package studio.hypertext.curfew

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import studio.hypertext.curfew.alarm.AlarmRecurrencePolicy
import studio.hypertext.curfew.alarm.WakeScheduleResolver
import studio.hypertext.curfew.callback.CallbackConfigurationRepository
import studio.hypertext.curfew.account.BrowserOAuthEnrollment
import studio.hypertext.curfew.account.NativeDeviceProofAuthenticator
import studio.hypertext.curfew.account.OAuthTokenExchange
import studio.hypertext.curfew.persistence.CurfewPreferenceState
import studio.hypertext.curfew.persistence.CurfewPreferences
import studio.hypertext.curfew.platform.AlarmRuntimeRepository
import studio.hypertext.curfew.platform.AlarmSchedulingCoordinator
import studio.hypertext.curfew.platform.AndroidAlarmClockGateway
import studio.hypertext.curfew.platform.PerpetualAlarmService
import studio.hypertext.curfew.readiness.AlarmCapabilities
import studio.hypertext.curfew.readiness.AlarmReadiness
import studio.hypertext.curfew.readiness.AlarmReadinessEvaluator
import studio.hypertext.curfew.ui.AlarmRingingScreen
import studio.hypertext.curfew.ui.CurfewApp
import studio.hypertext.curfew.ui.theme.CurfewTheme
import studio.hypertext.curfew.sync.WakeSyncScheduler
import studio.hypertext.curfew.sync.WakeStatusPollScheduler
import studio.hypertext.curfew.sync.WakeStatusPublisher

class MainActivity : ComponentActivity() {
    private lateinit var preferences: CurfewPreferences
    private var capabilityEpoch by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PerpetualAlarmService.ensureChannels(this)
        WakeSyncScheduler.install(this)
        preferences = CurfewPreferences(this)
        if (handleOAuthCallback(intent)) {
            setIntent(Intent(this, MainActivity::class.java))
        }
        setContent {
            @Suppress("UNUSED_EXPRESSION")
            capabilityEpoch
            CurfewTheme {
                if (intent.getBooleanExtra(EXTRA_SHOW_ALARM, false)) {
                    AlarmRingingScreen(
                        attempt = intent.getIntExtra(
                            PerpetualAlarmService.EXTRA_ATTEMPT_NUMBER,
                            1,
                        ),
                        maximumAttempts = intent.getIntExtra(
                            PerpetualAlarmService.EXTRA_MAXIMUM_ATTEMPTS,
                            3,
                        ),
                        conditionLabel = intent.getStringExtra(
                            PerpetualAlarmService.EXTRA_CONDITION_LABEL,
                        ) ?: "your wake condition",
                        actionLabel = intent.getStringExtra(
                            PerpetualAlarmService.EXTRA_ACTION_URL,
                        )?.let { "Open work surface" },
                        onOpenAction = {
                            intent.getStringExtra(PerpetualAlarmService.EXTRA_ACTION_URL)?.let {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                            }
                        },
                    )
                } else {
                    val preferenceState = preferences.state.collectAsStateWithLifecycle(
                        initialValue = CurfewPreferenceState(
                            null,
                            false,
                            false,
                            false,
                            null,
                            false,
                        ),
                    ).value
                    val readiness = currentReadiness(preferenceState)
                    CurfewApp(
                        readiness = readiness,
                        accountEnrolled = preferenceState.accountEnrolled,
                        accountSignedIn = preferenceState.accountSignedIn,
                        onGrantExactAlarm = ::requestExactAlarmAccess,
                        onGrantNotifications = ::requestNotifications,
                        onRepairAlarmChannel = ::openAlarmChannelSettings,
                        onPlayTestSound = ::playTestSound,
                        onAcknowledgeLimitations = {
                            lifecycleScope.launch { preferences.acknowledgeLimitations() }
                        },
                        onOpenAccount = {
                            startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://curfew.hypertext.studio/account"),
                                ),
                            )
                        },
                        onEnrollAccount = {
                            lifecycleScope.launch {
                                runCatching {
                                    BrowserOAuthEnrollment(this@MainActivity).begin()
                                }.onFailure {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Account enrollment is temporarily unavailable.",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        },
                        onSaveCallback = { label, endpoint, actionUrl ->
                            lifecycleScope.launch {
                                runCatching {
                                    CallbackConfigurationRepository(this@MainActivity).save(
                                        label,
                                        endpoint,
                                        actionUrl,
                                    )
                                }.onSuccess { definition ->
                                    preferences.selectCallback(definition.callbackId)
                                }.onFailure {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "The callback must use a valid HTTPS endpoint.",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        },
                        onArm = ::armAlarm,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        capabilityEpoch += 1
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (handleOAuthCallback(intent)) {
            setIntent(Intent(this, MainActivity::class.java))
        } else {
            setIntent(intent)
            recreate()
        }
    }

    private fun handleOAuthCallback(callbackIntent: Intent): Boolean {
        val callback = callbackIntent.data ?: return false
        if (
            callback.scheme != "studio.hypertext.curfew" ||
            callback.host != "oauth" ||
            callback.path != "/callback"
        ) {
            return false
        }
        lifecycleScope.launch {
            runCatching { OAuthTokenExchange(this@MainActivity).exchange(callback) }
                .onSuccess {
                    capabilityEpoch += 1
                    Toast.makeText(
                        this@MainActivity,
                        "Signed in. Finish encryption enrollment before sync begins.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                .onFailure {
                    Toast.makeText(
                        this@MainActivity,
                        "Sign-in could not be completed. Please try again.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
        return true
    }

    private fun currentReadiness(preferenceState: CurfewPreferenceState): AlarmReadiness {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val fullScreenAllowed = Build.VERSION.SDK_INT < 34 ||
            notificationManager.canUseFullScreenIntent()
        return AlarmReadinessEvaluator().evaluate(
            AlarmCapabilities(
                canScheduleExactAlarms = getSystemService(AlarmManager::class.java)
                    .canScheduleExactAlarms(),
                notificationsGranted = notificationsGranted,
                fullScreenIntentAllowed = fullScreenAllowed,
                alarmChannelAudible = PerpetualAlarmService.alarmChannelIsAudible(this),
                testSoundCompleted = preferenceState.testSoundCompleted,
                limitationsAcknowledged = preferenceState.readinessAcknowledged,
            ),
        )
    }

    private fun requestExactAlarmAccess() {
        startActivity(
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun requestNotifications() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            NOTIFICATION_PERMISSION_REQUEST,
        )
    }

    private fun openAlarmChannelSettings() {
        startActivity(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, "curfew_perpetual_alarm"),
        )
    }

    private fun playTestSound() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val ringtone = RingtoneManager.getRingtone(this, uri)
        ringtone.play()
        window.decorView.postDelayed({ ringtone.stop() }, 2_000)
        lifecycleScope.launch { preferences.recordTestSound() }
    }

    private fun armAlarm(
        wakeTime: LocalTime,
        maximumAttempts: Int,
        ringMinutes: Int,
        quietMinutes: Int,
    ) {
        lifecycleScope.launch {
            val policy = runCatching {
                AlarmRecurrencePolicy(
                    maximumAttempts = maximumAttempts,
                    ringDuration = Duration.ofMinutes(ringMinutes.toLong()),
                    quietDuration = Duration.ofMinutes(quietMinutes.toLong()),
                )
            }.getOrElse {
                Toast.makeText(
                    this@MainActivity,
                    "That campaign would exceed Curfew’s two-hour limit.",
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            var day = LocalDate.now(zone)
            var start = WakeScheduleResolver().resolve(day, wakeTime, zone).toInstant()
            if (!start.isAfter(now)) {
                day = day.plusDays(1)
                start = WakeScheduleResolver().resolve(day, wakeTime, zone).toInstant()
            }
            val campaignId = UUID.randomUUID().toString()
            val deviceId = NativeDeviceProofAuthenticator(this@MainActivity).deviceId()
            val repository = AlarmRuntimeRepository(this@MainActivity)
            val prefs = preferences.state.first()
            val campaign = repository.create(
                campaignId,
                start,
                policy,
                setOf(deviceId),
                callbackId = prefs.selectedCallbackId,
            )
            val readiness = currentReadiness(prefs)
            runCatching {
                AlarmSchedulingCoordinator(AndroidAlarmClockGateway(this@MainActivity)).arm(
                    campaignId,
                    start,
                    readiness,
                )
            }.onSuccess {
                preferences.setPrimaryAlarmDevice(deviceId)
                WakeStatusPublisher(this@MainActivity).publish(campaign, Instant.now())
                WakeStatusPollScheduler.schedule(this@MainActivity, campaignId)
                Toast.makeText(
                    this@MainActivity,
                    "Wake campaign armed for $wakeTime.",
                    Toast.LENGTH_LONG,
                ).show()
            }.onFailure {
                Toast.makeText(
                    this@MainActivity,
                    "Finish alarm readiness before arming.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    companion object {
        const val EXTRA_SHOW_ALARM = "show_alarm"
        private const val NOTIFICATION_PERMISSION_REQUEST = 41
    }
}
