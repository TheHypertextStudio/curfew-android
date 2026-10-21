package studio.hypertext.curfew.ui

import androidx.compose.runtime.saveable.Saver
import studio.hypertext.curfew.alarm.AlarmSetupDraft
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

internal val AlarmSetupDraftSaver = Saver<AlarmSetupDraft?, List<String>>(
    save = { draft -> draft?.let {
        listOf(it.id, it.wakeTime.toString(), it.onceDate?.toString().orEmpty(), it.weekdays.joinToString(",") { day -> day.value.toString() })
    } },
    restore = { values ->
        AlarmSetupDraft(values[0], LocalTime.parse(values[1]), values[2].takeIf(String::isNotBlank)?.let(LocalDate::parse),
            values[3].takeIf(String::isNotBlank)?.split(",")?.map { DayOfWeek.of(it.toInt()) }?.toSet() ?: emptySet())
    },
)
