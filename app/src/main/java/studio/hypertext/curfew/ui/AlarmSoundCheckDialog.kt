package studio.hypertext.curfew.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import studio.hypertext.curfew.readiness.AlarmSoundCheckState

@Composable
internal fun AlarmSoundCheckDialog(
    state: AlarmSoundCheckState,
    onConfirm: () -> Unit,
    onReplay: () -> Unit,
    onCancel: () -> Unit,
) {
    if (state == AlarmSoundCheckState.CLOSED) return
    val busy = state == AlarmSoundCheckState.PLAYING || state == AlarmSoundCheckState.SAVING
    val heard = state == AlarmSoundCheckState.AWAITING_CONFIRMATION || state == AlarmSoundCheckState.SAVE_FAILED
    AlertDialog(
        onDismissRequest = { if (state != AlarmSoundCheckState.SAVING) onCancel() },
        title = { Text(when (state) {
            AlarmSoundCheckState.FAILED -> "No sound played"
            AlarmSoundCheckState.SAVE_FAILED -> "Couldn’t save confirmation"
            AlarmSoundCheckState.PLAYING -> "Alarm sound"
            AlarmSoundCheckState.SAVING -> "Saving…"
            else -> "Did you hear the alarm?"
        }, modifier = Modifier.fillMaxWidth()) },
        text = if (state == AlarmSoundCheckState.SAVE_FAILED) null else {
            {
                when (state) {
                    AlarmSoundCheckState.FAILED -> Text("Check your alarm volume and try again.", modifier = Modifier.fillMaxWidth())
                    AlarmSoundCheckState.AWAITING_CONFIRMATION -> TextButton(onClick = onReplay) { SoundCheckActionText("Play again") }
                    else -> CircularProgressIndicator(Modifier.size(24.dp))
                }
            }
        },
        confirmButton = {
            if (!busy) TextButton(onClick = if (heard) onConfirm else onReplay) {
                SoundCheckActionText(if (state == AlarmSoundCheckState.SAVE_FAILED) "Try saving again" else if (heard) "I heard it" else "Try again")
            }
        },
        dismissButton = { TextButton(onClick = onCancel, enabled = state != AlarmSoundCheckState.SAVING) { SoundCheckActionText("Not now") } },
    )
}

@Composable
private fun SoundCheckActionText(label: String) {
    val density = LocalDensity.current
    val width = rememberTextMeasurer().measure(label, LocalTextStyle.current, maxLines = 1).size.width + 2
    // Intrinsic rounding can under-allocate custom-font labels by a pixel.
    Text(label, modifier = Modifier.width(with(density) { width.toDp() }))
}
