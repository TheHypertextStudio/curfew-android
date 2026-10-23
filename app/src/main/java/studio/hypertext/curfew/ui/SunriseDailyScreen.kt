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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
internal fun CurfewHorizonMark(modifier: Modifier = Modifier.size(28.dp, 24.dp)) {
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val scale = minOf(size.width / 48f, size.height / 36f)
        val left = (size.width - 48f * scale) / 2f
        val top = (size.height - 36f * scale) / 2f
        drawArc(Color(0xFFE98243), 180f, 180f, true, Offset(left + 7f * scale, top + 6f * scale), Size(34f * scale, 34f * scale))
        drawRect(ink, Offset(left + 2f * scale, top + 29f * scale), Size(44f * scale, 5f * scale))
    }
}

@Composable
internal fun TaskContent(spacing: Dp = 24.dp, verticalPadding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    // The screen scaffold owns system insets; this content may occupy a smaller pane.
    BoxWithConstraints(Modifier.widthIn(max = 640.dp).fillMaxSize().imePadding()) {
        val gutter = if (maxWidth < 360.dp) 16.dp else 24.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = gutter, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(spacing), content = content,
        )
    }
}

@Composable
internal fun TaskMessage(message: String, error: Boolean = false) {
    Text(message, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
internal fun SettingRow(title: String, detail: String? = null, icon: Int? = null, onClick: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val showLeadingIcon = maxWidth.value / LocalDensity.current.fontScale.coerceAtLeast(1f) >= 180f
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = detail?.let { { Text(it) } },
            leadingContent = icon?.takeIf { showLeadingIcon }?.let { { Icon(painterResource(it), null, tint = MaterialTheme.colorScheme.primary) } },
            trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right_rounded), null) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick),
        )
    }
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Leave room for recurrence and the check-in action when the viewport is short.
        val timeSizeLimit = (maxHeight.value * 0.30f / LocalDensity.current.fontScale).coerceIn(48f, 100f)
        TaskContent(spacing = 12.dp, verticalPadding = 12.dp) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (saved == null) {
                        Text("No alarm set", style = MaterialTheme.typography.titleLarge)
                        FilledTonalButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { contentDescription = "Edit alarm" }) {
                            Text("Choose a wake time")
                        }
                    } else {
                        if (complete || saved.enabled && !canArm) {
                            Text(if (complete) "One-night alarm complete" else "Needs attention",
                                style = MaterialTheme.typography.titleMedium)
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.weight(1f).heightIn(min = 48.dp).curfewSharedBounds("alarm-time").clickable(role = Role.Button, onClick = onEdit)
                                .semantics {
                                    contentDescription = "Edit alarm"
                                    alarm.occurrence?.startsAt?.atZone(ZoneId.systemDefault())?.let { occurrence ->
                                        stateDescription = "${occurrence.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))}, ${timeLabel(occurrence.toLocalTime(), context, locale)}"
                                    }
                                }) {
                                DailyAlarmTime(saved.wakeTime, DateFormat.is24HourFormat(context), locale, Modifier.fillMaxWidth(), timeSizeLimit)
                            }
                            if (!complete) Switch(
                                checked = saved.enabled, onCheckedChange = onToggle,
                                enabled = !busy && (saved.enabled || canArm),
                                modifier = Modifier.semantics { contentDescription = "Alarm" },
                            )
                        }
                        if (saved.onceDate == null) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                DayOfWeek.entries.forEach { day ->
                                    val selected = day in saved.weekdays
                                    Surface(modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.extraLarge, color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent) {
                                        Box(Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(vertical = 6.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                            Text(day.getDisplayName(TextStyle.NARROW, locale), style = MaterialTheme.typography.labelLarge,
                                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${day.getDisplayName(TextStyle.FULL, locale)}, ${if (selected) "selected" else "not selected"}" })
                                        }
                                    }
                                }
                            }
                        }
                        saved.onceDate?.let { date ->
                            Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)),
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
}

@Composable
internal fun DailyAlarmTime(time: LocalTime, use24Hour: Boolean, locale: Locale, modifier: Modifier = Modifier, maxFontSize: Float = 100f) {
    val numeric = time.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm", locale))
    val period = if (use24Hour) null else time.format(DateTimeFormatter.ofPattern("a", locale))
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val numericStyle = MaterialTheme.typography.displayLarge.copy(
        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily, letterSpacing = (-1).sp, fontFeatureSettings = "tnum",
    )
    val periodStyle = MaterialTheme.typography.titleMedium
    BoxWithConstraints(modifier) {
        val periodWidth = period?.let { measurer.measure(it, periodStyle).size.width + 2 }
        // Keep the time dominant before choosing a shared AM/PM baseline.
        val minimumInlineTime = measurer.measure(numeric, numericStyle.copy(fontSize = (periodStyle.fontSize.value * 2.5f).sp), maxLines = 1).size.width + 2
        val inlinePeriod = periodWidth != null && minimumInlineTime + periodWidth + with(density) { 8.dp.toPx() } <= constraints.maxWidth
        val available = constraints.maxWidth - 2f - if (inlinePeriod) {
            requireNotNull(periodWidth) + with(density) { 8.dp.toPx() }
        } else 0f
        var low = 8f
        var high = maxFontSize
        repeat(10) {
            val candidate = (low + high) / 2f
            val width = measurer.measure(numeric, numericStyle.copy(fontSize = candidate.sp), maxLines = 1).size.width
            if (width <= available) low = candidate else high = candidate
        }
        val fittedStyle = numericStyle.copy(fontSize = low.sp, lineHeight = low.sp)
        if (inlinePeriod) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                val numericWidth = measurer.measure(numeric, fittedStyle, maxLines = 1).size.width + 2
                Text(numeric, style = fittedStyle, maxLines = 1, modifier = Modifier.width(with(density) { numericWidth.toDp() }).alignByBaseline())
                Text(requireNotNull(period), style = periodStyle, modifier = Modifier.width(with(density) { requireNotNull(periodWidth).toDp() }).alignByBaseline())
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(numeric, style = fittedStyle, maxLines = 1, modifier = Modifier.fillMaxWidth())
                if (period != null) Text(period, style = periodStyle, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
