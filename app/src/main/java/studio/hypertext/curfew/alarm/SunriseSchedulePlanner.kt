package studio.hypertext.curfew.alarm

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class SunriseOccurrencePhase { SCHEDULED, ACTIVE, FINISHED }

data class SunriseSchedulePlan(
    val occurrence: SunriseOccurrence?,
    val cancelCampaignId: String?,
    val completedThrough: LocalDate? = null,
)

class SunriseSchedulePlanner {
    fun plan(
        schedule: SunriseSchedule,
        now: Instant,
        zone: ZoneId,
        previous: SunriseOccurrence?,
        phase: SunriseOccurrencePhase?,
        completedThrough: LocalDate? = null,
    ): SunriseSchedulePlan {
        val consumedDate = listOfNotNull(
            completedThrough,
            previous?.localDate?.takeIf { phase == SunriseOccurrencePhase.FINISHED },
        ).maxOrNull()
        if (previous != null && phase == SunriseOccurrencePhase.ACTIVE) {
            return SunriseSchedulePlan(previous, null, consumedDate)
        }
        if (previous != null &&
            phase == SunriseOccurrencePhase.SCHEDULED && previous.startsAt <= now
        ) {
            // An overdue saved alarm still has to wake its owner after recovery.
            return SunriseSchedulePlan(previous, null, consumedDate)
        }
        val after = if (consumedDate != null) {
            maxOf(now, consumedDate.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1))
        } else {
            now
        }
        val next = schedule.nextOccurrence(after, zone)
        val cancel = previous?.campaignId?.takeIf {
            phase == SunriseOccurrencePhase.SCHEDULED && it != next?.campaignId
        }
        return SunriseSchedulePlan(next, cancel, consumedDate)
    }
}
