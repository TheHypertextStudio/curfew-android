package studio.hypertext.curfew.platform

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
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.IBinder
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import studio.hypertext.curfew.MainActivity
import studio.hypertext.curfew.R
import studio.hypertext.curfew.account.NativeDeviceProofAuthenticator
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.callback.CallbackPollScheduler
import studio.hypertext.curfew.security.AndroidKeystoreSecretStore
import studio.hypertext.curfew.sync.ForegroundWakeSocket
import studio.hypertext.curfew.sync.WakeStatusPollScheduler

class PerpetualAlarmService : Service() {
    private var ringtone: Ringtone? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null
    private var wakeSocket: ForegroundWakeSocket? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A sticky redelivery after process death arrives with a null intent.
        // Fabricating a campaign here rang with invented parameters — attempt 1
        // of 3 against a campaign id that matches no row — until the next real
        // transition corrected it. AlarmTransitionReceiver is the only
        // authoritative driver, and its AlarmManager alarm outlives the process,
        // so declining is both safe and recoverable. Foreground state is still
        // entered first: the framework requires it even on the path that stops.
        if (intent == null) {
            ServiceCompat.startForeground(
                this,
                ALARM_NOTIFICATION_ID,
                ringingNotification(this, "", 0, 0, null, null),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val campaignId = intent.getStringExtra(EXTRA_CAMPAIGN_ID) ?: "active-campaign"
        val attempt = intent.getIntExtra(EXTRA_ATTEMPT, 1)
        val maximumAttempts = intent.getIntExtra(EXTRA_MAXIMUM_ATTEMPTS, 3)
        val conditionLabel = intent.getStringExtra(EXTRA_CONDITION_LABEL)
        val actionUrl = intent.getStringExtra(EXTRA_ACTION_URL)
        ServiceCompat.startForeground(
            this,
            ALARM_NOTIFICATION_ID,
            ringingNotification(
                this,
                campaignId,
                attempt,
                maximumAttempts,
                conditionLabel,
                actionUrl,
            ),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        beginAlarmSound()
        beginActiveSync(campaignId)
        return START_STICKY
    }

    override fun onDestroy() {
        ringtone?.stop()
        ringtone = null
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

    private fun beginAlarmSound() {
        if (ringtone?.isPlaying == true) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val audioManager = getSystemService(AudioManager::class.java)
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> ringtone?.play()
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                    -> ringtone?.stop()
                }
            }
            .build()
            .also(audioManager::requestAudioFocus)
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = RingtoneManager.getRingtone(this, alarmUri)?.apply {
            audioAttributes = attributes
            isLooping = true
            play()
        }
    }

    private fun beginActiveSync(campaignId: String) {
        wakeSocket?.close()
        wakeSocket = null
        pollRunnable?.let(mainHandler::removeCallbacks)
        pollRunnable = object : Runnable {
            override fun run() {
                CallbackPollScheduler.schedule(this@PerpetualAlarmService, campaignId)
                WakeStatusPollScheduler.schedule(this@PerpetualAlarmService, campaignId)
                mainHandler.postDelayed(this, 15_000)
            }
        }.also(mainHandler::post)

        val token = AndroidKeystoreSecretStore(this)
            .get("account:access-token")
            ?.toString(Charsets.UTF_8)
            ?: return
        serviceScope.launch {
            val campaign = java.net.URLEncoder.encode(
                campaignId,
                java.nio.charset.StandardCharsets.UTF_8,
            )
            val canonicalUrl =
                "https://curfew-sync.hypertext.studio/sync/socket?campaignId=$campaign"
            val proofAuthenticator = NativeDeviceProofAuthenticator(this@PerpetualAlarmService)
            val socket = ForegroundWakeSocket()
            wakeSocket = socket
            runCatching { socket.connect(
                endpoint = java.net.URI(
                    "wss://curfew-sync.hypertext.studio/sync/socket?campaignId=$campaign",
                ),
                bearerToken = token,
                deviceId = proofAuthenticator.deviceId(),
                deviceProof = proofAuthenticator.proof(
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
            ) }.onFailure { socket.close() }
        }
    }

    companion object {
        private const val ALARM_CHANNEL_ID = "curfew_perpetual_alarm"
        private const val STATUS_CHANNEL_ID = "curfew_wake_status"
        private const val ALARM_NOTIFICATION_ID = 4100
        private const val MISSED_NOTIFICATION_ID = 4101
        private const val EXTRA_CAMPAIGN_ID = "campaign_id"
        private const val EXTRA_ATTEMPT = "attempt"
        const val EXTRA_CONDITION_LABEL = "condition_label"
        const val EXTRA_ACTION_URL = "action_url"
        const val EXTRA_ATTEMPT_NUMBER = "attempt"
        const val EXTRA_MAXIMUM_ATTEMPTS = "maximum_attempts"

        fun startIntent(
            context: Context,
            campaignId: String,
            attempt: Int,
            maximumAttempts: Int = 3,
            conditionLabel: String? = null,
            actionUrl: String? = null,
        ): Intent =
            Intent(context, PerpetualAlarmService::class.java)
                .putExtra(EXTRA_CAMPAIGN_ID, campaignId)
                .putExtra(EXTRA_ATTEMPT, attempt)
                .putExtra(EXTRA_MAXIMUM_ATTEMPTS, maximumAttempts)
                .putExtra(EXTRA_CONDITION_LABEL, conditionLabel)
                .putExtra(EXTRA_ACTION_URL, actionUrl)

        fun stopIntent(context: Context, campaignId: String): Intent =
            Intent(context, PerpetualAlarmService::class.java)
                .putExtra(EXTRA_CAMPAIGN_ID, campaignId)

        fun ensureChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            manager.createNotificationChannel(
                NotificationChannel(
                    ALARM_CHANNEL_ID,
                    context.getString(R.string.alarm_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.alarm_channel_description)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setSound(sound, attributes)
                    enableVibration(true)
                    setBypassDnd(false)
                },
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    STATUS_CHANNEL_ID,
                    context.getString(R.string.status_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }

        fun alarmChannelIsAudible(context: Context): Boolean {
            ensureChannels(context)
            val channel = context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(ALARM_CHANNEL_ID)
            return channel != null &&
                channel.importance >= NotificationManager.IMPORTANCE_HIGH &&
                channel.sound != null
        }

        fun showMissedWakeNotice(
            context: Context,
            state: AlarmCampaignState.Exhausted,
        ) {
            ensureChannels(context)
            val notification = NotificationCompat.Builder(context, STATUS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.missed_wake_title))
                .setContentText(
                    context.getString(
                        R.string.missed_wake_body,
                        state.policy.maximumAttempts,
                    ),
                )
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build()
            context.getSystemService(NotificationManager::class.java)
                .notify(MISSED_NOTIFICATION_ID, notification)
        }

        private fun ringingNotification(
            context: Context,
            campaignId: String,
            attempt: Int,
            maximumAttempts: Int,
            conditionLabel: String?,
            actionUrl: String?,
        ): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            val alarmIntent = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SHOW_ALARM, true)
                .putExtra(AndroidAlarmClockGateway.EXTRA_CAMPAIGN_ID, campaignId)
                .putExtra(EXTRA_ATTEMPT_NUMBER, attempt)
                .putExtra(EXTRA_MAXIMUM_ATTEMPTS, maximumAttempts)
                .putExtra(EXTRA_CONDITION_LABEL, conditionLabel)
                .putExtra(EXTRA_ACTION_URL, actionUrl)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pendingIntent = PendingIntent.getActivity(
                context,
                campaignId.hashCode(),
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val builder = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.alarm_notification_title))
                .setContentText(context.getString(R.string.alarm_notification_body, attempt))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
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
