package studio.hypertext.curfew.alarm

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmRecoveryPlannerTest {
    @Test
    fun `recovery preserves an active campaign after the former deadline`() {
        val start = Instant.parse("2026-08-10T07:00:00Z")
        val snapshot = AlarmRuntimeSnapshot(
            AlarmCampaignState.Scheduled("campaign", start, AlarmRecurrencePolicy.default(), setOf("device")),
            start,
            1,
            "old-boot",
        )

        val recovered = AlarmRecoveryPlanner(AlarmCampaignEngine()).recover(
            snapshot,
            start.plusSeconds(24 * 60 * 60),
            1,
            "new-boot",
        )

        assertEquals(481, (recovered as AlarmCampaignState.Ringing).attempt)
    }
}
