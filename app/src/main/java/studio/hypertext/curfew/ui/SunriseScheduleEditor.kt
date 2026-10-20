package studio.hypertext.curfew.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.UUID
import studio.hypertext.curfew.alarm.SavedSunriseAlarm
import studio.hypertext.curfew.alarm.SunriseSchedule
import studio.hypertext.curfew.alarm.WakeScheduleResolver

@Composable
internal fun SunriseScheduleEditor(
    alarm: SavedSunriseAlarm?,
    callbackId: String?,
    canArm: Boolean,
    busy: Boolean,
    error: String?,
    onSave: (SunriseSchedule) -> Unit,
    onFinishSetup: () -> Unit,
) {
    val saved = alarm?.schedule
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val id = rememberSaveable { saved?.id ?: UUID.randomUUID().toString() }
    var timeText by rememberSaveable { mutableStateOf((saved?.wakeTime ?: LocalTime.of(7, 0)).toString()) }
    var weekly by rememberSaveable { mutableStateOf(saved != null && saved.onceDate == null) }
    var dateText by rememberSaveable { mutableStateOf((saved?.onceDate ?: nextSunriseDate(LocalTime.parse(timeText))).toString()) }
    var dateExplicitlyChosen by rememberSaveable { mutableStateOf(saved?.onceDate != null) }
    var weekdays by rememberSaveable { mutableIntStateOf(saved?.weekdays?.takeIf { it.isNotEmpty() }?.fold(0) { mask, day -> mask or (1 shl day.ordinal) } ?: 127) }
    val canSave = !busy && (callbackId ?: saved?.callbackId) != null && (canArm || saved?.enabled == false) && (!weekly || weekdays != 0)
    TaskContent {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Wake time", style = MaterialTheme.typography.labelLarge)
            FilledTonalButton(
                onClick = {
                    val time = LocalTime.parse(timeText)
                    TimePickerDialog(context, { _, hour, minute ->
                        timeText = LocalTime.of(hour, minute).toString()
                        if (!dateExplicitlyChosen) dateText = nextSunriseDate(LocalTime.parse(timeText)).toString()
                    }, time.hour, time.minute, DateFormat.is24HourFormat(context)).show()
                }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
            ) { Text(timeLabel(LocalTime.parse(timeText), context, locale), style = MaterialTheme.typography.headlineLarge.copy(fontFamily = MaterialTheme.typography.bodyLarge.fontFamily)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Repeat", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !weekly, onClick = { weekly = false }, label = { Text("One night") }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp))
                FilterChip(selected = weekly, onClick = { weekly = true }, label = { Text("Weekly") }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp))
            }
            if (weekly) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        val bit = 1 shl day.ordinal
                        FilterChip(
                            selected = weekdays and bit != 0, onClick = { weekdays = weekdays xor bit },
                            label = { Text(day.getDisplayName(TextStyle.NARROW, locale)) }, enabled = !busy,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics {
                                contentDescription = "Repeat on ${day.getDisplayName(TextStyle.FULL, locale)}"
                            },
                        )
                    }
                }
                if (weekdays == 0) TaskMessage("Choose at least one day.", error = true)
            } else {
                OutlinedButton(
                    onClick = {
                        val date = LocalDate.parse(dateText)
                        DatePickerDialog(context, { _, year, month, day ->
                            dateText = LocalDate.of(year, month + 1, day).toString()
                            dateExplicitlyChosen = true
                        }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(LocalDate.parse(dateText).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))) }
            }
        }
        if (error != null) TaskMessage(error, error = true)
        if (!canArm && saved?.enabled != false) {
            TextButton(onClick = onFinishSetup, enabled = !busy) { Text("Finish setup") }
        }
        Button(
            onClick = {
                onSave(SunriseSchedule(id, LocalTime.parse(timeText), if (weekly) null else LocalDate.parse(dateText),
                    if (weekly) DayOfWeek.entries.filter { weekdays and (1 shl it.ordinal) != 0 }.toSet() else emptySet(),
                    saved?.enabled ?: true, requireNotNull(callbackId ?: saved?.callbackId)))
            }, enabled = canSave, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(if (busy) "Saving…" else "Save") }
    }
}

private fun nextSunriseDate(time: LocalTime): LocalDate {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    return if (WakeScheduleResolver().resolve(today, time, zone).toInstant() > Instant.now()) today else today.plusDays(1)
}
