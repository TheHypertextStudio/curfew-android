package studio.hypertext.curfew.ui

import studio.hypertext.curfew.alarm.AlarmSetupDraft

import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import studio.hypertext.curfew.R

@Composable
internal fun AlarmSetupReview(
    draft: AlarmSetupDraft,
    conditionLabel: String,
    busy: Boolean,
    error: String?,
    onEdit: () -> Unit = {},
    onActivate: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    SetupTaskContent(
        introduction = {
            SetupIntroduction(R.drawable.ic_alarm_rounded, "Review your alarm", "Check your wake time and check-in before turning the alarm on.")
            Column(Modifier.fillMaxWidth().clickable(enabled = !busy, role = Role.Button, onClick = onEdit)
                .semantics { contentDescription = "Edit wake time" }, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Surface(modifier = Modifier.fillMaxWidth().curfewSharedBounds("alarm-time"), shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.secondaryContainer) {
                    DailyAlarmTime(draft.wakeTime, android.text.format.DateFormat.is24HourFormat(context), locale,
                        modifier = Modifier.padding(24.dp), maxFontSize = 76f)
                }
                if (draft.onceDate != null) {
                    Text(draft.onceDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)), style = MaterialTheme.typography.bodyLarge)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        draft.weekdays.sortedBy { it.value }.forEach { day ->
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(day.getDisplayName(TextStyle.SHORT, locale), modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                            }
                        }
                    }
                }
            }
        },
    ) {
        CheckInSummary(conditionLabel, verified = true)
        Text("The alarm rings until you check in.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (error != null) TaskMessage(error, error = true)
        Button(onClick = onActivate, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(if (busy) "Turning on…" else "Turn on alarm")
        }
    }
}
