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
    initialWeekdayMask: Int? = null,
) {
    val saved = alarm?.schedule
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val id = rememberSaveable { initialDraft?.id ?: saved?.id ?: UUID.randomUUID().toString() }
    var timeText by rememberSaveable { mutableStateOf((initialDraft?.wakeTime ?: saved?.wakeTime ?: LocalTime.of(7, 0)).toString()) }
    var weekly by rememberSaveable { mutableStateOf(initialDraft?.let { it.onceDate == null } ?: (saved != null && saved.onceDate == null)) }
    var dateText by rememberSaveable { mutableStateOf((initialDraft?.onceDate ?: saved?.onceDate ?: nextSunriseDate(LocalTime.parse(timeText))).toString()) }
    var dateExplicitlyChosen by rememberSaveable { mutableStateOf(initialDraft?.onceDate != null || saved?.onceDate != null) }
    var weekdays by rememberSaveable { mutableIntStateOf(initialWeekdayMask ?: (initialDraft?.weekdays ?: saved?.weekdays)?.takeIf { it.isNotEmpty() }?.fold(0) { mask, day -> mask or (1 shl day.ordinal) } ?: 127) }
    var choosingTime by rememberSaveable { mutableStateOf(false) }
    var choosingDate by rememberSaveable { mutableStateOf(false) }
    var submitted by remember { mutableStateOf<SunriseSchedule?>(null) }
    val savedSnapshot = submitted != null && !busy && error == null && alarm?.schedule == submitted
    val unsavedChanges = submitted?.let { it.wakeTime.toString() != timeText || (it.onceDate == null) != weekly ||
        if (weekly) it.weekdays != DayOfWeek.entries.filter { day -> weekdays and (1 shl day.ordinal) != 0 }.toSet() else it.onceDate.toString() != dateText } ?: false
    val canSave = !busy && (callbackId ?: saved?.callbackId) != null && (canArm || saved?.enabled == false) && (!weekly || weekdays != 0)
    SetupTaskContent(
        introduction = {
            SetupIntroduction(studio.hypertext.curfew.R.drawable.ic_alarm_rounded,
                if (setupMode) "When do you want to wake up?" else "Edit your alarm",
                "Choose a wake time and the days your alarm should ring.")
            Text("Wake time", style = MaterialTheme.typography.titleMedium)
            FilledTonalButton(
                onClick = { choosingTime = true }, enabled = !busy || !setupMode,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).curfewSharedBounds("alarm-time"),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                DailyAlarmTime(LocalTime.parse(timeText), android.text.format.DateFormat.is24HourFormat(context), locale,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), maxFontSize = 76f)
            }
        },
    ) {
        Text("Schedule", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = !weekly, onClick = { weekly = false }, label = { Text("Once") }, enabled = !busy || !setupMode, modifier = Modifier.heightIn(min = 48.dp))
                FilterChip(selected = weekly, onClick = { weekly = true }, label = { Text("Repeat") }, enabled = !busy || !setupMode, modifier = Modifier.heightIn(min = 48.dp))
            }
            if (weekly) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val dayWidth = maxWidth
                    val daysPerRow = if (maxWidth >= 72.dp * 7 + 8.dp * 6) 7 else if (maxWidth >= 72.dp * 4 + 8.dp * 3) 4 else 3
                    FlowRow(maxItemsInEachRow = daysPerRow, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            val bit = 1 shl day.ordinal
                            FilterChip(
                                selected = weekdays and bit != 0, onClick = { weekdays = weekdays xor bit },
                                label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) }, enabled = !busy || !setupMode,
                                modifier = Modifier.width((dayWidth - 8.dp * (daysPerRow - 1)) / daysPerRow).heightIn(min = 52.dp).semantics {
                                    contentDescription = "Repeat on ${day.getDisplayName(TextStyle.FULL, locale)}"
                                },
                            )
                        }
                    }
                }
                if (weekdays == 0) TaskMessage("Choose at least one day.", error = true)
            } else {
                OutlinedButton(
                    onClick = { choosingDate = true }, enabled = !busy || !setupMode, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(LocalDate.parse(dateText).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))) }
            }
        }
        if (savedSnapshot) TaskMessage(if (unsavedChanges) "Previous changes saved. You have unsaved changes." else "Alarm saved.")
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (error != null) TaskMessage(error, error = true)
        Button(
            onClick = {
                if (!setupMode && !canArm && saved?.enabled != false) {
                    onFinishSetup()
                    return@Button
                }
                val time = LocalTime.parse(timeText)
                val date = if (weekly) null else LocalDate.parse(dateText)
                val days = if (weekly) DayOfWeek.entries.filter { weekdays and (1 shl it.ordinal) != 0 }.toSet() else emptySet()
                if (setupMode) onPrepare(AlarmSetupDraft(id, time, date, days))
                else {
                    val snapshot = SunriseSchedule(id, time, date, days, saved?.enabled ?: true, requireNotNull(callbackId ?: saved?.callbackId))
                    submitted = snapshot
                    onSave(snapshot)
                }
            }, enabled = if (setupMode) !busy && (!weekly || weekdays != 0) else if (!canArm && saved?.enabled != false) !busy else canSave, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(if (busy) "Saving…" else if (setupMode) "Continue" else if (!canArm && saved?.enabled != false) "Continue alarm setup" else "Save alarm") }
    }
    if (choosingTime) AlarmTimePicker(LocalTime.parse(timeText), onCancel = { choosingTime = false }, onDone = { time ->
        timeText = time.toString()
        if (!dateExplicitlyChosen) dateText = nextSunriseDate(time).toString()
        choosingTime = false
    })
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
