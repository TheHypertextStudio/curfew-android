package studio.hypertext.curfew.ui

import java.time.Instant
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRecurrencePolicy
import studio.hypertext.curfew.alarm.AlarmRuntimeSnapshot
import studio.hypertext.curfew.checkin.CheckInDescription

/**
 * Checks presentation changes across scheduled, ringing, quiet, and satisfied snapshots,
 * including missing check-in metadata.
 */
class RingingAlarmPresentationTest {
    private val now = Instant.parse("2026-11-11T15:00:00Z")
    private val policy = AlarmRecurrencePolicy.default()
    private val devices = setOf("device")
    private val ringing = AlarmCampaignState.Ringing("wake", 1, now, now.plusSeconds(120), policy, devices)
    private val quiet = AlarmCampaignState.Quiet("wake", 1, now.plusSeconds(180), policy, devices)

    @Test
    fun displaysRingingAndQuietUntilTheOccurrenceIsSatisfied() = runBlocking {
        val scheduled = AlarmCampaignState.Scheduled("wake", now, policy, devices)
        val satisfied = AlarmCampaignState.Satisfied(
            "wake", now, "Bathroom", devicesToRelease = devices, policy = policy, selectedDeviceIds = devices,
        )
        val condition = CheckInDescription("bathroom", "Bathroom", actionUrl = null, placeCode = true)
        val presentations = flowOf(scheduled, ringing, quiet, satisfied)
            .map(::snapshot)
            .ringingPresentation { id ->
                assertEquals("wake", id)
                condition
            }.toList()

        assertEquals(
            listOf(null, RingingAlarmPresentation(snapshot(ringing), condition), RingingAlarmPresentation(snapshot(quiet), condition), null),
            presentations,
        )
    }

    @Test
    fun keepsOwnerRecoveryReachableWhenTheConditionCannotLoad() = runBlocking {
        val presentations = flowOf(snapshot(ringing)).ringingPresentation { error("Unavailable") }.toList()
        assertEquals(listOf(RingingAlarmPresentation(snapshot(ringing), null)), presentations)
    }

    private fun snapshot(state: AlarmCampaignState) = AlarmRuntimeSnapshot(state, now, 0L, "boot")
}
