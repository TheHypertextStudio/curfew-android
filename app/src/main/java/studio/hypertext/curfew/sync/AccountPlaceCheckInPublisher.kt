package studio.hypertext.curfew.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import studio.hypertext.curfew.account.NativeDeviceProofAuthenticator
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.checkin.PlaceCheckInPublisher
import studio.hypertext.curfew.protocols.Result
import studio.hypertext.curfew.protocols.WakeOutcome

/**
 * Queues a committed place check-in for account sync. Devices without account
 * key material can still check in locally; they have no wake outcome to send.
 * A sync error cannot undo the local alarm release.
 */
internal class AccountPlaceCheckInPublisher(context: Context) : PlaceCheckInPublisher {
    private val context = context.applicationContext

    override suspend fun publish(terminal: AlarmCampaignState.Satisfied) {
        try {
            val device = NativeDeviceProofAuthenticator(context).deviceId()
            val queued = WakeOutboxPublisher(context).publish(
                WakeOutcome(
                    campaignId = terminal.campaignId,
                    releasedAt = terminal.satisfiedAt.toString(),
                    result = Result.Satisfied,
                    satisfyingDeviceId = device,
                ),
                writerDeviceId = device,
                version = INITIAL_WAKE_VERSION,
            )
            if (queued) WakeSyncScheduler.accelerate(context)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Log.w(LOG_TAG, SYNC_DELAY_MESSAGE)
        }
    }

    private companion object {
        const val LOG_TAG = "PlaceCheckIn"
        const val SYNC_DELAY_MESSAGE = "Local check-in saved; account sync needs attention"
        const val INITIAL_WAKE_VERSION = 1L
    }
}
