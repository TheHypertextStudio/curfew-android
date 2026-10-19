package studio.hypertext.curfew.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import java.time.format.TextStyle
import java.util.UUID
import studio.hypertext.curfew.alarm.SavedSunriseAlarm
import studio.hypertext.curfew.alarm.SunriseSchedule
import studio.hypertext.curfew.alarm.WakeScheduleResolver

@Composable
fun SunriseAlarmCard(
    alarm: SavedSunriseAlarm?,
    callbackId: String?,
    conditionLabel: String?,
    canArm: Boolean,
    busy: Boolean,
    onSave: (SunriseSchedule) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    val saved = alarm?.schedule
    val key = saved?.toString()
    var draftId by rememberSaveable(key) { mutableStateOf(saved?.id ?: UUID.randomUUID().toString()) }
    var timeText by rememberSaveable(key) { mutableStateOf(saved?.wakeTime?.toString() ?: "07:00") }
    var repeating by rememberSaveable(key) { mutableStateOf(saved?.onceDate == null && saved != null) }
    var dateText by rememberSaveable(key) {
        mutableStateOf((saved?.onceDate ?: nextNight(LocalTime.parse(timeText))).toString())
    }
    var weekdays by rememberSaveable(key) {
        mutableIntStateOf(saved?.weekdays?.takeIf { it.isNotEmpty() }?.fold(0) { mask, day ->
            mask or (1 shl day.ordinal)
        } ?: 127)
    }
    val conditionId = callbackId ?: saved?.callbackId
    val enabled = saved?.enabled ?: true

    CurfewCard("Sunrise") {
        val occurrence = alarm?.occurrence
        val status = when {
            occurrence != null && occurrence.startsAt <= Instant.now() -> "Sunrise is active"
            occurrence != null -> "Next Sunrise: ${occurrence.startsAt.atZone(ZoneId.systemDefault()).format(
                DateTimeFormatter.ofPattern("EEE, MMM d · HH:mm"),
            )}"
            alarm?.completedThrough != null && !repeating -> "Your one-night Sunrise is complete"
            saved != null && !saved.enabled -> "Sunrise is off"
            else -> "Choose when to wake up"
        }
        Text(status, style = MaterialTheme.typography.titleMedium)
        if (saved != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(if (enabled) "Future alarms are on" else "Future alarms are off")
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggleEnabled,
                    enabled = !busy && (!enabled && canArm || enabled),
                    modifier = Modifier.semantics { contentDescription = "Enable future Sunrise alarms" },
                )
            }
            Text(
                "An active Sunrise still needs its release condition.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("Wake time", style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            onClick = {
                val time = LocalTime.parse(timeText)
                TimePickerDialog(context, { _, hour, minute ->
                    timeText = LocalTime.of(hour, minute).toString()
                    if (saved == null) dateText = nextNight(LocalTime.of(hour, minute)).toString()
                }, time.hour, time.minute, true).show()
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        ) {
            Text(timeText, style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !repeating,
                onClick = { repeating = false },
                label = { Text("One night") },
                enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp),
            )
            FilterChip(
                selected = repeating,
                onClick = { repeating = true },
                label = { Text("Repeat weekly") },
                enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        if (repeating) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { day ->
                    val bit = 1 shl day.ordinal
                    FilterChip(
                        selected = weekdays and bit != 0,
                        onClick = { weekdays = weekdays xor bit },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) },
                        enabled = !busy,
                        modifier = Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription = "Repeat on ${day.getDisplayName(TextStyle.FULL, locale)}"
                        },
                    )
                }
            }
            if (weekdays == 0) Text("Choose at least one day.", color = MaterialTheme.colorScheme.error)
        } else {
            OutlinedButton(
                onClick = {
                    val date = LocalDate.parse(dateText)
                    DatePickerDialog(context, { _, year, month, day ->
                        dateText = LocalDate.of(year, month + 1, day).toString()
                    }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(LocalDate.parse(dateText).format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy"))) }
        }
        Spacer(Modifier.height(16.dp))
        Text("Release: ${conditionLabel ?: "no wake condition configured"}")
        Text(
            "Sunrise keeps ringing until this condition succeeds or you use authorized recovery.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!canArm) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Configure and test a wake condition before turning Sunrise on.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                onSave(SunriseSchedule(
                    id = draftId,
                    wakeTime = LocalTime.parse(timeText),
                    onceDate = if (repeating) null else LocalDate.parse(dateText),
                    weekdays = if (repeating) DayOfWeek.entries.filter {
                        weekdays and (1 shl it.ordinal) != 0
                    }.toSet() else emptySet(),
                    enabled = enabled,
                    callbackId = requireNotNull(conditionId),
                ))
            },
            enabled = !busy && conditionId != null && (!enabled || canArm) && (!repeating || weekdays != 0),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text(if (busy) "Saving Sunrise…" else "Save Sunrise") }
    }
}

private fun nextNight(time: LocalTime): LocalDate {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    return if (WakeScheduleResolver().resolve(today, time, zone).toInstant() > Instant.now()) {
        today
    } else {
        today.plusDays(1)
    }
}
