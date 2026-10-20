package studio.hypertext.curfew.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import studio.hypertext.curfew.alarm.AlarmCampaignEngine
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.LocalAlarmTransition
import studio.hypertext.curfew.callback.CallbackConfigurationRepository
import studio.hypertext.curfew.callback.CallbackPollScheduler
import studio.hypertext.curfew.sync.WakeStatusPublisher
import studio.hypertext.curfew.sync.WakeSyncScheduler

class AlarmTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AndroidAlarmClockGateway.ACTION_ALARM_TRANSITION) return
        val campaignId = intent.getStringExtra(AndroidAlarmClockGateway.EXTRA_CAMPAIGN_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var applied: AlarmCampaignState? = null
            LocalAlarmTransition(
                applyLocal = { applied = transition(context.applicationContext, campaignId) },
                finishLocal = { pendingResult.finish() },
                publish = {
                    applied?.let { WakeStatusPublisher(context.applicationContext).publish(it, transitionInstant(it)) }
                },
                onPublicationFailure = { android.util.Log.w("SunriseStatus", "Wake status could not be published") },
            ).deliver()
        }
    }

    private suspend fun transition(context: Context, campaignId: String): AlarmCampaignState? {
        val repository = AlarmRuntimeRepository(context)
        val previous = repository.load(campaignId) ?: return null
        val current = repository.save(AlarmCampaignEngine().advanceTo(previous, Instant.now()))
        val gateway = AndroidAlarmClockGateway(context)
        when (current) {
            is AlarmCampaignState.Scheduled -> gateway.setAlarmClock(campaignId, current.startsAt)
            is AlarmCampaignState.Ringing -> {
                val definition = repository.callbackId(campaignId)?.let {
                    CallbackConfigurationRepository(context).find(it)
                }
                ContextCompat.startForegroundService(
                    context,
                    PerpetualAlarmService.startIntent(
                        context,
                        current.campaignId,
                        current.attempt,
                        definition?.displayLabel,
                        definition?.actionUrl,
                    ),
                )
                if (definition != null) CallbackPollScheduler.schedule(context, campaignId)
                gateway.setAlarmClock(campaignId, current.ringEndsAt)
            }
            is AlarmCampaignState.Quiet -> {
                gateway.setAlarmClock(campaignId, current.nextAttemptAt)
            }
            is AlarmCampaignState.Overridden,
            is AlarmCampaignState.Satisfied,
            -> {
                runCatching { SunriseScheduler(context).completeOccurrence(campaignId) }
                    .onFailure { android.util.Log.w("SunriseCompletion", "Next alarm needs attention") }
            }
        }
        return current
    }

    private fun transitionInstant(state: AlarmCampaignState): Instant = when (state) {
        is AlarmCampaignState.Scheduled -> Instant.now()
        is AlarmCampaignState.Ringing -> state.ringStartedAt
        is AlarmCampaignState.Quiet -> state.nextAttemptAt.minus(state.policy.quietDuration)
        is AlarmCampaignState.Satisfied -> state.satisfiedAt
        is AlarmCampaignState.Overridden -> state.overriddenAt
    }
}
