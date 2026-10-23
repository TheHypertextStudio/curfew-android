package studio.hypertext.curfew.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import studio.hypertext.curfew.R
import studio.hypertext.curfew.account.AccountRecoveryState
import studio.hypertext.curfew.readiness.AlarmReadiness
import studio.hypertext.curfew.readiness.ReadinessAction

@Composable
internal fun CurfewSettingsScreen(
    conditionLabel: String?, conditionVerified: Boolean, readinessComplete: Boolean, signedIn: Boolean,
    onCheckIn: () -> Unit, onReadiness: () -> Unit, onAccount: () -> Unit,
) {
    TaskContent {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column {
                SettingRow("Check-in", conditionLabel?.let { if (conditionVerified) it else "$it · Not tested" } ?: "Not connected", R.drawable.ic_link_rounded, onCheckIn)
                SettingRow("Alarm setup", if (readinessComplete) "Ready" else "Needs attention", R.drawable.ic_lock_rounded, onReadiness)
            }
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            SettingRow("Account", if (signedIn) "Signed in" else "Not signed in · Optional", onClick = onAccount)
        }
    }
}

@Composable
internal fun AlarmReadinessScreen(
    readiness: AlarmReadiness,
    onGrantExactAlarm: () -> Unit,
    onGrantNotifications: () -> Unit,
    onRepairAlarmChannel: () -> Unit,
    onPlayTestSound: () -> Unit,
    onAcknowledgeLimitations: () -> Unit,
    onRaiseAlarmVolume: () -> Unit,
    onDone: () -> Unit,
    onGrantFullScreen: () -> Unit = {},
    inSetup: Boolean = false,
    initiallyShowLimits: Boolean = false,
    soundState: studio.hypertext.curfew.readiness.AlarmSoundCheckState = studio.hypertext.curfew.readiness.AlarmSoundCheckState.CLOSED,
    onConfirmSound: () -> Unit = {},
    onCancelSound: () -> Unit = {},
) {
    LaunchedEffect(readiness.canArm, inSetup) {
        if (inSetup && readiness.canArm) onDone()
    }
    var showLimits by rememberSaveable { mutableStateOf(initiallyShowLimits) }
    val next = listOf(
        ReadinessAction.GRANT_EXACT_ALARM_ACCESS,
        ReadinessAction.GRANT_NOTIFICATIONS,
        ReadinessAction.GRANT_FULL_SCREEN_ACCESS,
        ReadinessAction.REPAIR_ALARM_CHANNEL,
        ReadinessAction.RAISE_ALARM_VOLUME,
        ReadinessAction.PLAY_TEST_SOUND,
        ReadinessAction.ACKNOWLEDGE_LIMITATIONS,
    ).firstOrNull { it in readiness.requiredActions }
    val title = when (next) {
        ReadinessAction.GRANT_EXACT_ALARM_ACCESS -> "Ring on time"
        ReadinessAction.GRANT_NOTIFICATIONS -> "Show alarm notifications"
        ReadinessAction.GRANT_FULL_SCREEN_ACCESS -> "On your lock screen"
        ReadinessAction.REPAIR_ALARM_CHANNEL -> "Allow alarm sound"
        ReadinessAction.RAISE_ALARM_VOLUME -> "Turn up the alarm"
        ReadinessAction.PLAY_TEST_SOUND -> "Try your alarm"
        ReadinessAction.ACKNOWLEDGE_LIMITATIONS -> "Before you rely on your alarm"
        null -> "Your alarm checks are complete"
    }
    val purpose = when (next) {
        ReadinessAction.GRANT_EXACT_ALARM_ACCESS -> "Allow Curfew to ring at your chosen time."
        ReadinessAction.GRANT_NOTIFICATIONS -> "See and open your ringing alarm from a notification, even when another app is open."
        ReadinessAction.GRANT_FULL_SCREEN_ACCESS -> "Allow Curfew to appear over your lock screen."
        ReadinessAction.REPAIR_ALARM_CHANNEL -> "Allow sound for Curfew’s alarm notification."
        ReadinessAction.RAISE_ALARM_VOLUME -> "Your alarm volume is muted."
        ReadinessAction.PLAY_TEST_SOUND -> "Play a short preview at your current alarm volume. Then tell us whether you heard it."
        ReadinessAction.ACKNOWLEDGE_LIMITATIONS -> "Unlock your phone after restarting so alarms can resume."
        null -> "Android permissions are enabled and you’ve confirmed the alarm sound. Review your wake time before turning the alarm on."
    }
    val (label, callback) = when (next) {
        ReadinessAction.GRANT_EXACT_ALARM_ACCESS -> "Allow exact alarms" to onGrantExactAlarm
        ReadinessAction.GRANT_NOTIFICATIONS -> "Allow notifications" to onGrantNotifications
        ReadinessAction.GRANT_FULL_SCREEN_ACCESS -> "Allow alarm screen" to onGrantFullScreen
        ReadinessAction.REPAIR_ALARM_CHANNEL -> "Check alarm sound" to onRepairAlarmChannel
        ReadinessAction.RAISE_ALARM_VOLUME -> "Turn up alarm volume" to onRaiseAlarmVolume
        ReadinessAction.PLAY_TEST_SOUND -> "Test alarm sound" to onPlayTestSound
        ReadinessAction.ACKNOWLEDGE_LIMITATIONS -> "I understand" to onAcknowledgeLimitations
        null -> (if (inSetup) "Continue" else "Done") to onDone
    }
    SetupTaskContent(
        introduction = {
            if (next == ReadinessAction.PLAY_TEST_SOUND || soundState != studio.hypertext.curfew.readiness.AlarmSoundCheckState.CLOSED) {
                Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                if (purpose != null) Text(purpose, style = MaterialTheme.typography.bodyLarge)
            } else SetupIntroduction(R.drawable.ic_alarm_rounded, title, purpose)
        },
    ) {
        if (next == ReadinessAction.ACKNOWLEDGE_LIMITATIONS) {
            Text("Keep your phone powered on", style = MaterialTheme.typography.titleMedium)
            Text("An alarm cannot ring while your phone is off. After restarting, unlock it once so Curfew can resume your alarms.")
            Text("Check settings that can silence alarms", style = MaterialTheme.typography.titleMedium)
            Text("Allow alarms through Do Not Disturb and your phone’s battery restrictions. Force-stopping or uninstalling Curfew prevents it from ringing.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (next == ReadinessAction.PLAY_TEST_SOUND || soundState != studio.hypertext.curfew.readiness.AlarmSoundCheckState.CLOSED) {
            AlarmSoundTestContent(soundState, onPlayTestSound, onConfirmSound, onCancelSound)
        } else {
            Button(onClick = callback, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(label, textAlign = TextAlign.Center) }
        }
        if (readiness.canArm && soundState == studio.hypertext.curfew.readiness.AlarmSoundCheckState.CLOSED) {
            TextButton(onClick = onPlayTestSound, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Test alarm sound") }
        }
        if (next == null && !inSetup) {
            TextButton(onClick = { showLimits = !showLimits }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (showLimits) "Hide alarm reliability details" else "What can prevent my alarm?") }
            if (showLimits) {
                Text("Unlock your phone after restarting so alarms can resume. Powering off, force-stopping, or uninstalling Curfew can prevent the alarm. Do Not Disturb and manufacturer battery settings can also affect sound and delivery.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun CurfewAccountScreen(
    enrolled: Boolean,
    signedIn: Boolean,
    recoveryState: AccountRecoveryState?,
    onOpenAccount: () -> Unit,
    onEnrollAccount: () -> Unit,
    onRestoreRecoveryKey: (String) -> Unit,
    onAcknowledgeRecoveryKey: () -> Unit,
    initialRecoveryKey: String = "",
) {
    var recoveryKey by rememberSaveable { mutableStateOf(initialRecoveryKey) }
    TaskContent {
        Text(when {
            enrolled -> "This device is connected to your account."
            signedIn -> "This device is not connected yet."
            else -> "Connect your devices with a Curfew account. Alarms work without an account."
        }, style = MaterialTheme.typography.bodyLarge)
        when (recoveryState) {
            AccountRecoveryState.EnterRecoveryKey -> {
                OutlinedTextField(
                    value = recoveryKey, onValueChange = { recoveryKey = it.trim() },
                    label = { Text("Recovery Key") },
                    supportingText = { Text("Use the key you saved when you connected your first device.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                Button(onClick = { onRestoreRecoveryKey(recoveryKey) }, enabled = recoveryKey.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text("Restore settings")
                }
            }
            is AccountRecoveryState.SaveRecoveryKey -> {
                Text("Save your Recovery Key", style = MaterialTheme.typography.titleLarge)
                Text("Keep this key outside Curfew. You need it to restore your encrypted settings. Account recovery codes cannot replace it.")
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    SelectionContainer {
                        Text(recoveryState.encodedRecoveryKey, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.padding(20.dp))
                    }
                }
                Button(onClick = onAcknowledgeRecoveryKey, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("I saved my Recovery Key") }
            }
            AccountRecoveryState.Ready, null -> {
                Button(
                    onClick = if (!signedIn) onEnrollAccount else onOpenAccount,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text(if (!signedIn) "Sign in" else if (!enrolled) "Continue setup" else "Manage account") }
            }
        }
        if (!signedIn) TextButton(onClick = onOpenAccount) { Text("Open account website") }
    }
}

@Composable
internal fun AlarmSoundTestContent(
    state: studio.hypertext.curfew.readiness.AlarmSoundCheckState,
    onPlay: () -> Unit, onConfirm: () -> Unit, onStop: () -> Unit,
) {
    val playing = state == studio.hypertext.curfew.readiness.AlarmSoundCheckState.PLAYING
    val heard = state == studio.hypertext.curfew.readiness.AlarmSoundCheckState.AWAITING_CONFIRMATION || state == studio.hypertext.curfew.readiness.AlarmSoundCheckState.SAVE_FAILED
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(painterResource(R.drawable.ic_alarm_rounded), null, Modifier.size(48.dp))
            Text(when (state) {
                studio.hypertext.curfew.readiness.AlarmSoundCheckState.PLAYING -> "Listen for your alarm"
                studio.hypertext.curfew.readiness.AlarmSoundCheckState.AWAITING_CONFIRMATION -> "Did you hear it?"
                studio.hypertext.curfew.readiness.AlarmSoundCheckState.FAILED -> "No sound played"
                studio.hypertext.curfew.readiness.AlarmSoundCheckState.SAVE_FAILED -> "Your confirmation wasn’t saved"
                studio.hypertext.curfew.readiness.AlarmSoundCheckState.SAVING -> "Saving your sound check…"
                else -> "Hear what will wake you up"
            }, style = MaterialTheme.typography.headlineSmall)
            if (playing || state == studio.hypertext.curfew.readiness.AlarmSoundCheckState.SAVING) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state == studio.hypertext.curfew.readiness.AlarmSoundCheckState.FAILED) Text("Check your phone’s alarm volume, then play it again.")
            if (state == studio.hypertext.curfew.readiness.AlarmSoundCheckState.SAVE_FAILED) Text("Try confirming again. You don’t need to replay the sound.")
            if (heard) Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("I heard it") }
            if (heard) TextButton(onClick = onStop) { Text("Close preview") }
            if (state != studio.hypertext.curfew.readiness.AlarmSoundCheckState.SAVING) {
                if (playing) OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Stop preview") }
                else if (heard) OutlinedButton(onClick = onPlay, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Play again") }
                else Button(onClick = onPlay, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Play alarm preview") }
            }
        }
    }
}
