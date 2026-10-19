package studio.hypertext.curfew.alarm

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SunriseScheduleTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private val monday = Instant.parse("2026-10-19T13:00:00Z")

    @Test
    fun `one night resolves only its selected date`() {
        val alarm = once(LocalDate.of(2026, 10, 19))

        assertEquals(Instant.parse("2026-10-19T14:00:00Z"), alarm.nextOccurrence(monday, zone)?.startsAt)
        assertNull(alarm.nextOccurrence(Instant.parse("2026-10-19T14:00:00Z"), zone))
        assertNull(alarm.nextOccurrence(monday.plusSeconds(86_400), zone))
    }

    @Test
    fun `recurring alarm skips days the owner did not select`() {
        val alarm = repeating(setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY))

        assertEquals(Instant.parse("2026-10-23T14:00:00Z"), alarm.nextOccurrence(
            Instant.parse("2026-10-19T14:00:00Z"), zone,
        )?.startsAt)
    }

    @Test
    fun `disabled alarm has no future occurrence`() {
        assertNull(repeating(DayOfWeek.entries.toSet()).copy(enabled = false).nextOccurrence(monday, zone))
    }

    @Test
    fun `recovery does not generate another identity for the same morning`() {
        val alarm = repeating(DayOfWeek.entries.toSet())
        val first = requireNotNull(alarm.nextOccurrence(monday, zone))
        val restored = requireNotNull(alarm.nextOccurrence(monday.plusSeconds(30), zone))
        val tomorrow = requireNotNull(alarm.nextOccurrence(first.startsAt, zone))

        assertEquals(first.campaignId, restored.campaignId)
        assertNotEquals(first.campaignId, tomorrow.campaignId)
    }

    @Test
    fun `future alarm follows device local time after a timezone change`() {
        val alarm = repeating(DayOfWeek.entries.toSet())
        val now = Instant.parse("2026-10-19T09:00:00Z")

        assertEquals(Instant.parse("2026-10-19T14:00:00Z"), alarm.nextOccurrence(now, zone)?.startsAt)
        assertEquals(Instant.parse("2026-10-19T11:00:00Z"), alarm.nextOccurrence(
            now, ZoneId.of("America/New_York"),
        )?.startsAt)
        assertEquals(alarm.nextOccurrence(now, zone)?.campaignId, alarm.nextOccurrence(
            now, ZoneId.of("America/New_York"),
        )?.campaignId)
    }

    @Test
    fun `spring gap rings at first valid instant`() {
        val alarm = once(LocalDate.of(2026, 3, 8)).copy(wakeTime = LocalTime.of(2, 30))

        assertEquals(Instant.parse("2026-03-08T10:00:00Z"), alarm.nextOccurrence(
            Instant.parse("2026-03-08T08:00:00Z"), zone,
        )?.startsAt)
    }

    @Test
    fun `fall overlap never creates a second alarm for the same morning`() {
        val alarm = once(LocalDate.of(2026, 11, 1)).copy(wakeTime = LocalTime.of(1, 30))

        assertEquals(Instant.parse("2026-11-01T08:30:00Z"), alarm.nextOccurrence(
            Instant.parse("2026-11-01T07:00:00Z"), zone,
        )?.startsAt)
        assertNull(alarm.nextOccurrence(Instant.parse("2026-11-01T08:31:00Z"), zone))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `one night cannot also repeat`() {
        once(LocalDate.of(2026, 10, 19)).copy(weekdays = setOf(DayOfWeek.MONDAY))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `recurrence must select at least one weekday`() {
        repeating(emptySet())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `alarm requires a release condition`() {
        repeating(DayOfWeek.entries.toSet()).copy(callbackId = "")
    }

    private fun once(date: LocalDate) = SunriseSchedule(
        id = "sunrise-1",
        wakeTime = LocalTime.of(7, 0),
        onceDate = date,
        weekdays = emptySet(),
        enabled = true,
        callbackId = "verifier-1",
    )

    private fun repeating(days: Set<DayOfWeek>) = SunriseSchedule(
        id = "sunrise-1",
        wakeTime = LocalTime.of(7, 0),
        onceDate = null,
        weekdays = days,
        enabled = true,
        callbackId = "verifier-1",
    )
}
