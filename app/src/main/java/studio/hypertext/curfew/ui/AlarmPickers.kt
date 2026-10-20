package studio.hypertext.curfew.ui

import android.text.format.DateFormat
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlarmTimePicker(initial: LocalTime, onCancel: () -> Unit, onDone: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val use24Hour = DateFormat.is24HourFormat(context)
    val state = rememberTimePickerState(initial.hour, initial.minute, use24Hour)
    var inputMode by rememberSaveable { mutableStateOf(false) }
    var hourText by rememberSaveable(state.hour) {
        mutableStateOf((if (use24Hour) state.hour else (state.hour % 12).takeIf { it != 0 } ?: 12).toString())
    }
    var minuteText by rememberSaveable(state.minute) { mutableStateOf(state.minute.toString().padStart(2, '0')) }
    var afternoon by rememberSaveable(state.hour) { mutableStateOf(state.hour >= 12) }
    AlarmPickerSurface("Wake time", onCancel) { compact ->
        val showInput = compact || inputMode
        LaunchedEffect(compact) { if (compact) inputMode = true }
        val hour = hourText.toIntOrNull()?.takeIf { it in if (use24Hour) 0..23 else 1..12 }
        val minute = minuteText.toIntOrNull()?.takeIf { it in 0..59 }
        val pendingTime = if (hour != null && minute != null) LocalTime.of(
            if (use24Hour) hour else hour % 12 + if (afternoon) 12 else 0, minute,
        ) else null
        val valid = if (showInput) pendingTime != null else state.isInputValid
        val canChangeMode = !showInput || valid
        PickerContents(
            onCancel = onCancel,
            onDone = { onDone(if (showInput) requireNotNull(pendingTime) else LocalTime.of(state.hour, state.minute)) },
            valid = valid,
            modeToggle = if (compact) null else {
                {
                    TimePickerDialogDefaults.DisplayModeToggle(
                        onDisplayModeChange = {
                            if (canChangeMode) {
                                if (inputMode) pendingTime?.let { state.hour = it.hour; state.minute = it.minute }
                                inputMode = !inputMode
                            }
                        },
                        displayMode = if (inputMode) TimePickerDisplayMode.Input else TimePickerDisplayMode.Picker,
                        // The public Material toggle has no enabled parameter.
                        modifier = Modifier.testTag("time-input-mode").alpha(if (canChangeMode) 1f else 0.38f)
                            .semantics { if (!canChangeMode) disabled() },
                    )
                }
            },
        ) {
            if (showInput) {
                val focus = LocalFocusManager.current
                val keyboard = LocalSoftwareKeyboardController.current
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = hourText, onValueChange = { hourText = it }, label = { Text("Hour") },
                            isError = hourText.isNotEmpty() && hour == null, singleLine = true,
                            supportingText = if (hourText.isNotEmpty() && hour == null) { { Text(if (use24Hour) "0–23" else "1–12") } } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Next) }),
                            modifier = Modifier.weight(1f).testTag("alarm-hour"),
                        )
                        OutlinedTextField(
                            value = minuteText, onValueChange = { minuteText = it }, label = { Text("Minute") },
                            isError = minuteText.isNotEmpty() && minute == null, singleLine = true,
                            supportingText = if (minuteText.isNotEmpty() && minute == null) { { Text("0–59") } } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focus.clearFocus() }),
                            modifier = Modifier.weight(1f).testTag("alarm-minute"),
                        )
                    }
                    if (!use24Hour) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (pm in listOf(false, true)) FilterChip(
                            selected = afternoon == pm, onClick = { afternoon = pm },
                            label = { Text(LocalTime.of(if (pm) 12 else 0, 0).format(DateTimeFormatter.ofPattern("a", locale))) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag(if (pm) "alarm-pm" else "alarm-am"),
                        )
                    }
                }
            } else {
                val typography = MaterialTheme.typography
                MaterialTheme(typography = typography.copy(displayLarge = typography.displayLarge.copy(fontFamily = typography.bodyLarge.fontFamily))) {
                    TimePicker(state)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlarmDatePicker(initial: LocalDate, onCancel: () -> Unit, onDone: (LocalDate) -> Unit) {
    AlarmPickerSurface("One-night date", onCancel, comfortableWidth = 360.dp, outsidePadding = 12.dp) { compact ->
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialDisplayMode = if (compact) DisplayMode.Input else DisplayMode.Picker,
        )
        LaunchedEffect(compact) { if (compact) state.displayMode = DisplayMode.Input }
        PickerContents(onCancel, {
            state.selectedDateMillis?.let { onDone(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
        }, valid = state.selectedDateMillis != null, bodyHorizontalPadding = 0.dp,
            modeToggle = if (compact) null else {
                { TextButton(onClick = { state.displayMode = if (state.displayMode == DisplayMode.Input) DisplayMode.Picker else DisplayMode.Input },
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(if (state.displayMode == DisplayMode.Input) "Calendar" else "Type date") } }
            },
        ) {
            DatePicker(state, title = null, headline = null, showModeToggle = false)
        }
    }
}

@Composable
private fun AlarmPickerSurface(title: String, onCancel: () -> Unit, comfortableWidth: androidx.compose.ui.unit.Dp = 352.dp, outsidePadding: androidx.compose.ui.unit.Dp = 16.dp, content: @Composable ColumnScope.(Boolean) -> Unit) {
    val dismiss by rememberUpdatedState(onCancel)
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(
            Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { dismiss() }) }.safeDrawingPadding().imePadding().padding(outsidePadding),
            contentAlignment = Alignment.Center,
        ) {
            val compact = maxWidth < comfortableWidth || LocalDensity.current.fontScale > 1f
            Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().pointerInput(Unit) { detectTapGestures(onTap = {}) }, shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
                Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                    content(compact)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.PickerContents(
    onCancel: () -> Unit,
    onDone: () -> Unit,
    valid: Boolean,
    modeToggle: (@Composable () -> Unit)? = null,
    bodyHorizontalPadding: androidx.compose.ui.unit.Dp = 24.dp,
    content: @Composable () -> Unit,
) {
    Box(Modifier.weight(1f, fill = false).fillMaxWidth().padding(horizontal = bodyHorizontalPadding).verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) { content() }
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        modeToggle?.invoke()
        TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        TextButton(onClick = onDone, enabled = valid, modifier = Modifier.heightIn(min = 48.dp)) { Text("Done") }
    }
}
