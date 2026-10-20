package studio.hypertext.curfew.alarm

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmRuntimeClockTest {
    private val start = Instant.parse("2026-10-20T07:00:00Z")
    private val engine = AlarmCampaignEngine()
    private val scheduled = AlarmCampaignState.Scheduled("morning", start, AlarmRecurrencePolicy.default(), setOf("phone"))
    private val ringing = engine.advanceTo(scheduled, start)
    private fun snapshot(state: AlarmCampaignState) = AlarmRuntimeSnapshot(state, start, 1000, "boot")

    @Test fun `active elapsed time ignores a forward wall clock jump`() {
        assertEquals(start.plusSeconds(10), AlarmRuntimeClock.effectiveNow(snapshot(ringing), start.plusSeconds(310), 11000, "boot"))
    }

    @Test fun `active elapsed time ignores a backward wall clock jump`() {
        assertEquals(start.plusSeconds(10), AlarmRuntimeClock.effectiveNow(snapshot(ringing), start.minusSeconds(300), 11000, "boot"))
    }

    @Test fun `scheduled alarms follow the changed wall clock`() {
        assertEquals(start.plusSeconds(310), AlarmRuntimeClock.effectiveNow(snapshot(scheduled), start.plusSeconds(310), 11000, "boot"))
    }

    @Test fun `a new boot uses wall time instead of a prior elapsed clock`() {
        assertEquals(start.plusSeconds(310), AlarmRuntimeClock.effectiveNow(snapshot(ringing), start.plusSeconds(310), 11000, "new-boot"))
    }

    @Test fun `saving after a clock jump retains the logical active anchor`() {
        val checkpoint = AlarmRuntimeClock.checkpoint(snapshot(ringing), ringing, start.plusSeconds(310), 11000, "boot")
        assertEquals(start.plusSeconds(10), checkpoint.persistedAtWall)
        assertEquals(start.plusSeconds(15), AlarmRuntimeClock.effectiveNow(checkpoint, start.plusSeconds(315), 16000, "boot"))
    }

    @Test fun `a different occurrence cannot inherit an active clock anchor`() {
        val another = (ringing as AlarmCampaignState.Ringing).copy(campaignId = "another")
        assertEquals(start.plusSeconds(310), AlarmRuntimeClock.checkpoint(snapshot(ringing), another, start.plusSeconds(310), 11000, "boot").persistedAtWall)
    }

    @Test fun `first active checkpoint uses the wall time at activation`() {
        assertEquals(start.plusSeconds(310), AlarmRuntimeClock.checkpoint(snapshot(scheduled), ringing, start.plusSeconds(310), 11000, "boot").persistedAtWall)
    }

    @Test fun `ring boundary maps its remaining duration to changed wall time`() {
        assertEquals(start.plusSeconds(420), AlarmRuntimeClock.nextTransitionAt(snapshot(ringing), start.plusSeconds(310), 11000, "boot"))
    }

    @Test fun `quiet boundary also follows elapsed time`() {
        val quiet = engine.advanceTo(ringing, start.plusSeconds(120))
        val saved = AlarmRuntimeSnapshot(quiet, start.plusSeconds(120), 121000, "boot")
        assertEquals(start.plusSeconds(480), AlarmRuntimeClock.nextTransitionAt(saved, start.plusSeconds(430), 131000, "boot"))
    }

    @Test fun `scheduled trigger remains an absolute local schedule boundary`() {
        assertEquals(start, AlarmRuntimeClock.nextTransitionAt(snapshot(scheduled), start.minusSeconds(300), 11000, "boot"))
    }

    @Test fun `terminal outcomes have no transition`() {
        val terminal = engine.satisfy(ringing, start.plusSeconds(1), "Verified")
        assertNull(AlarmRuntimeClock.nextTransitionAt(snapshot(terminal), start.plusSeconds(310), 11000, "boot"))
    }
}
