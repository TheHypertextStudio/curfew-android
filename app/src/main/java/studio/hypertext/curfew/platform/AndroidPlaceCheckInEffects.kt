package studio.hypertext.curfew.platform

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import studio.hypertext.curfew.account.NativeDeviceProofAuthenticator
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.callback.CallbackPollScheduler
import studio.hypertext.curfew.checkin.PlaceCheckInEffects
import studio.hypertext.curfew.protocols.Result
import studio.hypertext.curfew.protocols.WakeOutcome
import studio.hypertext.curfew.sync.WakeOutboxPublisher
import studio.hypertext.curfew.sync.WakeSyncScheduler

internal class AndroidPlaceCheckInEffects(
    context: Context,
    scheduler: SunriseScheduler? = null,
) : PlaceCheckInEffects {
    private val context = context.applicationContext
    private val alarms by lazy { scheduler ?: SunriseScheduler(this.context) }

    override suspend fun revokeCondition(id: String, revoke: suspend () -> Unit) = alarms.revokeCondition(id, revoke)

    override suspend fun completeOccurrence(campaignId: String) = alarms.completeOccurrence(campaignId)

    override fun retryCompletion(campaignId: String) {
        CallbackPollScheduler.schedule(context, campaignId)
    }

    override suspend fun publishWakeOutcome(terminal: AlarmCampaignState.Satisfied) {
        try {
            val device = NativeDeviceProofAuthenticator(context).deviceId()
            WakeOutboxPublisher(context).publish(
                WakeOutcome(
                    campaignId = terminal.campaignId,
                    releasedAt = terminal.satisfiedAt.toString(),
                    result = Result.Satisfied,
                    satisfyingDeviceId = device,
                ),
                writerDeviceId = device,
                version = INITIAL_WAKE_VERSION,
            )
            WakeSyncScheduler.accelerate(context)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Log.w(LOG_TAG, "Local check-in saved; account sync needs attention")
        }
    }

    private companion object {
        const val LOG_TAG = "PlaceCheckIn"
        const val INITIAL_WAKE_VERSION = 1L
    }
}
