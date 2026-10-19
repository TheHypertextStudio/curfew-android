package studio.hypertext.curfew.alarm

import java.io.IOException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SunriseScheduleControllerTest {
    private val now = Instant.parse("2026-10-19T13:00:00Z")
    private val zone = ZoneId.of("America/Los_Angeles")
    private val schedule = SunriseSchedule(
        "alarm-1", LocalTime.of(7, 0), null, DayOfWeek.entries.toSet(), true, "callback-1",
    )
    private val occurrence = requireNotNull(schedule.nextOccurrence(now, zone))
    private val store = MemoryStore()
    private val runtime = LocalRuntime()
    private val controller = SunriseScheduleController(store, runtime)

    @Test
    fun `saving an alarm schedules the selected occurrence without an account`() = runBlocking {
        val saved = controller.save(schedule, now, zone)

        assertEquals(occurrence, saved.occurrence)
        assertEquals(saved, store.value)
        assertEquals(occurrence, runtime.scheduled[occurrence.campaignId]?.first)
        assertEquals("callback-1", runtime.scheduled[occurrence.campaignId]?.second)
    }

    @Test
    fun `unusable verifier does not replace the saved alarm`() = runBlocking {
        val existing = initial()
        runtime.ready = false

        assertFailure { controller.save(schedule.copy(wakeTime = LocalTime.of(8, 0)), now, zone) }

        assertEquals(existing, store.value)
        assertTrue(runtime.scheduled.isEmpty())
        assertTrue(runtime.cancelled.isEmpty())
    }

    @Test
    fun `scheduling failure leaves the previous alarm saved and uncancelled`() = runBlocking {
        val existing = initial()
        runtime.failArm = true

        assertFailure { controller.save(schedule.copy(wakeTime = LocalTime.of(8, 0)), now, zone) }

        assertEquals(existing, store.value)
        assertTrue(runtime.cancelled.isEmpty())
    }

    @Test
    fun `failed persistence restores the previous scheduled time`() = runBlocking {
        val existing = initial()
        store.failWrite = true

        assertFailure { controller.save(schedule.copy(wakeTime = LocalTime.of(8, 0)), now, zone) }

        assertEquals(existing, store.value)
        assertEquals(occurrence, runtime.scheduled[occurrence.campaignId]?.first)
    }

    @Test
    fun `disabling cancels a future alarm even when readiness is incomplete`() = runBlocking {
        initial()
        runtime.ready = false

        val saved = controller.save(schedule.copy(enabled = false), now, zone)

        assertFalse(saved.schedule.enabled)
        assertNull(saved.occurrence)
        assertEquals(listOf(occurrence.campaignId), runtime.cancelled)
    }

    @Test
    fun `editing future settings does not reset an active occurrence or its callback`() = runBlocking {
        initial()
        runtime.phases[occurrence.campaignId] = SunriseOccurrencePhase.ACTIVE

        val saved = controller.save(schedule.copy(callbackId = "callback-2"), now, zone)

        assertEquals(occurrence, saved.occurrence)
        assertEquals("callback-2", saved.schedule.callbackId)
        assertTrue(runtime.scheduled.isEmpty())
        assertTrue(runtime.cancelled.isEmpty())
    }

    @Test
    fun `restoring a released occurrence schedules the next morning and keeps consumption`() = runBlocking {
        initial()
        runtime.phases[occurrence.campaignId] = SunriseOccurrencePhase.FINISHED

        val saved = requireNotNull(controller.restore(now.plusSeconds(7_200), zone))
        assertEquals(occurrence.localDate, saved.completedThrough)
        assertEquals(occurrence.localDate.plusDays(1), saved.occurrence?.localDate)

        val afterRewind = requireNotNull(controller.restore(now, zone))
        assertEquals(saved.occurrence, afterRewind.occurrence)
        assertEquals(saved.completedThrough, afterRewind.completedThrough)
    }

    @Test
    fun `one night remains completed after a second restoration`() = runBlocking {
        val once = schedule.copy(onceDate = occurrence.localDate, weekdays = emptySet())
        store.value = SavedSunriseAlarm(once, occurrence)
        runtime.phases[occurrence.campaignId] = SunriseOccurrencePhase.FINISHED

        val first = requireNotNull(controller.restore(now.plusSeconds(7_200), zone))
        assertNull(first.occurrence)
        val second = requireNotNull(controller.restore(now, zone))
        assertNull(second.occurrence)
        assertEquals(occurrence.localDate, second.completedThrough)
    }

    @Test
    fun `repeated restore keeps one campaign identity`() = runBlocking {
        initial()

        repeat(3) { controller.restore(now, zone) }

        assertEquals(setOf(occurrence.campaignId), runtime.scheduled.keys)
        assertEquals(occurrence, store.value?.occurrence)
        assertTrue(runtime.cancelled.isEmpty())
    }

    @Test
    fun `restoration recreates the committed overdue occurrence after missing runtime state`() = runBlocking {
        initial()
        runtime.phases.clear()

        val saved = requireNotNull(controller.restore(now.plusSeconds(7_200), zone))

        assertEquals(occurrence, saved.occurrence)
        assertNotNull(runtime.scheduled[occurrence.campaignId])
    }

    @Test
    fun `empty installation has nothing to restore`() = runBlocking {
        assertNull(controller.restore(now, zone))
        assertTrue(runtime.scheduled.isEmpty())
    }

    @Test
    fun `a new one night alarm in the past is rejected instead of saved as armed`() = runBlocking {
        val past = schedule.copy(onceDate = occurrence.localDate.minusDays(1), weekdays = emptySet())

        assertFailure { controller.save(past, now, zone) }

        assertNull(store.value)
        assertTrue(runtime.scheduled.isEmpty())
    }

    @Test
    fun `release consumption remains durable when the next alarm cannot be scheduled`() = runBlocking {
        initial()
        runtime.phases[occurrence.campaignId] = SunriseOccurrencePhase.FINISHED
        runtime.ready = false

        val completed = requireNotNull(controller.consume(occurrence.campaignId))

        assertNull(completed.occurrence)
        assertEquals(occurrence.localDate, completed.completedThrough)
        assertFailure { controller.restore(now, zone) }
        assertEquals(completed, store.value)
    }

    @Test
    fun `a duplicate release cannot consume a newer morning`() = runBlocking {
        initial()
        runtime.phases[occurrence.campaignId] = SunriseOccurrencePhase.FINISHED
        controller.consume(occurrence.campaignId)
        val next = controller.restore(now, zone)

        controller.consume(occurrence.campaignId)

        assertEquals(next, store.value)
    }

    @Test
    fun `consumption cannot clear an active alarm without a terminal runtime result`() = runBlocking {
        val previous = initial()
        runtime.phases[occurrence.campaignId] = SunriseOccurrencePhase.ACTIVE

        controller.consume(occurrence.campaignId)

        assertEquals(previous, store.value)
    }

    private fun initial(): SavedSunriseAlarm {
        val value = SavedSunriseAlarm(schedule, occurrence)
        store.value = value
        runtime.phases[occurrence.campaignId] = SunriseOccurrencePhase.SCHEDULED
        return value
    }

    private suspend fun assertFailure(action: suspend () -> Unit) {
        val failure = runCatching { action() }.exceptionOrNull()
        assertNotNull("The operation must report failure", failure)
    }

    private class MemoryStore : SunriseScheduleStore {
        var value: SavedSunriseAlarm? = null
        var failWrite = false

        override suspend fun read(): SavedSunriseAlarm? = value

        override suspend fun write(alarm: SavedSunriseAlarm) {
            if (failWrite) throw IOException("storage unavailable")
            value = alarm
        }
    }

    private class LocalRuntime : SunriseScheduleRuntime {
        var ready = true
        var failArm = false
        val phases = mutableMapOf<String, SunriseOccurrencePhase>()
        val scheduled = mutableMapOf<String, Pair<SunriseOccurrence, String>>()
        val cancelled = mutableListOf<String>()

        override suspend fun canArm(callbackId: String): Boolean = ready

        override suspend fun phase(campaignId: String): SunriseOccurrencePhase? = phases[campaignId]

        override suspend fun arm(occurrence: SunriseOccurrence, callbackId: String) {
            if (failArm) throw IOException("exact alarm rejected")
            scheduled[occurrence.campaignId] = occurrence to callbackId
            phases[occurrence.campaignId] = SunriseOccurrencePhase.SCHEDULED
        }

        override suspend fun cancel(campaignId: String) {
            cancelled += campaignId
            scheduled.remove(campaignId)
            phases.remove(campaignId)
        }
    }
}
