package studio.hypertext.curfew.sync

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRecurrencePolicy
import studio.hypertext.curfew.protocols.WakeCampaignState

class WakeStatusPublisherTest {
    private val start = Instant.parse("2026-08-13T14:00:00Z")
    private val policy = AlarmRecurrencePolicy(
        maximumAttempts = 3,
        ringDuration = Duration.ofMinutes(2),
        quietDuration = Duration.ofMinutes(5),
    )
    private val deviceIds = setOf(
        "10000000-0000-4000-8000-000000000002",
        "10000000-0000-4000-8000-000000000001",
    )

    @Test
    fun `maps a ringing attempt to deterministic shared status`() {
        val state = AlarmCampaignState.Ringing(
            campaignId = "20000000-0000-4000-8000-000000000001",
            attempt = 2,
            ringStartedAt = start.plusSeconds(7 * 60),
            ringEndsAt = start.plusSeconds(9 * 60),
            policy = policy,
            selectedDeviceIds = deviceIds,
        )

        val status = WakeStatusFactory.create(
            state = state,
            finalDeadlineAt = start.plus(policy.totalDuration),
            statusVersion = 4,
            updatedAt = state.ringStartedAt,
        )

        assertEquals(WakeCampaignState.RingingAttempt, status.state)
        assertEquals(2L, status.attemptNumber)
        assertEquals(3L, status.maximumAttempts)
        assertEquals(4L, status.statusVersion)
        assertEquals(start.plusSeconds(16 * 60).toString(), status.finalDeadlineAt)
        assertEquals(deviceIds.sorted(), status.selectedDeviceIds)
    }

    @Test
    fun `exhaustion reports the final attempt at the fixed deadline`() {
        val deadline = start.plus(policy.totalDuration)
        val state = AlarmCampaignState.Exhausted(
            campaignId = "20000000-0000-4000-8000-000000000001",
            exhaustedAt = deadline,
            policy = policy,
            selectedDeviceIds = deviceIds,
        )

        val status = WakeStatusFactory.create(state, deadline, 7, deadline)

        assertEquals(WakeCampaignState.Exhausted, status.state)
        assertEquals(3L, status.attemptNumber)
        assertEquals(deadline.toString(), status.updatedAt)
    }
}
