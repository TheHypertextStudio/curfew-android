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
) {
    var showLimits by rememberSaveable { mutableStateOf(false) }
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
        ReadinessAction.PLAY_TEST_SOUND -> "Check the sound"
        ReadinessAction.ACKNOWLEDGE_LIMITATIONS -> "Keep your alarm available"
        null -> "Access and sound are ready"
    }
    val purpose = when (next) {
        ReadinessAction.GRANT_EXACT_ALARM_ACCESS -> "Allow Curfew to ring at your chosen time."
        ReadinessAction.GRANT_NOTIFICATIONS -> "Keep the alarm available outside Curfew."
        ReadinessAction.GRANT_FULL_SCREEN_ACCESS -> "Allow Curfew to appear over your lock screen."
        ReadinessAction.REPAIR_ALARM_CHANNEL -> "Allow sound for Curfew’s alarm notification."
        ReadinessAction.RAISE_ALARM_VOLUME -> "Your alarm volume is muted."
        ReadinessAction.PLAY_TEST_SOUND -> "A short alarm will play. Confirm that you hear it."
        ReadinessAction.ACKNOWLEDGE_LIMITATIONS -> "Unlock your phone after restarting so alarms can resume."
        null -> null
    }
    val (label, callback) = when (next) {
        ReadinessAction.GRANT_EXACT_ALARM_ACCESS -> "Allow exact alarms" to onGrantExactAlarm
        ReadinessAction.GRANT_NOTIFICATIONS -> "Allow notifications" to onGrantNotifications
        ReadinessAction.GRANT_FULL_SCREEN_ACCESS -> "Allow alarm screen" to onGrantFullScreen
        ReadinessAction.REPAIR_ALARM_CHANNEL -> "Check alarm sound" to onRepairAlarmChannel
        ReadinessAction.RAISE_ALARM_VOLUME -> "Turn up alarm volume" to onRaiseAlarmVolume
        ReadinessAction.PLAY_TEST_SOUND -> "Test alarm sound" to onPlayTestSound
        ReadinessAction.ACKNOWLEDGE_LIMITATIONS -> "I understand" to onAcknowledgeLimitations
        null -> "Done" to onDone
    }
    TaskContent {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(
                    painterResource(if (next == null) R.drawable.ic_check_circle_rounded else R.drawable.ic_lock_rounded),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
                Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                if (purpose != null) Text(purpose, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (next == ReadinessAction.ACKNOWLEDGE_LIMITATIONS) {
                    Text("Powering off, force-stopping, or uninstalling Curfew can prevent the alarm. Do Not Disturb and manufacturer battery settings can also affect sound and delivery.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = callback, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(label) }
            }
        }
        if (readiness.canArm) {
            TextButton(onClick = onPlayTestSound, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Test alarm sound") }
        }
        if (next != ReadinessAction.ACKNOWLEDGE_LIMITATIONS) {
            TextButton(onClick = { showLimits = !showLimits }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Android limits") }
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
) {
    var recoveryKey by rememberSaveable { mutableStateOf("") }
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
