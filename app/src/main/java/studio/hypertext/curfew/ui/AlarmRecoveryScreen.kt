package studio.hypertext.curfew.ui

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import studio.hypertext.curfew.R
import studio.hypertext.curfew.alarm.OWNER_RECOVERY_HOLD_MILLIS
import studio.hypertext.curfew.alarm.OWNER_RECOVERY_REASON_MINIMUM

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmRecoveryScreen(
    conditionLabel: String,
    credentialAvailable: Boolean,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onBeginHold: (String) -> Unit,
    onCancelHold: () -> Unit,
    onConfirm: () -> Unit,
    onSecuritySettings: () -> Unit,
    initialReason: String = "",
) {
    var reason by rememberSaveable { mutableStateOf(initialReason) }
    val trimmed = reason.trim()
    val length = trimmed.codePointCount(0, trimmed.length)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alarm recovery") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back_rounded), "Back")
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding().padding(24.dp)) {
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Spacer(Modifier.height(16.dp))
                }
                if (credentialAvailable) {
                    key(trimmed) {
                    RecoveryHoldButton(
                        enabled = length >= OWNER_RECOVERY_REASON_MINIMUM && !busy,
                        busy = busy,
                        onBegin = { onBeginHold(trimmed) },
                        onCancel = onCancelHold,
                        onConfirm = onConfirm,
                    )
                    }
                } else {
                    Button(onClick = onSecuritySettings, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text("Open security settings")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState(LocalTaskInitialScroll.current)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (credentialAvailable) {
                Text("This stops the alarm without completing $conditionLabel.", style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                enabled = !busy,
                label = { Text("What happened?") },
                minLines = 4,
                supportingText = if (length < OWNER_RECOVERY_REASON_MINIMUM) {
                    { Text(if (length == 0) "At least 50 characters" else "${OWNER_RECOVERY_REASON_MINIMUM - length} more characters") }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )
            } else {
                Text("Set a screen lock to use recovery.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RecoveryHoldButton(
    enabled: Boolean,
    busy: Boolean,
    onBegin: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var progress by remember { mutableFloatStateOf(0f) }
    val interactions = remember { MutableInteractionSource() }
    var timer by remember { mutableStateOf<Job?>(null) }
    var accessibleHold by remember { mutableStateOf(false) }
    val latestBegin by rememberUpdatedState(onBegin)
    val latestCancel by rememberUpdatedState(onCancel)
    val latestConfirm by rememberUpdatedState(onConfirm)
    val latestEnabled by rememberUpdatedState(enabled)

    fun cancel() {
        timer?.cancel()
        timer = null
        accessibleHold = false
        progress = 0f
        latestCancel()
    }

    fun start(accessible: Boolean) {
        if (!latestEnabled || timer != null) return
        latestBegin()
        accessibleHold = accessible
        timer = scope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            // Reduced motion must not shorten the confirmation or finish its indicator early.
            do {
                withContext(Dispatchers.Default) { delay(50) }
                progress = ((SystemClock.elapsedRealtime() - startedAt).toFloat() / OWNER_RECOVERY_HOLD_MILLIS).coerceAtMost(1f)
            } while (progress < 1f)
            latestConfirm()
            accessibleHold = false
            timer = null
            progress = 0f
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) cancel()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            timer?.cancel()
            latestCancel()
        }
    }
    Button(
        onClick = { if (accessibleHold) cancel() else start(accessible = true) },
        enabled = enabled,
        interactionSource = interactions,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .onFocusChanged { if (!it.isFocused && timer != null) cancel() }
            .onPreviewKeyEvent {
                if (it.key !in listOf(Key.Enter, Key.NumPadEnter, Key.Spacebar)) false
                else {
                    when (it.type) {
                        KeyEventType.KeyDown -> if (it.nativeKeyEvent.repeatCount == 0) start(accessible = false)
                        KeyEventType.KeyUp -> cancel()
                    }
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                    down.consume()
                    if (!latestEnabled) return@awaitEachGesture
                    val press = PressInteraction.Press(down.position)
                    scope.launch { interactions.emit(press) }
                    start(accessible = false)
                    val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    up?.consume()
                    scope.launch { interactions.emit(if (up != null) PressInteraction.Release(press) else PressInteraction.Cancel(press)) }
                    cancel()
                }
            },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (busy) "Confirming…" else if (accessibleHold) "Cancel confirmation" else "Hold for 3 seconds")
            if (timer != null) {
                Spacer(Modifier.height(2.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    color = MaterialTheme.colorScheme.onPrimary,
                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
