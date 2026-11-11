package studio.hypertext.curfew.ui

import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRuntimeSnapshot
import studio.hypertext.curfew.checkin.CheckInDescription

/**
 * The active occurrence, its bound check-in, and the next ring time during quiet. These values
 * are separate from the schedule currently being edited.
 */
internal data class RingingAlarmPresentation(
    val snapshot: AlarmRuntimeSnapshot,
    val condition: CheckInDescription?,
    val nextRingAt: Instant? = null,
)

/**
 * Projects persisted snapshots into active alarm UI only for ringing and quiet phases. Missing
 * check-in metadata leaves the alarm and owner recovery reachable; terminal states remove
 * presentation.
 */
internal fun Flow<AlarmRuntimeSnapshot?>.ringingPresentation(
    findCondition: suspend (campaignId: String) -> CheckInDescription?,
): Flow<RingingAlarmPresentation?> = map { snapshot ->
    if (snapshot == null) return@map null
    val state = snapshot.state
    if (state !is AlarmCampaignState.Ringing && state !is AlarmCampaignState.Quiet) return@map null

    val condition = try {
        findCondition(state.campaignId)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // A check-in lookup failure cannot make the alarm or owner recovery disappear.
        null
    }
    RingingAlarmPresentation(snapshot, condition)
}
