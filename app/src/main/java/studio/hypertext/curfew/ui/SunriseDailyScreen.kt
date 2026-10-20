package studio.hypertext.curfew.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import studio.hypertext.curfew.R
import studio.hypertext.curfew.alarm.SavedSunriseAlarm
import studio.hypertext.curfew.alarm.SunriseSchedule

@Composable
internal fun CurfewHorizonMark() {
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(28.dp, 24.dp)) {
        val scale = minOf(size.width / 48f, size.height / 36f)
        val left = (size.width - 48f * scale) / 2f
        val top = (size.height - 36f * scale) / 2f
        drawArc(Color(0xFFE98243), 180f, 180f, true, Offset(left + 7f * scale, top + 6f * scale), Size(34f * scale, 34f * scale))
        drawRect(ink, Offset(left + 2f * scale, top + 29f * scale), Size(44f * scale, 5f * scale))
    }
}

@Composable
internal fun TaskContent(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp), content = content,
    )
}

@Composable
internal fun TaskMessage(message: String, error: Boolean = false) {
    Text(message, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
internal fun SettingRow(title: String, detail: String? = null, icon: Int? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = detail?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(painterResource(it), null, tint = MaterialTheme.colorScheme.primary) } },
        trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right_rounded), null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(role = Role.Button, onClick = onClick),
    )
}

internal fun timeLabel(time: LocalTime, context: Context, locale: Locale): String = time.format(
    DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", locale),
)

internal fun recurrenceLabel(schedule: SunriseSchedule, locale: Locale): String {
    schedule.onceDate?.let { return it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) }
    if (schedule.weekdays.size == 7) return "Every day"
    if (schedule.weekdays == DayOfWeek.entries.take(5).toSet()) return "Monday–Friday"
    return DayOfWeek.entries.filter { it in schedule.weekdays }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
}

@Composable
internal fun SunriseDailyScreen(
    alarm: SavedSunriseAlarm?,
    conditionLabel: String?,
    conditionVerified: Boolean,
    canArm: Boolean,
    busy: Boolean,
    error: String?,
    onEdit: () -> Unit,
    onCheckIn: () -> Unit,
    onSetup: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val saved = alarm?.schedule
    val complete = saved?.onceDate?.let { date ->
        alarm.occurrence == null && alarm.completedThrough?.let { it >= date } == true
    } ?: false
    TaskContent {
        Text("Sunrise", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (saved == null) {
                    Text("No alarm set", style = MaterialTheme.typography.titleLarge)
                    FilledTonalButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { contentDescription = "Edit Sunrise" }) {
                        Text("Choose a wake time")
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(when { complete -> "One-night alarm complete"; saved.enabled -> "Alarm on"; else -> "Alarm off" },
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (!complete) Switch(
                            checked = saved.enabled, onCheckedChange = onToggle,
                            enabled = !busy && (saved.enabled || canArm),
                            modifier = Modifier.semantics { contentDescription = "Enable future Sunrise alarms" },
                        )
                    }
                    val time = timeLabel(saved.wakeTime, context, locale)
                    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onEdit).semantics { contentDescription = "Edit Sunrise" },
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BoxWithConstraints(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            val fontScale = LocalDensity.current.fontScale
                            val size = minOf(88f, maxWidth.value / (time.length * .58f * fontScale))
                            Text(time, style = MaterialTheme.typography.displayLarge.copy(
                                fontFamily = MaterialTheme.typography.bodyLarge.fontFamily, fontSize = size.sp,
                                lineHeight = (size * 1.12f).sp, letterSpacing = (-1).sp, fontFeatureSettings = "tnum",
                            ), maxLines = 1)
                        }
                        Icon(painterResource(R.drawable.ic_chevron_right_rounded), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (saved.onceDate == null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            DayOfWeek.entries.forEach { day ->
                                val selected = day in saved.weekdays
                                Surface(shape = MaterialTheme.shapes.extraLarge, color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent) {
                                    Box(Modifier.size(32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                        Text(day.getDisplayName(TextStyle.NARROW, locale), style = MaterialTheme.typography.labelLarge,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.semantics { contentDescription = "${day.getDisplayName(TextStyle.FULL, locale)}, ${if (selected) "selected" else "not selected"}" })
                                    }
                                }
                            }
                        }
                    }
                    val next = alarm.occurrence?.startsAt?.atZone(ZoneId.systemDefault())
                    val date = when {
                        complete -> saved.onceDate
                        saved.enabled && next != null -> next.toLocalDate()
                        else -> saved.onceDate
                    }
                    if (date != null) {
                        val prefix = if (!complete && saved.enabled && next != null) "Next · " else "One night · "
                        Text(prefix + date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            SettingRow(conditionLabel ?: "Check-in", if (conditionLabel == null) "Not connected" else if (!conditionVerified) "Not tested" else null,
                R.drawable.ic_link_rounded, onCheckIn)
        }
        if (error != null) TaskMessage(error, error = true)
        if (!canArm) Button(onClick = onSetup, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Finish setup") }
    }
}
