package studio.hypertext.curfew.alarm

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmPlaybackPlannerTest {
    private val start = Instant.parse("2026-10-20T07:00:00Z")
    private val engine = AlarmCampaignEngine()
    private val scheduled = AlarmCampaignState.Scheduled(
        "morning", start, AlarmRecurrencePolicy.default(), setOf("phone"),
    )
    private val ringing = engine.advanceTo(scheduled, start)
    private fun snapshot(state: AlarmCampaignState) = AlarmRuntimeSnapshot(state, start, 1000, "boot")

    @Test
    fun `sticky restart recovers the saved active attempt immediately`() {
        val result = AlarmPlaybackPlanner().restore(null, listOf(snapshot(ringing)), start.plusSeconds(30), 31000, "boot")
        assertEquals(ringing, result)
    }

    @Test
    fun `delayed service start respects the quiet interval instead of ringing stale extras`() {
        val result = AlarmPlaybackPlanner().restore("morning", listOf(snapshot(ringing)), start.plusSeconds(125), 126000, "boot")
        assertEquals(engine.advanceTo(ringing, start.plusSeconds(125)), result)
    }

    @Test
    fun `sticky restart never invents an alarm from a future schedule`() {
        assertNull(AlarmPlaybackPlanner().restore(null, listOf(snapshot(scheduled)), start, 1000, "boot"))
    }

    @Test
    fun `explicit stale start cannot fall back to a different active campaign`() {
        assertNull(AlarmPlaybackPlanner().restore("released", listOf(snapshot(ringing)), start, 1000, "boot"))
    }

    @Test
    fun `terminal state cannot become ringing after delayed delivery`() {
        val terminal = engine.satisfy(ringing, start.plusSeconds(1), "Verified")
        val result = AlarmPlaybackPlanner().restore("morning", listOf(snapshot(terminal)), start.plusSeconds(30), 31000, "boot")
        assertEquals(terminal, result)
    }

    @Test
    fun `sticky restart chooses the most recently persisted active campaign`() {
        val newer = snapshot(ringing.copyCampaign("newer")).copy(persistedAtWall = start.plusSeconds(1))
        assertEquals("newer", AlarmPlaybackPlanner().restore(null, listOf(snapshot(ringing), newer), start.plusSeconds(2), 1000, "boot")?.campaignId)
    }

    @Test
    fun `stale command preserves an active occurrence already requested by this service`() {
        assertEquals(ringing, AlarmPlaybackPlanner().restore(
            "released", listOf(snapshot(ringing)), start, 1000, "boot", setOf("morning"),
        ))
    }

    @Test
    fun `terminal command preserves a known current occurrence`() {
        val terminal = engine.satisfy(ringing.copyCampaign("released"), start, "Verified")
        assertEquals(ringing, AlarmPlaybackPlanner().restore(
            "released", listOf(snapshot(terminal), snapshot(ringing)), start, 1000, "boot", setOf("morning"),
        ))
    }

    @Test
    fun `stale command retains the known occurrences quiet boundary`() {
        val result = AlarmPlaybackPlanner().restore(
            "released", listOf(snapshot(ringing)), start.plusSeconds(125), 126000, "boot", setOf("morning"),
        )
        assertEquals(engine.advanceTo(ringing, start.plusSeconds(125)), result)
    }

    @Test
    fun `stale command never starts an unknown active occurrence`() {
        assertNull(AlarmPlaybackPlanner().restore(
            "released", listOf(snapshot(ringing)), start, 1000, "boot", setOf("unrelated"),
        ))
    }

    @Test
    fun `stale command never resumes a known terminal occurrence`() {
        val terminal = engine.satisfy(ringing, start, "Verified")
        assertNull(AlarmPlaybackPlanner().restore(
            "released", listOf(snapshot(terminal)), start, 1000, "boot", setOf("morning"),
        ))
    }

    @Test
    fun `valid requested occurrence takes precedence over a known older one`() {
        val newer = ringing.copyCampaign("newer")
        assertEquals(newer, AlarmPlaybackPlanner().restore(
            "newer", listOf(snapshot(ringing), snapshot(newer)), start, 1000, "boot", setOf("morning"),
        ))
    }

    @Test
    fun `stale command can finish a sticky recovery already requested by Android`() {
        assertEquals(ringing, AlarmPlaybackPlanner().restore(
            "released", listOf(snapshot(ringing)), start, 1000, "boot", emptySet(), true,
        ))
    }

    @Test
    fun `sticky continuation cannot turn a future schedule into ringing`() {
        assertNull(AlarmPlaybackPlanner().restore(
            "released", listOf(snapshot(scheduled)), start, 1000, "boot", emptySet(), true,
        ))
    }

    private fun AlarmCampaignState.copyCampaign(id: String): AlarmCampaignState =
        (this as AlarmCampaignState.Ringing).copy(campaignId = id)
}
