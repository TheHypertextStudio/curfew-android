package studio.hypertext.curfew.platform

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import studio.hypertext.curfew.alarm.AlarmCampaignState

class AlarmRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val supported = intent.action in SUPPORTED_ACTIONS
        if (!supported) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                restorePending(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun restorePending(context: Context) {
        // Readiness failure for tomorrow must not prevent recovery of today's active alarm.
        runCatching { SunriseScheduler(context).restore() }
            .onFailure { android.util.Log.w("SunriseRecovery", "Saved schedule needs attention") }
        val gateway = AndroidAlarmClockGateway(context)
        if (!gateway.canScheduleExactAlarms()) return
        val repository = AlarmRuntimeRepository(context)
        repository.pendingSnapshots().forEach { snapshot ->
            val state = repository.recover(snapshot.state.campaignId) ?: return@forEach
            val next = when (state) {
                is AlarmCampaignState.Scheduled -> state.startsAt
                // BOOT_COMPLETED cannot directly launch a media-playback FGS on Android 15+.
                // A user-visible alarm-clock transition immediately re-enters the normal path.
                is AlarmCampaignState.Ringing -> Instant.now().plusSeconds(5)
                is AlarmCampaignState.Quiet -> repository.nextTransitionAt(state.campaignId)
                is AlarmCampaignState.Overridden,
                is AlarmCampaignState.Satisfied,
                -> null
            }
            if (next != null) gateway.setAlarmClock(state.campaignId, next)
        }
    }

    companion object {
        private const val ACTION_RETRY_PLAYBACK = "studio.hypertext.curfew.action.RETRY_PLAYBACK"

        internal fun requestPlaybackRetry(context: Context) {
            context.applicationContext.sendBroadcast(
                Intent(context, AlarmRecoveryReceiver::class.java)
                    .setAction(ACTION_RETRY_PLAYBACK)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
            )
        }

        private val SUPPORTED_ACTIONS = setOf(
            ACTION_RETRY_PLAYBACK,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
