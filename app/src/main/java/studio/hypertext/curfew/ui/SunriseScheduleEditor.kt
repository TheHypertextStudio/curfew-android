package studio.hypertext.curfew.ui

import studio.hypertext.curfew.alarm.AlarmSetupDraft

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
    setupMode: Boolean = false,
    initialDraft: AlarmSetupDraft? = null,
    onPrepare: (AlarmSetupDraft) -> Unit = {},
) {
    val saved = alarm?.schedule
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val id = rememberSaveable { initialDraft?.id ?: saved?.id ?: UUID.randomUUID().toString() }
    var timeText by rememberSaveable { mutableStateOf((initialDraft?.wakeTime ?: saved?.wakeTime ?: LocalTime.of(7, 0)).toString()) }
    var weekly by rememberSaveable { mutableStateOf(initialDraft?.let { it.onceDate == null } ?: (saved != null && saved.onceDate == null)) }
    var dateText by rememberSaveable { mutableStateOf((initialDraft?.onceDate ?: saved?.onceDate ?: nextSunriseDate(LocalTime.parse(timeText))).toString()) }
    var dateExplicitlyChosen by rememberSaveable { mutableStateOf(initialDraft?.onceDate != null || saved?.onceDate != null) }
    var weekdays by rememberSaveable { mutableIntStateOf((initialDraft?.weekdays ?: saved?.weekdays)?.takeIf { it.isNotEmpty() }?.fold(0) { mask, day -> mask or (1 shl day.ordinal) } ?: 127) }
    var choosingTime by rememberSaveable { mutableStateOf(false) }
    var choosingDate by rememberSaveable { mutableStateOf(false) }
    val canSave = !busy && (callbackId ?: saved?.callbackId) != null && (canArm || saved?.enabled == false) && (!weekly || weekdays != 0)
    SetupTaskContent(
        introduction = {
            FilledTonalButton(
                onClick = { choosingTime = true }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).curfewSharedBounds("alarm-time"),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                DailyAlarmTime(LocalTime.parse(timeText), android.text.format.DateFormat.is24HourFormat(context), locale,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), maxFontSize = 76f)
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = !weekly, onClick = { weekly = false }, label = { Text("One night") }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp))
                FilterChip(selected = weekly, onClick = { weekly = true }, label = { Text("Weekly") }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp))
            }
            if (weekly) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val daysPerRow = if (maxWidth >= 48.dp * 7 + 2.dp * 6) 7 else 4
                    FlowRow(maxItemsInEachRow = daysPerRow, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                }
                if (weekdays == 0) TaskMessage("Choose at least one day.", error = true)
            } else {
                OutlinedButton(
                    onClick = { choosingDate = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(LocalDate.parse(dateText).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))) }
            }
        }
        if (error != null) TaskMessage(error, error = true)
        if (!setupMode && !canArm && saved?.enabled != false) {
            TextButton(onClick = onFinishSetup, enabled = !busy) { Text("Finish setup") }
        }
        Button(
            onClick = {
                val time = LocalTime.parse(timeText)
                val date = if (weekly) null else LocalDate.parse(dateText)
                val days = if (weekly) DayOfWeek.entries.filter { weekdays and (1 shl it.ordinal) != 0 }.toSet() else emptySet()
                if (setupMode) onPrepare(AlarmSetupDraft(id, time, date, days))
                else onSave(SunriseSchedule(id, time, date, days, saved?.enabled ?: true, requireNotNull(callbackId ?: saved?.callbackId)))
            }, enabled = if (setupMode) !busy && (!weekly || weekdays != 0) else canSave, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(if (busy) "Saving…" else if (setupMode) "Continue" else "Save") }
    }
    if (choosingTime) AlarmTimePicker(LocalTime.parse(timeText), onCancel = { choosingTime = false }) { time ->
        timeText = time.toString()
        if (!dateExplicitlyChosen) dateText = nextSunriseDate(time).toString()
        choosingTime = false
    }
    if (choosingDate) AlarmDatePicker(LocalDate.parse(dateText), onCancel = { choosingDate = false }) { date ->
        dateText = date.toString()
        dateExplicitlyChosen = true
        choosingDate = false
    }
}

private fun nextSunriseDate(time: LocalTime): LocalDate {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    return if (WakeScheduleResolver().resolve(today, time, zone).toInstant() > Instant.now()) today else today.plusDays(1)
}
