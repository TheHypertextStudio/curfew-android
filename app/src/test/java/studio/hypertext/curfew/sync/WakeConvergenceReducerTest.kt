package studio.hypertext.curfew.sync

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.hypertext.curfew.alarm.AlarmCampaignEngine
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRecurrencePolicy
import studio.hypertext.curfew.protocols.AuthorizedBy
import studio.hypertext.curfew.protocols.OverrideStatus
import studio.hypertext.curfew.protocols.RemoteOverride
import studio.hypertext.curfew.protocols.Result
import studio.hypertext.curfew.protocols.WakeCampaignState
import studio.hypertext.curfew.protocols.WakeStatus

class WakeConvergenceReducerTest {
    private val start = Instant.parse("2026-08-10T14:00:00Z")
    private val local = AlarmCampaignEngine().advance(
        AlarmCampaignState.Scheduled(
            campaignId = "campaign-1",
            startsAt = start,
            policy = AlarmRecurrencePolicy.default(),
            selectedDeviceIds = setOf("phone-a", "phone-b"),
        ),
        start,
    )

    @Test
    fun `shared protocol success releases every selected device`() {
        val result = WakeConvergenceReducer().apply(
            local,
            status(),
            lastStatusVersion = 3,
        )

        assertEquals(Result.Satisfied, result.outcome)
        assertEquals(setOf("phone-a", "phone-b"), result.devicesToRelease)
        assertTrue(result.releasesWakeGate)
    }

    @Test
    fun `selected device deadline and version mismatches fail closed`() {
        val reducer = WakeConvergenceReducer()
        assertThrows(IllegalArgumentException::class.java) {
            reducer.apply(local, status().copy(selectedDeviceIds = listOf("phone-c")), 3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            reducer.apply(local, status().copy(statusVersion = 3), 3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            reducer.apply(
                local,
                status().copy(finalDeadlineAt = start.plusSeconds(999).toString()),
                3,
            )
        }
    }

    @Test
    fun `shared protocol bounded override releases only targeted devices`() {
        val result = WakeConvergenceReducer().applyOverride(
            local,
            RemoteOverride(
                authorizedBy = AuthorizedBy.MCPUserApproval,
                durationMinutes = 10,
                overrideId = "override-1",
                reason = "Travel day",
                requestId = "request-1",
                startsAt = start.plusSeconds(20).toString(),
                status = OverrideStatus.Active,
                targetDeviceIds = listOf("phone-b"),
            ),
            statusVersion = 5,
        )

        assertEquals(Result.RemoteOverride, result.outcome)
        assertEquals(setOf("phone-b"), result.devicesToRelease)
    }

    private fun status() = WakeStatus(
        attemptNumber = 1,
        campaignId = "campaign-1",
        finalDeadlineAt = start.plusSeconds(16 * 60).toString(),
        maximumAttempts = 3,
        selectedDeviceIds = listOf("phone-a", "phone-b"),
        state = WakeCampaignState.Satisfied,
        statusVersion = 4,
        updatedAt = start.plusSeconds(20).toString(),
    )
}
