package studio.hypertext.curfew.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import studio.hypertext.curfew.R
import studio.hypertext.curfew.account.AccountRecoveryState
import studio.hypertext.curfew.readiness.AlarmReadiness
import studio.hypertext.curfew.readiness.ReadinessAction
import studio.hypertext.curfew.readiness.ReadinessDisclosure

@Composable
internal fun CurfewSettingsScreen(
    conditionLabel: String?, conditionVerified: Boolean, readinessComplete: Boolean, signedIn: Boolean,
    onCheckIn: () -> Unit, onReadiness: () -> Unit, onAccount: () -> Unit,
) {
    TaskContent {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column {
                SettingRow("Check-in", conditionLabel?.let { if (conditionVerified) it else "$it · Not tested" } ?: "Not connected", R.drawable.ic_link_rounded, onCheckIn)
                SettingRow("Alarm access and sound", if (readinessComplete) "Ready" else "Needs attention", R.drawable.ic_lock_rounded, onReadiness)
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
) {
    var showLimits by rememberSaveable { mutableStateOf(false) }
    val actions = listOf(
        ReadinessAction.GRANT_EXACT_ALARM_ACCESS to "Exact alarms",
        ReadinessAction.GRANT_NOTIFICATIONS to "Notifications",
        ReadinessAction.REPAIR_ALARM_CHANNEL to "Alarm sound",
        ReadinessAction.RAISE_ALARM_VOLUME to "Alarm volume",
        ReadinessAction.PLAY_TEST_SOUND to "Sound test",
        ReadinessAction.ACKNOWLEDGE_LIMITATIONS to "Device limits",
    )
    val next = actions.firstOrNull { it.first in readiness.requiredActions }?.first
    TaskContent {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            actions.forEach { (action, title) ->
                ListItem(
                    headlineContent = { Text(title) },
                    supportingContent = { Text(if (action in readiness.requiredActions) "Needs attention" else "Complete") },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        }
        if (ReadinessDisclosure.FULL_SCREEN_FALLBACK in readiness.disclosures) {
            Text("Full-screen access is off. Sunrise will use a lock-screen notification.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = { showLimits = !showLimits }) { Text("Android limits") }
        if (showLimits || next == ReadinessAction.ACKNOWLEDGE_LIMITATIONS) {
            Text("Powering off, force-stopping, or uninstalling Curfew can prevent the alarm. Do Not Disturb and manufacturer battery settings can also affect sound and delivery.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val (label, callback) = when (next) {
            ReadinessAction.GRANT_EXACT_ALARM_ACCESS -> "Allow exact alarms" to onGrantExactAlarm
            ReadinessAction.GRANT_NOTIFICATIONS -> "Allow notifications" to onGrantNotifications
            ReadinessAction.REPAIR_ALARM_CHANNEL -> "Check alarm sound" to onRepairAlarmChannel
            ReadinessAction.RAISE_ALARM_VOLUME -> "Turn up alarm volume" to onRaiseAlarmVolume
            ReadinessAction.PLAY_TEST_SOUND -> "Test alarm sound" to onPlayTestSound
            ReadinessAction.ACKNOWLEDGE_LIMITATIONS -> "I understand" to onAcknowledgeLimitations
            null -> "Done" to onDone
        }
        Button(onClick = callback, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(label) }
        if (ReadinessAction.PLAY_TEST_SOUND !in readiness.requiredActions) {
            TextButton(onClick = onPlayTestSound, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Test alarm sound") }
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
            else -> "Connect your devices with a Curfew account. Sunrise works without an account."
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
