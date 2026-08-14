package studio.hypertext.curfew.alarm

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class WakeScheduleResolver {
    fun resolve(
        date: LocalDate,
        time: LocalTime,
        zoneId: ZoneId,
    ): ZonedDateTime {
        val local = LocalDateTime.of(date, time)
        val rules = zoneId.rules
        val offsets = rules.getValidOffsets(local)
        return when {
            offsets.isNotEmpty() -> ZonedDateTime.ofLocal(local, zoneId, offsets.first())
            else -> {
                val transition = requireNotNull(rules.getTransition(local))
                ZonedDateTime.ofLocal(transition.dateTimeAfter, zoneId, transition.offsetAfter)
            }
        }
    }
}
