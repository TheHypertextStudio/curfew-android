package studio.hypertext.curfew.alarm

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SunriseSchedulePlannerTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private val now = Instant.parse("2026-10-19T13:00:00Z")
    private val schedule = SunriseSchedule(
        "sunrise-1", LocalTime.of(7, 0), null, DayOfWeek.entries.toSet(), true, "callback-1",
    )
    private val occurrence = requireNotNull(schedule.nextOccurrence(now, zone))
    private val planner = SunriseSchedulePlanner()

    @Test
    fun `editing tomorrow never dismisses the active morning`() {
        val plan = planner.plan(
            schedule.copy(wakeTime = LocalTime.of(8, 0)),
            now.plusSeconds(4_000), zone, occurrence, SunriseOccurrencePhase.ACTIVE,
        )

        assertEquals(occurrence, plan.occurrence)
        assertNull(plan.cancelCampaignId)
    }

    @Test
    fun `disabling future alarms preserves an active occurrence`() {
        val plan = planner.plan(
            schedule.copy(enabled = false), now, zone, occurrence, SunriseOccurrencePhase.ACTIVE,
        )

        assertEquals(occurrence, plan.occurrence)
        assertNull(plan.cancelCampaignId)
    }

    @Test
    fun `disabling an alarm cancels its future occurrence`() {
        val plan = planner.plan(
            schedule.copy(enabled = false), now, zone, occurrence, SunriseOccurrencePhase.SCHEDULED,
        )

        assertNull(plan.occurrence)
        assertEquals(occurrence.campaignId, plan.cancelCampaignId)
    }

    @Test
    fun `disabling after the boundary cannot cancel an undelivered wake occurrence`() {
        val plan = planner.plan(
            schedule.copy(enabled = false), now.plusSeconds(7_200), zone,
            occurrence, SunriseOccurrencePhase.SCHEDULED,
        )

        assertEquals(occurrence, plan.occurrence)
        assertNull(plan.cancelCampaignId)
    }

    @Test
    fun `reconciliation preserves an overdue occurrence instead of silently skipping it`() {
        val plan = planner.plan(
            schedule, now.plusSeconds(7_200), zone, occurrence, SunriseOccurrencePhase.SCHEDULED,
        )

        assertEquals(occurrence, plan.occurrence)
        assertNull(plan.cancelCampaignId)
    }

    @Test
    fun `changing a future time replaces the alarm without a new morning identity`() {
        val plan = planner.plan(
            schedule.copy(wakeTime = LocalTime.of(8, 0)),
            now, zone, occurrence, SunriseOccurrencePhase.SCHEDULED,
        )

        assertEquals(Instant.parse("2026-10-19T15:00:00Z"), plan.occurrence?.startsAt)
        assertEquals(occurrence.campaignId, plan.occurrence?.campaignId)
        assertNull(plan.cancelCampaignId)
    }

    @Test
    fun `changing the one night date cancels only the previous future occurrence`() {
        val plan = planner.plan(
            schedule.copy(onceDate = LocalDate.of(2026, 10, 20), weekdays = emptySet()),
            now, zone, occurrence, SunriseOccurrencePhase.SCHEDULED,
        )

        assertEquals(LocalDate.of(2026, 10, 20), plan.occurrence?.localDate)
        assertEquals(occurrence.campaignId, plan.cancelCampaignId)
    }

    @Test
    fun `completed recurring morning cannot reopen after clock rewind`() {
        val plan = planner.plan(
            schedule, now, zone, occurrence, SunriseOccurrencePhase.FINISHED,
        )

        assertEquals(LocalDate.of(2026, 10, 20), plan.occurrence?.localDate)
        assertNull(plan.cancelCampaignId)
    }

    @Test
    fun `completed one night never rearms after clock rewind`() {
        val plan = planner.plan(
            schedule.copy(onceDate = occurrence.localDate, weekdays = emptySet()),
            now, zone, occurrence, SunriseOccurrencePhase.FINISHED,
        )

        assertNull(plan.occurrence)
        assertNull(plan.cancelCampaignId)
    }

    @Test
    fun `completed date remains consumed after the next morning has been scheduled`() {
        val completed = planner.plan(schedule, now, zone, occurrence, SunriseOccurrencePhase.FINISHED)
        val restored = planner.plan(
            schedule, now, zone, completed.occurrence, SunriseOccurrencePhase.SCHEDULED,
            completedThrough = completed.completedThrough,
        )

        assertEquals(LocalDate.of(2026, 10, 19), restored.completedThrough)
        assertEquals(LocalDate.of(2026, 10, 20), restored.occurrence?.localDate)
    }

    @Test
    fun `identical reconciliation keeps the same occurrence`() {
        val plan = planner.plan(
            schedule, now.plusSeconds(1), zone, occurrence, SunriseOccurrencePhase.SCHEDULED,
        )

        assertEquals(occurrence, plan.occurrence)
        assertNull(plan.cancelCampaignId)
    }
}
