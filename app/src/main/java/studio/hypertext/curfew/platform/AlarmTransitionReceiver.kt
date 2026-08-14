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
import studio.hypertext.curfew.callback.CallbackConfigurationRepository
import studio.hypertext.curfew.callback.CallbackPollScheduler
import studio.hypertext.curfew.account.NativeDeviceProofAuthenticator
import studio.hypertext.curfew.protocols.Result as ProtocolResult
import studio.hypertext.curfew.protocols.WakeOutcome as ProtocolWakeOutcome
import studio.hypertext.curfew.sync.WakeOutboxPublisher
import studio.hypertext.curfew.sync.WakeStatusPublisher
import studio.hypertext.curfew.sync.WakeSyncScheduler

class AlarmTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AndroidAlarmClockGateway.ACTION_ALARM_TRANSITION) return
        val campaignId = intent.getStringExtra(AndroidAlarmClockGateway.EXTRA_CAMPAIGN_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                transition(context.applicationContext, campaignId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun transition(context: Context, campaignId: String) {
        val repository = AlarmRuntimeRepository(context)
        val previous = repository.load(campaignId) ?: return
        val current = AlarmCampaignEngine().advanceTo(previous, Instant.now())
        repository.save(current)
        WakeStatusPublisher(context).publish(current, transitionInstant(current))
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
                        current.policy.maximumAttempts,
                        definition?.displayLabel,
                        definition?.actionUrl,
                    ),
                )
                if (definition != null) CallbackPollScheduler.schedule(context, campaignId)
                gateway.setAlarmClock(campaignId, current.ringEndsAt)
            }
            is AlarmCampaignState.Quiet -> {
                context.stopService(PerpetualAlarmService.stopIntent(context, campaignId))
                gateway.setAlarmClock(campaignId, current.nextAttemptAt)
            }
            is AlarmCampaignState.Exhausted -> {
                context.stopService(PerpetualAlarmService.stopIntent(context, campaignId))
                gateway.cancel(campaignId)
                CallbackPollScheduler.cancel(context, campaignId)
                val deviceId = NativeDeviceProofAuthenticator(context).deviceId()
                WakeOutboxPublisher(context).publish(
                    ProtocolWakeOutcome(
                        attemptsCompleted = current.policy.maximumAttempts.toLong(),
                        campaignId = current.campaignId,
                        releasedAt = current.exhaustedAt.toString(),
                        result = ProtocolResult.Exhausted,
                    ),
                    writerDeviceId = deviceId,
                    version = 1,
                )
                WakeSyncScheduler.accelerate(context)
                PerpetualAlarmService.showMissedWakeNotice(context, current)
            }
            is AlarmCampaignState.Overridden,
            is AlarmCampaignState.Satisfied,
            -> {
                context.stopService(PerpetualAlarmService.stopIntent(context, campaignId))
                gateway.cancel(campaignId)
                CallbackPollScheduler.cancel(context, campaignId)
            }
        }
    }

    private fun transitionInstant(state: AlarmCampaignState): Instant = when (state) {
        is AlarmCampaignState.Scheduled -> Instant.now()
        is AlarmCampaignState.Ringing -> state.ringStartedAt
        is AlarmCampaignState.Quiet -> state.nextAttemptAt.minus(state.policy.quietDuration)
        is AlarmCampaignState.Satisfied -> state.satisfiedAt
        is AlarmCampaignState.Exhausted -> state.exhaustedAt
        is AlarmCampaignState.Overridden -> state.overriddenAt
    }
}
