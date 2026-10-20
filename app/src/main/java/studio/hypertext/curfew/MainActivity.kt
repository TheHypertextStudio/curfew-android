package studio.hypertext.curfew

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import studio.hypertext.curfew.callback.AndroidWakeConditionSetup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import studio.hypertext.curfew.platform.AndroidAlarmSoundPreview
import studio.hypertext.curfew.readiness.AlarmSoundCheck
import studio.hypertext.curfew.ui.AlarmSoundCheckDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio.hypertext.curfew.callback.CallbackConfigurationRepository
import studio.hypertext.curfew.account.BrowserOAuthEnrollment
import studio.hypertext.curfew.account.AccountRecoveryState
import studio.hypertext.curfew.account.AndroidAccountRecovery
import studio.hypertext.curfew.account.NativeDeviceProofAuthenticator
import studio.hypertext.curfew.account.OAuthTokenExchange
import studio.hypertext.curfew.persistence.CurfewPreferenceState
import studio.hypertext.curfew.persistence.CurfewPreferences
import studio.hypertext.curfew.platform.AlarmRuntimeRepository
import studio.hypertext.curfew.platform.AndroidAlarmClockGateway
import studio.hypertext.curfew.platform.PerpetualAlarmService
import studio.hypertext.curfew.readiness.AlarmReadiness
import studio.hypertext.curfew.ui.AlarmRingingScreen
import studio.hypertext.curfew.ui.AlarmRecoveryScreen
import studio.hypertext.curfew.platform.AndroidOwnerRecovery
import studio.hypertext.curfew.alarm.OwnerRecoveryResult
import studio.hypertext.curfew.alarm.campaignStartedAt
import studio.hypertext.curfew.ui.CurfewApp
import studio.hypertext.curfew.ui.theme.CurfewTheme
import studio.hypertext.curfew.sync.WakeSyncScheduler
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRuntimeSnapshot
import studio.hypertext.curfew.alarm.AlarmRuntimeClock
import studio.hypertext.curfew.alarm.SunriseSchedule
import studio.hypertext.curfew.platform.SunriseScheduler
import studio.hypertext.curfew.protocols.CallbackDefinition

