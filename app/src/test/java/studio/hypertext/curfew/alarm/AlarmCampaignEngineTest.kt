package studio.hypertext.curfew.alarm

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmCampaignEngineTest {
    private val engine = AlarmCampaignEngine()
    private val start = Instant.parse("2026-08-10T07:00:00Z")
    private val policy = AlarmRecurrencePolicy.default()
    private val scheduled = AlarmCampaignState.Scheduled("campaign", start, policy, setOf("device"))

    @Test
    fun `the default cadence rings for two minutes then stays quiet for one`() {
        val ringing = engine.advanceTo(scheduled, start) as AlarmCampaignState.Ringing
        assertEquals(start.plus(Duration.ofMinutes(2)), ringing.ringEndsAt)

        val quiet = engine.advanceTo(ringing, ringing.ringEndsAt) as AlarmCampaignState.Quiet
        assertEquals(ringing.ringEndsAt.plus(Duration.ofMinutes(1)), quiet.nextAttemptAt)
    }

    @Test
    fun `the campaign keeps advancing beyond the former attempt ceiling`() {
        val ringing = engine.advanceTo(scheduled, start.plus(Duration.ofMinutes(72)))
        assertTrue(ringing is AlarmCampaignState.Ringing)
        assertEquals(25, (ringing as AlarmCampaignState.Ringing).attempt)
    }

    @Test
    fun `only a verified release or override makes the campaign terminal`() {
        val ringing = engine.advanceTo(scheduled, start) as AlarmCampaignState.Ringing
        assertTrue(engine.onConditionUnavailable(ringing) is AlarmCampaignState.Ringing)
        assertTrue(engine.satisfy(ringing, start.plusSeconds(1), "Docket") is AlarmCampaignState.Satisfied)
    }
}
