package studio.hypertext.curfew.ui

import studio.hypertext.curfew.alarm.AlarmSetupDraft

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSetupDraftTest {
    @Test fun `one night draft becomes enabled only on explicit conversion with check in`() {
        val draft = AlarmSetupDraft("draft-1", LocalTime.of(7, 30), LocalDate.of(2026, 10, 21), emptySet())
        val schedule = draft.toSchedule("verified-check-in")
        assertEquals("draft-1", schedule.id)
        assertEquals(draft.wakeTime, schedule.wakeTime)
        assertEquals(draft.onceDate, schedule.onceDate)
        assertEquals("verified-check-in", schedule.callbackId)
        assertTrue(schedule.enabled)
    }

    @Test fun `weekly draft preserves chosen days`() {
        val draft = AlarmSetupDraft("draft-2", LocalTime.of(8, 0), null, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        assertEquals(draft.weekdays, draft.toSchedule("current-check-in").weekdays)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `draft cannot become an alarm without a check in`() {
        AlarmSetupDraft("draft-3", LocalTime.NOON, null, setOf(DayOfWeek.TUESDAY)).toSchedule("")
    }
}
