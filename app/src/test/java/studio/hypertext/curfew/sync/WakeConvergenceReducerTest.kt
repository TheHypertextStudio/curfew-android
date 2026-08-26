package studio.hypertext.curfew.sync

import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRecurrencePolicy
import studio.hypertext.curfew.protocols.WakeCampaignState
import studio.hypertext.curfew.protocols.WakeStatus

class WakeConvergenceReducerTest {
    @Test
    fun `a fresh satisfied status releases without a deadline comparison`() {
        val local = AlarmCampaignState.Scheduled(
            "campaign",
            Instant.parse("2026-08-10T07:00:00Z"),
            AlarmRecurrencePolicy.default(),
            setOf("device"),
        )
        val remote = WakeStatus(
            attemptNumber = 0,
            campaignId = "campaign",
            selectedDeviceIds = listOf("device"),
            state = WakeCampaignState.Satisfied,
            statusVersion = 1,
            updatedAt = "2026-08-10T07:00:01Z",
        )

        assertTrue(WakeConvergenceReducer().apply(local, remote, 0).releasesWakeGate)
    }
}
