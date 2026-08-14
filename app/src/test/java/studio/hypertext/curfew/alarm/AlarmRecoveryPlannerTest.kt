package studio.hypertext.curfew.alarm

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmRecoveryPlannerTest {
    private val start = Instant.parse("2026-08-10T14:00:00Z")

    @Test
    fun `process recovery advances through missed intervals using elapsed time`() {
        val scheduled = AlarmCampaignState.Scheduled(
            campaignId = "campaign-1",
            startsAt = start,
            policy = AlarmRecurrencePolicy.default(),
            selectedDeviceIds = setOf("primary-phone"),
        )
        val snapshot = AlarmRuntimeSnapshot(
            state = scheduled,
            persistedAtWall = start.minusSeconds(10),
            persistedAtElapsedMillis = 40_000,
            bootId = "boot-a",
        )

        val recovered = AlarmRecoveryPlanner(AlarmCampaignEngine()).recover(
            snapshot = snapshot,
            nowWall = start.plus(Duration.ofMinutes(8)),
            nowElapsedMillis = 520_000,
            currentBootId = "boot-a",
        )

        assertTrue(recovered is AlarmCampaignState.Ringing)
        val ringing = recovered as AlarmCampaignState.Ringing
        assertEquals(2, ringing.attempt)
    }

    @Test
    fun `reboot recovery falls back to wall time and exhausts after deadline`() {
        val policy = AlarmRecurrencePolicy(
            maximumAttempts = 1,
            ringDuration = Duration.ofMinutes(2),
            quietDuration = Duration.ofMinutes(5),
        )
        val snapshot = AlarmRuntimeSnapshot(
            state = AlarmCampaignState.Scheduled(
                campaignId = "campaign-1",
                startsAt = start,
                policy = policy,
                selectedDeviceIds = setOf("primary-phone"),
            ),
            persistedAtWall = start.minusSeconds(10),
            persistedAtElapsedMillis = 40_000,
            bootId = "boot-a",
        )

        val recovered = AlarmRecoveryPlanner(AlarmCampaignEngine()).recover(
            snapshot = snapshot,
            nowWall = start.plus(Duration.ofMinutes(3)),
            nowElapsedMillis = 20_000,
            currentBootId = "boot-b",
        )

        assertTrue(recovered is AlarmCampaignState.Exhausted)
    }
}
