package studio.hypertext.curfew.alarm

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.hypertext.curfew.protocols.Result
import studio.hypertext.curfew.protocols.AlarmConfiguration

class AlarmCampaignEngineTest {
    private val start = Instant.parse("2026-08-10T14:00:00Z")
    private val engine = AlarmCampaignEngine()

    @Test
    fun `default campaign rings three times for sixteen minutes`() {
        val policy = AlarmRecurrencePolicy.default()

        assertEquals(3, policy.maximumAttempts)
        assertEquals(Duration.ofMinutes(2), policy.ringDuration)
        assertEquals(Duration.ofMinutes(5), policy.quietDuration)
        assertEquals(Duration.ofMinutes(16), policy.totalDuration)
        assertEquals(start.plus(Duration.ofMinutes(16)), policy.deadlineFrom(start))
    }

    @Test
    fun `campaign policy rejects a schedule beyond two hours`() {
        assertThrows(IllegalArgumentException::class.java) {
            AlarmRecurrencePolicy(
                maximumAttempts = 13,
                ringDuration = Duration.ofMinutes(5),
                quietDuration = Duration.ofMinutes(5),
            )
        }
    }

    @Test
    fun `campaign policy enforces generated protocol attempt and ring bounds`() {
        assertThrows(IllegalArgumentException::class.java) {
            AlarmRecurrencePolicy(
                maximumAttempts = 25,
                ringDuration = Duration.ofSeconds(30),
                quietDuration = Duration.ZERO,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AlarmRecurrencePolicy(
                maximumAttempts = 1,
                ringDuration = Duration.ofSeconds(29),
                quietDuration = Duration.ZERO,
            )
        }
    }

    @Test
    fun `generated protocol configuration must carry the exact deterministic duration`() {
        val configuration = AlarmConfiguration(
            campaignDurationSeconds = 16 * 60,
            maximumAttempts = 3,
            quietIntervalSeconds = 5 * 60,
            ringDurationSeconds = 2 * 60,
            selectedDeviceIds = listOf("primary-phone"),
        )
        assertEquals(Duration.ofMinutes(16), AlarmRecurrencePolicy.fromProtocol(configuration).totalDuration)

        assertThrows(IllegalArgumentException::class.java) {
            AlarmRecurrencePolicy.fromProtocol(configuration.copy(campaignDurationSeconds = 999))
        }
    }

    @Test
    fun `scheduled campaign begins its first ringing attempt`() {
        val scheduled = scheduledCampaign()

        val state = engine.advance(scheduled, start)

        assertTrue(state is AlarmCampaignState.Ringing)
        val ringing = state as AlarmCampaignState.Ringing
        assertEquals(1, ringing.attempt)
        assertEquals(start.plus(Duration.ofMinutes(2)), ringing.ringEndsAt)
    }

    @Test
    fun `ringing attempt enters a quiet interval before another attempt`() {
        val ringing = engine.advance(scheduledCampaign(), start)

        val state = engine.advance(ringing, start.plus(Duration.ofMinutes(2)))

        assertTrue(state is AlarmCampaignState.Quiet)
        val quiet = state as AlarmCampaignState.Quiet
        assertEquals(1, quiet.completedAttempt)
        assertEquals(start.plus(Duration.ofMinutes(7)), quiet.nextAttemptAt)
    }

    @Test
    fun `final ringing attempt exhausts and releases the wake gate`() {
        val policy = AlarmRecurrencePolicy(
            maximumAttempts = 1,
            ringDuration = Duration.ofMinutes(2),
            quietDuration = Duration.ofMinutes(5),
        )
        val scheduled = scheduledCampaign(policy)
        val ringing = engine.advance(scheduled, start)

        val state = engine.advance(ringing, start.plus(Duration.ofMinutes(2)))

        assertTrue(state is AlarmCampaignState.Exhausted)
        val exhausted = state as AlarmCampaignState.Exhausted
        assertEquals(Result.Exhausted, exhausted.outcome)
        assertEquals(true, exhausted.releasesWakeGate)
    }

    @Test
    fun `condition success terminates any pending state and releases selected devices`() {
        val ringing = engine.advance(scheduledCampaign(), start)

        val state = engine.satisfy(ringing, start.plusSeconds(30), "callback")

        assertTrue(state is AlarmCampaignState.Satisfied)
        val satisfied = state as AlarmCampaignState.Satisfied
        assertEquals(Result.Satisfied, satisfied.outcome)
        assertEquals(setOf("primary-phone", "bedside-phone"), satisfied.devicesToRelease)
        assertEquals(true, satisfied.releasesWakeGate)
    }

    @Test
    fun `condition observation before campaign start cannot terminate an unadvanced campaign`() {
        val scheduled = scheduledCampaign()

        assertThrows(IllegalArgumentException::class.java) {
            engine.satisfy(scheduled, start.minusMillis(1), "callback")
        }
    }

    @Test
    fun `condition observation at or after deadline cannot terminate an unadvanced campaign`() {
        val scheduled = scheduledCampaign()

        assertThrows(IllegalArgumentException::class.java) {
            engine.satisfy(scheduled, scheduled.policy.deadlineFrom(start), "callback")
        }
    }

    @Test
    fun `transport failure leaves a campaign pending`() {
        val ringing = engine.advance(scheduledCampaign(), start)

        assertEquals(ringing, engine.onConditionUnavailable(ringing))
    }

    @Test
    fun `bounded remote override terminates a campaign`() {
        val ringing = engine.advance(scheduledCampaign(), start)

        val state = engine.override(
            state = ringing,
            at = start.plusSeconds(20),
            overrideId = "override-1",
            validUntil = start.plus(Duration.ofMinutes(10)),
        )

        assertTrue(state is AlarmCampaignState.Overridden)
        val overridden = state as AlarmCampaignState.Overridden
        assertEquals(Result.RemoteOverride, overridden.outcome)
        assertEquals(true, overridden.releasesWakeGate)
    }

    @Test
    fun `expired remote override cannot terminate a campaign`() {
        val ringing = engine.advance(scheduledCampaign(), start)

        assertThrows(IllegalArgumentException::class.java) {
            engine.override(
                state = ringing,
                at = start.plus(Duration.ofMinutes(11)),
                overrideId = "override-1",
                validUntil = start.plus(Duration.ofMinutes(10)),
            )
        }
    }

    @Test
    fun `remote override after campaign deadline cannot terminate an unadvanced campaign`() {
        val scheduled = scheduledCampaign()
        val afterDeadline = scheduled.policy.deadlineFrom(start).plusMillis(1)

        assertThrows(IllegalArgumentException::class.java) {
            engine.override(
                state = scheduled,
                at = afterDeadline,
                overrideId = "override-1",
                validUntil = afterDeadline.plus(Duration.ofMinutes(5)),
            )
        }
    }

    private fun scheduledCampaign(
        policy: AlarmRecurrencePolicy = AlarmRecurrencePolicy.default(),
    ) = AlarmCampaignState.Scheduled(
        campaignId = "campaign-1",
        startsAt = start,
        policy = policy,
        selectedDeviceIds = setOf("primary-phone", "bedside-phone"),
    )
}
