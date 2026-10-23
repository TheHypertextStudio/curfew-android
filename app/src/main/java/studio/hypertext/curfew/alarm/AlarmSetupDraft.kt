package studio.hypertext.curfew.alarm

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class AlarmSetupDraft(
    val id: String,
    val wakeTime: LocalTime,
    val onceDate: LocalDate?,
    val weekdays: Set<DayOfWeek>,
) {
    init {
        require(id.isNotBlank())
        require((onceDate != null && weekdays.isEmpty()) || (onceDate == null && weekdays.isNotEmpty()))
    }

    fun toSchedule(callbackId: String): SunriseSchedule = SunriseSchedule(
        id, wakeTime, onceDate, weekdays, enabled = true, callbackId = callbackId,
    )
}
