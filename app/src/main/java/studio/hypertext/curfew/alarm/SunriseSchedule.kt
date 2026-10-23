package studio.hypertext.curfew.alarm

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

data class SunriseOccurrence(
    val campaignId: String,
    val localDate: LocalDate,
    val startsAt: Instant,
)

data class SunriseSchedule(
    val id: String,
    val wakeTime: LocalTime,
    val onceDate: LocalDate?,
    val weekdays: Set<DayOfWeek>,
    val enabled: Boolean,
    val callbackId: String,
) {
    init {
        require(id.isNotBlank()) { "a schedule needs an identity" }
        require(callbackId.isNotBlank()) { "Sunrise requires a release condition" }
        require((onceDate != null && weekdays.isEmpty()) ||
            (onceDate == null && weekdays.isNotEmpty())) {
            "choose one night or at least one repeating weekday"
        }
    }

    fun nextOccurrence(after: Instant, zone: ZoneId): SunriseOccurrence? {
        if (!enabled) return null
        val firstDate = onceDate ?: after.atZone(zone).toLocalDate()
        val dates = if (onceDate != null) listOf(firstDate) else (0L..7L).map(firstDate::plusDays)
        val resolver = WakeScheduleResolver()
        return dates.asSequence()
            .filter { onceDate != null || it.dayOfWeek in weekdays }
            .map { date ->
                SunriseOccurrence(
                    // Local identity survives timezone reconciliation without creating a second morning.
                    campaignId = UUID.nameUUIDFromBytes("$id:$date".toByteArray(Charsets.UTF_8)).toString(),
                    localDate = date,
                    startsAt = resolver.resolve(date, wakeTime, zone).toInstant(),
                )
            }
            .firstOrNull { it.startsAt > after }
    }
}
