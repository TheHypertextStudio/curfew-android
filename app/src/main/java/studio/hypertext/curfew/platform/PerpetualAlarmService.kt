package studio.hypertext.curfew.platform

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.hypertext.curfew.MainActivity
import studio.hypertext.curfew.R
import studio.hypertext.curfew.account.NativeDeviceProofAuthenticator
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmPlaybackPlanner
import studio.hypertext.curfew.callback.CallbackConfigurationRepository
import studio.hypertext.curfew.callback.CallbackPollScheduler
import studio.hypertext.curfew.config.ProductionServices
import studio.hypertext.curfew.security.AndroidKeystoreSecretStore
import studio.hypertext.curfew.sync.ForegroundWakeSocket
import studio.hypertext.curfew.sync.WakeStatusPollScheduler
import java.time.Instant

class PerpetualAlarmService : Service() {
    private val alarmSound by lazy { AndroidAlarmSound(this) }
    private var audioFocusRequest: AudioFocusRequest? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null
    private var wakeSocket: ForegroundWakeSocket? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var playbackJob: Job? = null
    private var playbackGeneration = 0
    private val requestedCampaignIds = mutableSetOf<String>()
    private var stickyRecoveryRequested = false
    private var foregroundEntered = false
    private var syncCampaignId: String? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        ensureChannels(this)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val previouslyRequestedIds = requestedCampaignIds.toSet()
        val previouslyRequestedStickyRecovery = stickyRecoveryRequested
        if (intent == null) stickyRecoveryRequested = true
        val requestedId = intent?.getStringExtra(EXTRA_CAMPAIGN_ID)
        requestedId?.let(requestedCampaignIds::add)
        val generation = ++playbackGeneration
        playbackJob?.cancel()
        // Android requires foreground entry before disk recovery can complete.
        try {
            if (!foregroundEntered) {
                ServiceCompat.startForeground(
                    this,
                    STARTUP_NOTIFICATION_ID,
                    NotificationCompat
                        .Builder(this, STATUS_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle(getString(R.string.app_name))
                        .setSilent(true)
                        .setOngoing(true)
                        .build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
                foregroundEntered = true
            }
        } catch (_: ForegroundServiceStartNotAllowedException) {
            // Android may retain a boot-only exemption after an exact alarm delivery.
            AlarmRecoveryReceiver.requestPlaybackRetry(this)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        playbackJob =
            serviceScope.launch {
                try {
                    val repository = AlarmRuntimeRepository(this@PerpetualAlarmService)
                    val state =
                        SunriseScheduler.serialized {
                            AlarmPlaybackPlanner()
                                .restore(
                                    requestedId,
                                    repository.pendingSnapshots(),
                                    Instant.now(),
                                    SystemClock.elapsedRealtime(),
                                    repository.currentBootId(),
                                    previouslyRequestedIds,
                                    previouslyRequestedStickyRecovery,
                                )?.let { repository.save(it) }
                        }
                    val gateway = AndroidAlarmClockGateway(this@PerpetualAlarmService)
                    val boundary = state?.let { repository.nextTransitionAt(it.campaignId) }
                    if (state != null && boundary != null) {
                        runCatching { gateway.setAlarmClock(state.campaignId, boundary) }
                            .onFailure { android.util.Log.w("SunrisePlayback", "Next transition needs exact alarm access") }
                    }
                    if (state !is AlarmCampaignState.Ringing) {
                        withContext(Dispatchers.Main) {
                            if (generation == playbackGeneration) stopSelf(startId)
                        }
                        return@launch
                    }
                    val definition =
                        repository.callbackId(state.campaignId)?.let {
                            CallbackConfigurationRepository(this@PerpetualAlarmService).find(it)
                        }
                    repository.observe(state.campaignId).collectLatest { current ->
                        withContext(Dispatchers.Main) {
                            if (generation == playbackGeneration) {
                                if (current is AlarmCampaignState.Ringing) {
                                    // Full-screen launch is evaluated when a notification is added.
                                    // Updating the silent startup entry does not present the alarm.
                                    ServiceCompat.startForeground(
                                        this@PerpetualAlarmService,
                                        ALARM_NOTIFICATION_ID,
                                        ringingNotification(
                                            this@PerpetualAlarmService,
                                            current.campaignId,
                                            current.attempt,
                                            definition?.displayLabel,
                                            definition?.actionUrl,
                                        ),
                                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                                    )
                                    getSystemService(NotificationManager::class.java).cancel(STARTUP_NOTIFICATION_ID)
                                    requestedCampaignIds.add(current.campaignId)
                                    stickyRecoveryRequested = false
                                    beginAlarmSound()
                                    if (syncCampaignId != current.campaignId) beginActiveSync(current.campaignId)
                                } else {
                                    stopSelf(startId)
                                }
                            }
                        }
                    }
                } catch (failure: kotlinx.coroutines.CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    android.util.Log.e("SunrisePlayback", "Could not restore the saved alarm")
                    withContext(Dispatchers.Main) {
                        if (generation == playbackGeneration) stopSelf(startId)
                    }
                }
            }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        alarmSound.stop()
        audioFocusRequest?.let {
            getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it)
        }
        audioFocusRequest = null
        pollRunnable?.let(mainHandler::removeCallbacks)
        pollRunnable = null
        wakeSocket?.close()
        wakeSocket = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun beginAlarmSound() {
        if (alarmSound.isPlaying) return
        val attributes =
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        val audioManager = getSystemService(AudioManager::class.java)
        audioFocusRequest =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { change ->
                    when (change) {
                        AudioManager.AUDIOFOCUS_GAIN -> serviceScope.launch(Dispatchers.Main.immediate) { alarmSound.play() }

                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                        -> alarmSound.stop()
                    }
                }.build()
                .also(audioManager::requestAudioFocus)
        if (!alarmSound.play()) android.util.Log.e("SunrisePlayback", "Alarm sound could not start")
    }

    private fun beginActiveSync(campaignId: String) {
        syncCampaignId = campaignId
        wakeSocket?.close()
        wakeSocket = null
        pollRunnable?.let(mainHandler::removeCallbacks)
        pollRunnable =
            object : Runnable {
                override fun run() {
                    CallbackPollScheduler.schedule(this@PerpetualAlarmService, campaignId)
                    WakeStatusPollScheduler.schedule(this@PerpetualAlarmService, campaignId)
                    mainHandler.postDelayed(this, 15_000)
                }
            }.also(mainHandler::post)

        val token =
            AndroidKeystoreSecretStore(this)
                .get(ProductionServices.ACCESS_TOKEN_KEY)
                ?.toString(Charsets.UTF_8)
                ?: return
        serviceScope.launch {
            val campaign =
                java.net.URLEncoder.encode(
                    campaignId,
                    java.nio.charset.StandardCharsets.UTF_8,
                )
            val canonicalUrl =
                "${ProductionServices.SYNC_ORIGIN}/sync/socket?campaignId=$campaign"
            val proofAuthenticator = NativeDeviceProofAuthenticator(this@PerpetualAlarmService)
            val socket = ForegroundWakeSocket()
            wakeSocket = socket
            runCatching {
                socket.connect(
                    endpoint =
                        java.net.URI(
                            "${ProductionServices.SYNC_SOCKET_ORIGIN}/sync/socket?campaignId=$campaign",
                        ),
                    bearerToken = token,
                    deviceId = proofAuthenticator.deviceId(),
                    deviceProof =
                        proofAuthenticator.proof(
                            accessToken = token,
                            method = "GET",
                            canonicalUrl = canonicalUrl,
                        ),
                    onCiphertextRecordAvailable = {
                        WakeStatusPollScheduler.schedule(this@PerpetualAlarmService, campaignId)
                    },
                    onDisconnected = {
                        // The bounded foreground poll remains authoritative when the socket drops.
                    },
                )
            }.onFailure { socket.close() }
        }
    }

    companion object {
        @Volatile private var running = false

        internal fun restoreAfterForegroundEntry(context: Context) {
            // Force-stop removes timers; older Android versions do not send a reopen broadcast.
            if (!running) AlarmRecoveryReceiver.requestPlaybackRetry(context)
        }

        internal const val ALARM_CHANNEL_ID = "curfew_perpetual_alarm_playback"
        private const val LEGACY_ALARM_CHANNEL_ID = "curfew_perpetual_alarm"
        private const val STATUS_CHANNEL_ID = "curfew_wake_status"
        private const val ALARM_NOTIFICATION_ID = 4100
        private const val MISSED_NOTIFICATION_ID = 4101
        private const val STARTUP_NOTIFICATION_ID = 4102
        private const val EXTRA_CAMPAIGN_ID = "campaign_id"
        private const val EXTRA_ATTEMPT = "attempt"
        const val EXTRA_CONDITION_LABEL = "condition_label"
        const val EXTRA_ACTION_URL = "action_url"
        const val EXTRA_ATTEMPT_NUMBER = "attempt"

        fun startIntent(
            context: Context,
            campaignId: String,
            attempt: Int,
            conditionLabel: String? = null,
            actionUrl: String? = null,
        ): Intent =
            Intent(context, PerpetualAlarmService::class.java)
                .putExtra(EXTRA_CAMPAIGN_ID, campaignId)
                .putExtra(EXTRA_ATTEMPT, attempt)
                .putExtra(EXTRA_CONDITION_LABEL, conditionLabel)
                .putExtra(EXTRA_ACTION_URL, actionUrl)

        fun stopIntent(
            context: Context,
            campaignId: String,
        ): Intent =
            Intent(context, PerpetualAlarmService::class.java)
                .putExtra(EXTRA_CAMPAIGN_ID, campaignId)

        fun ensureChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            ensureAlarmNotificationChannel(
                manager,
                alarmNotificationChannel(context, manager.getNotificationChannel(LEGACY_ALARM_CHANNEL_ID)),
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    STATUS_CHANNEL_ID,
                    context.getString(R.string.status_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }

        internal fun ensureAlarmNotificationChannel(
            manager: NotificationManager,
            channel: NotificationChannel,
        ) {
            if (manager.getNotificationChannel(channel.id) == null) manager.createNotificationChannel(channel)
        }

        internal fun alarmNotificationChannel(
            context: Context,
            previous: NotificationChannel?,
            channelId: String = ALARM_CHANNEL_ID,
        ): NotificationChannel =
            NotificationChannel(
                channelId,
                context.getString(R.string.alarm_channel_name),
                previous?.importance ?: NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.alarm_channel_description)
                lockscreenVisibility = previous?.lockscreenVisibility ?: Notification.VISIBILITY_PUBLIC
                // MediaPlayer owns alarm audio. A notification ringtone would hold a second
                // focus request and prevent the player from regaining transiently lost focus.
                // Keep importance high; setSilent would also suppress full-screen presentation.
                setSound(null, null)
                previous?.vibrationPattern?.let(::setVibrationPattern)
                if (Build.VERSION.SDK_INT >= 35) previous?.vibrationEffect?.let(::setVibrationEffect)
                enableVibration(previous?.shouldVibrate() ?: true)
                setBypassDnd(previous?.canBypassDnd() ?: false)
                setShowBadge(previous?.canShowBadge() ?: true)
                enableLights(previous?.shouldShowLights() ?: false)
                previous?.let {
                    lightColor = it.lightColor
                    group = it.group
                }
            }

        fun alarmChannelAllowsPresentation(context: Context): Boolean {
            ensureChannels(context)
            val channel =
                context
                    .getSystemService(NotificationManager::class.java)
                    .getNotificationChannel(ALARM_CHANNEL_ID)
            return alarmChannelAllowsPresentation(channel)
        }

        internal fun alarmChannelAllowsPresentation(channel: NotificationChannel?): Boolean =
            channel != null && channel.importance >= NotificationManager.IMPORTANCE_HIGH && channel.sound == null

        private fun ringingNotification(
            context: Context,
            campaignId: String,
            attempt: Int,
            conditionLabel: String?,
            actionUrl: String?,
        ): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            val alarmIntent =
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_SHOW_ALARM, true)
                    .putExtra(AndroidAlarmClockGateway.EXTRA_CAMPAIGN_ID, campaignId)
                    .putExtra(EXTRA_ATTEMPT_NUMBER, attempt)
                    .putExtra(EXTRA_CONDITION_LABEL, conditionLabel)
                    .putExtra(EXTRA_ACTION_URL, actionUrl)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pendingIntent =
                PendingIntent.getActivity(
                    context,
                    campaignId.hashCode(),
                    alarmIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val builder =
                NotificationCompat
                    .Builder(context, ALARM_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(context.getString(R.string.alarm_notification_title))
                    .setContentText(
                        if (conditionLabel == null) {
                            context.getString(R.string.alarm_notification_body)
                        } else {
                            context.getString(R.string.alarm_notification_named_check_in, conditionLabel)
                        },
                    ).setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOngoing(true)
                    .setContentIntent(pendingIntent)
            if (Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent()) {
                builder.setFullScreenIntent(pendingIntent, true)
            }
            return builder.build()
        }
    }
}
