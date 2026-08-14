package studio.hypertext.curfew.platform

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import studio.hypertext.curfew.alarm.AlarmCampaignEngine
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRecoveryPlanner

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
        val gateway = AndroidAlarmClockGateway(context)
        if (!gateway.canScheduleExactAlarms()) return
        val repository = AlarmRuntimeRepository(context)
        val planner = AlarmRecoveryPlanner(AlarmCampaignEngine())
        val nowWall = Instant.now()
        val nowElapsed = SystemClock.elapsedRealtime()
        val bootId = repository.currentBootId()
        repository.pendingSnapshots().forEach { snapshot ->
            val state = planner.recover(snapshot, nowWall, nowElapsed, bootId)
            repository.save(state)
            val next = when (state) {
                is AlarmCampaignState.Scheduled -> state.startsAt
                // BOOT_COMPLETED cannot directly launch a media-playback FGS on Android 15+.
                // A user-visible alarm-clock transition immediately re-enters the normal path.
                is AlarmCampaignState.Ringing -> Instant.now().plusSeconds(1)
                is AlarmCampaignState.Quiet -> state.nextAttemptAt
                is AlarmCampaignState.Exhausted,
                is AlarmCampaignState.Overridden,
                is AlarmCampaignState.Satisfied,
                -> null
            }
            if (next != null) gateway.setAlarmClock(state.campaignId, next)
        }
    }

    private companion object {
        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