class MainActivity : ComponentActivity() {
    private lateinit var preferences: CurfewPreferences
    private val soundPreview by lazy { AndroidAlarmSoundPreview(this) }
    private val soundCheck by lazy {
        AlarmSoundCheck(soundPreview::play, soundPreview::stop) { preferences.setTestSoundConfirmed(true) }
    }
    private var testSoundJob: Job? = null
    private var capabilityEpoch by mutableIntStateOf(0)
    private var accountRecoveryState by mutableStateOf<AccountRecoveryState?>(null)
    private val sunrise by lazy { SunriseScheduler(this) }
    private var sunriseSaving by mutableStateOf(false)
    private var sunriseError by mutableStateOf<String?>(null)
    private var sunriseReadFailed by mutableStateOf(false)
    private var requestedAlarmSnapshot by mutableStateOf<AlarmRuntimeSnapshot?>(null)
    private var requestedAlarmLoaded by mutableStateOf(false)
    private var recoveryVisible by mutableStateOf(false)
    private val ownerRecoveryDelegate = lazy {
        AndroidOwnerRecovery(activity = this, onRecovered = { result ->
            recoveryVisible = false
            val message = when (result) {
                OwnerRecoveryResult.Recovered -> "Sunrise stopped through recovery."
                OwnerRecoveryResult.RecoveredNextAlarmUnavailable -> "Sunrise stopped. Check tomorrow’s alarm."
                OwnerRecoveryResult.NotActive -> "Sunrise is no longer active."
                OwnerRecoveryResult.NotSaved -> "Couldn’t save recovery. Sunrise is still active."
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        })
    }
    private val ownerRecovery by ownerRecoveryDelegate
    private val sunriseState by lazy {
        sunrise.state.catch {
            sunriseReadFailed = true
            sunriseError = "Sunrise settings could not be read. Restart before changing the alarm."
            emit(null)
        }
    }

    private val wakeConditionSetup by lazy { AndroidWakeConditionSetup(this) }
    private var conditionBusy by mutableStateOf(false)
    private var conditionMessage by mutableStateOf<String?>(null)
    private var pendingVerifierExport: CallbackDefinition? = null
    private val exportVerifier = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val definition = pendingVerifierExport
        pendingVerifierExport = null
        if (uri == null || definition == null) {
            conditionMessage = "Export canceled. Your check-in has not changed."
        } else {
            conditionBusy = true
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        requireNotNull(contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use {
                            it.write(Json.encodeToString(definition))
                        }
                    }
                    conditionMessage = "Setup file exported. Share it with your check-in service, then test the connection."
                } catch (failure: Exception) {
                    conditionMessage = "Couldn’t export the file. Choose another location and retry."
                } finally {
                    conditionBusy = false
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PerpetualAlarmService.ensureChannels(this)
        WakeSyncScheduler.install(this)
        preferences = CurfewPreferences(this)
        val requestedId = intent.getStringExtra(AndroidAlarmClockGateway.EXTRA_CAMPAIGN_ID)
        if (intent.getBooleanExtra(EXTRA_SHOW_ALARM, false) && requestedId != null) {
            lifecycleScope.launch {
                requestedAlarmSnapshot = runCatching {
                    AlarmRuntimeRepository(this@MainActivity).snapshot(requestedId)
                }.getOrNull()
                requestedAlarmLoaded = true
                if (requestedAlarmSnapshot?.state is AlarmCampaignState.Ringing ||
                    requestedAlarmSnapshot?.state is AlarmCampaignState.Quiet
                ) {
                    presentOverKeyguard()
                }
            }
        } else {
            requestedAlarmLoaded = true
        }
        accountRecoveryState = AndroidAccountRecovery(this).pendingState()
        if (handleOAuthCallback(intent)) {
            setIntent(Intent(this, MainActivity::class.java))
        }
        setContent {
            @Suppress("UNUSED_EXPRESSION")
            capabilityEpoch
            val savedAlarm = sunriseState.collectAsStateWithLifecycle(initialValue = null).value
            val runtime = remember { AlarmRuntimeRepository(this@MainActivity) }
            val bootId = remember(runtime) { runtime.currentBootId() }
            val campaignId = intent.getStringExtra(AndroidAlarmClockGateway.EXTRA_CAMPAIGN_ID)
                ?: savedAlarm?.occurrence?.campaignId
            val campaignFlow = remember(campaignId, runtime) {
                campaignId?.let { runtime.observeSnapshot(it) }
                    ?.catch {
                        sunriseError = "The current Sunrise could not be read."
                        emit(null)
                    } ?: flowOf(null)
            }
            val campaignSnapshot = remember(campaignFlow) { campaignFlow.map { true to it } }
                .collectAsStateWithLifecycle(initialValue = false to null).value
            val runtimeSnapshot = if (campaignSnapshot.first) campaignSnapshot.second else requestedAlarmSnapshot
            val campaign = runtimeSnapshot?.state
            val showAlarm = campaign is AlarmCampaignState.Ringing || campaign is AlarmCampaignState.Quiet
            var ringingCondition by remember(campaignId) { mutableStateOf<CallbackDefinition?>(null) }
            LaunchedEffect(campaignId) {
                ringingCondition = campaignId?.let { id ->
                    withContext(Dispatchers.IO) {
                        AlarmRuntimeRepository(this@MainActivity).callbackId(id)?.let {
                            CallbackConfigurationRepository(this@MainActivity).find(it)
                        }
                    }
                }
            }
            LaunchedEffect(showAlarm) {
                if (showAlarm) {
                    presentOverKeyguard()
                } else {
                    setShowWhenLocked(false)
                    setTurnScreenOn(false)
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            BackHandler(enabled = recoveryVisible) {
                ownerRecovery.close()
                recoveryVisible = false
            }
            LaunchedEffect(showAlarm, campaignId) {
                if (!showAlarm) {
                    recoveryVisible = false
                    if (ownerRecoveryDelegate.isInitialized()) ownerRecovery.alarmEnded()
                }
            }
            CurfewTheme {
                val soundCheckState = soundCheck.state.collectAsStateWithLifecycle().value
                AlarmSoundCheckDialog(soundCheckState,
                    onConfirm = { lifecycleScope.launch { soundCheck.confirm() } },
                    onReplay = ::playTestSound,
                    onCancel = ::cancelTestSound,
                )
                if (!requestedAlarmLoaded) {
                    Surface { Text("Loading Sunrise…") }
                } else if (showAlarm && recoveryVisible) {
                    AlarmRecoveryScreen(
                        conditionLabel = ringingCondition?.displayLabel ?: "your check-in",
                        credentialAvailable = ownerRecovery.credentialAvailable,
                        busy = ownerRecovery.busy,
                        error = ownerRecovery.error,
                        onBack = { ownerRecovery.close(); recoveryVisible = false },
                        onBeginHold = { reason -> campaignId?.let { ownerRecovery.beginHold(it, reason) } },
                        onCancelHold = ownerRecovery::cancelHold,
                        onConfirm = ownerRecovery::requestConfirmation,
                        onSecuritySettings = { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) },
                    )
                } else if (showAlarm) {
                    val clock = android.text.format.DateFormat.getTimeFormat(this@MainActivity)
                    AlarmRingingScreen(
                        time = clock.format(java.util.Date.from(requireNotNull(campaign).campaignStartedAt())),
                        nextRingTime = runtimeSnapshot.takeIf { it.state is AlarmCampaignState.Quiet }?.let {
                            AlarmRuntimeClock.nextTransitionAt(it, java.time.Instant.now(), android.os.SystemClock.elapsedRealtime(), bootId)
                                ?.let { next -> clock.format(java.util.Date.from(next)) }
                        },
                        onRecover = { recoveryVisible = true },
                        conditionLabel = ringingCondition?.displayLabel ?: "your check-in",
                        actionLabel = ringingCondition?.actionUrl?.let { "Open check-in" },
                        onOpenAction = {
                            ringingCondition?.actionUrl?.let {
                                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                                    .onFailure {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "No app could open this check-in. Sunrise is still active.",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
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
                    var conditionLabel by remember(preferenceState.selectedCallbackId) {
                        mutableStateOf<String?>(null)
                    }
                    LaunchedEffect(preferenceState.selectedCallbackId) {
                        conditionLabel = preferenceState.selectedCallbackId?.let {
                            CallbackConfigurationRepository(this@MainActivity).find(it)?.displayLabel
                        }
                    }
                    CurfewApp(
                        readiness = readiness,
                        accountEnrolled = preferenceState.accountEnrolled,
                        accountSignedIn = preferenceState.accountSignedIn,
                        accountRecoveryState = accountRecoveryState,
                        onGrantExactAlarm = ::requestExactAlarmAccess,
                        onGrantNotifications = ::requestNotifications,
                        onRepairAlarmChannel = ::openAlarmChannelSettings,
                        onRaiseAlarmVolume = { startActivity(Intent(Settings.ACTION_SOUND_SETTINGS)) },
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
                        onRestoreRecoveryKey = { encodedKey ->
                            lifecycleScope.launch {
                                runCatching {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        AndroidAccountRecovery(this@MainActivity).restore(encodedKey)
                                    }
                                }.onSuccess { state ->
                                    accountRecoveryState = state
                                    preferences.setAccountEnrolled(true)
                                }.onFailure {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "That Recovery Key could not decrypt this account.",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        },
                        onAcknowledgeRecoveryKey = {
                            lifecycleScope.launch {
                                AndroidAccountRecovery(this@MainActivity)
                                    .acknowledgeSavedRecoveryKey()
                                accountRecoveryState = AccountRecoveryState.Ready
                                preferences.setAccountEnrolled(true)
                            }
                        },
                        onSaveCallback = { label, endpoint, actionUrl ->
                            conditionBusy = true
                            conditionMessage = null
                            lifecycleScope.launch {
                                try {
                                    wakeConditionSetup.save(label, endpoint, actionUrl)
                                    conditionMessage = "Saved. Export the setup file, then test the connection."
                                } catch (failure: Exception) {
                                    conditionMessage = "Couldn’t save the check-in. Check its name and address, then retry."
                                } finally {
                                    conditionBusy = false
                                }
                            }
                        },
                        conditionVerified = preferenceState.selectedCallbackId?.let {
                            preferenceState.isCallbackReady(it)
                        } ?: false,
                        conditionBusy = conditionBusy,
                        conditionMessage = conditionMessage,
                        onExportCondition = {
                            val id = preferenceState.selectedCallbackId
                            if (id != null && !conditionBusy) lifecycleScope.launch {
                                conditionBusy = true
                                try {
                                    pendingVerifierExport = wakeConditionSetup.provision(id)
                                    exportVerifier.launch("curfew-verifier.json")
                                } catch (failure: Exception) {
                                    pendingVerifierExport = null
                                    conditionMessage = "Couldn’t read this check-in. Save it again before exporting."
                                } finally {
                                    conditionBusy = false
                                }
                            }
                        },
                        onVerifyCondition = {
                            val id = preferenceState.selectedCallbackId
                            if (id != null && !conditionBusy) lifecycleScope.launch {
                                conditionBusy = true
                                conditionMessage = "Testing the connection…"
                                try {
                                    conditionMessage = if (wakeConditionSetup.verify(id)) {
                                        "Connection confirmed."
                                    } else {
                                        "Your check-in changed. Test the connection again."
                                    }
                                } catch (failure: Exception) {
                                    conditionMessage = "Couldn’t confirm the connection. Check the service setup, then retry."
                                } finally {
                                    conditionBusy = false
                                }
                            }
                        },
                        sunriseAlarm = savedAlarm,
                        callbackId = preferenceState.selectedCallbackId,
                        conditionLabel = if (sunriseReadFailed) null else conditionLabel,
                        alarmBusy = sunriseSaving,
                        alarmError = sunriseError,
                        onSaveAlarm = ::saveSunrise,
                        onToggleAlarm = { enabled -> changeSunrise(enabled = enabled) },
                    )
                }
            }
        }
    }

    override fun onStop() {
        cancelTestSound()
        super.onStop()
    }

    override fun onDestroy() {
        if (ownerRecoveryDelegate.isInitialized()) ownerRecovery.close()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        capabilityEpoch += 1
        lifecycleScope.launch {
            runCatching { sunrise.restore() }.onFailure {
                sunriseError = if (!AndroidAlarmClockGateway(this@MainActivity).canScheduleExactAlarms()) {
                    "Alarm access is off."
                } else "Couldn’t restore this alarm."
            }
            PerpetualAlarmService.restoreAfterForegroundEntry(this@MainActivity)
        }
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

    /**
     * Present only the active alarm over the keyguard. Requesting keyguard
     * dismissal here opens a credential prompt before the owner can reach the
     * alarm's actions. Recovery requests its own fresh confirmation separately.
     * Launcher entry must keep normal lock-screen behavior.
     */
    private fun presentOverKeyguard() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
                .onSuccess { state ->
                    accountRecoveryState = state
                    if (state == AccountRecoveryState.Ready) {
                        preferences.setAccountEnrolled(true)
                    }
                    capabilityEpoch += 1
                    Toast.makeText(
                        this@MainActivity,
                        when (state) {
                            AccountRecoveryState.Ready -> "Signed in. Encrypted sync is ready."
                            AccountRecoveryState.EnterRecoveryKey ->
                                "Signed in. Enter your Curfew Recovery Key to restore sync."
                            is AccountRecoveryState.SaveRecoveryKey ->
                                "Signed in. Save your Curfew Recovery Key before sync begins."
                        },
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
        return studio.hypertext.curfew.platform.AndroidAlarmReadiness(this).evaluate(preferenceState)
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
        testSoundJob?.cancel()
        testSoundJob = lifecycleScope.launch { soundCheck.play() }
    }

    private fun cancelTestSound() {
        testSoundJob?.cancel()
        testSoundJob = null
        soundCheck.close()
    }

    private fun saveSunrise(schedule: SunriseSchedule) {
        changeSunrise(schedule = schedule)
    }

    private fun changeSunrise(schedule: SunriseSchedule? = null, enabled: Boolean? = null) {
        if (sunriseSaving || sunriseReadFailed) return
        sunriseSaving = true
        sunriseError = null
        lifecycleScope.launch {
            try {
                val desired = schedule ?: sunrise.state.first()?.schedule?.copy(
                    enabled = requireNotNull(enabled),
                ) ?: return@launch
                sunrise.save(desired)
            } catch (failure: IllegalArgumentException) {
                sunriseError = failure.message ?: "Choose a future date and time for Sunrise."
            } catch (failure: Exception) {
                sunriseError = "Sunrise could not be saved. Check alarm access, sound, and the wake condition."
            } finally {
                sunriseSaving = false
            }
        }
    }

    companion object {
        const val EXTRA_SHOW_ALARM = "show_alarm"
        private const val NOTIFICATION_PERMISSION_REQUEST = 41
    }
}
