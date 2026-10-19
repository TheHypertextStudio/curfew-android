package studio.hypertext.curfew.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import studio.hypertext.curfew.readiness.AlarmReadiness
import studio.hypertext.curfew.readiness.ReadinessAction
import studio.hypertext.curfew.readiness.ReadinessDisclosure
import studio.hypertext.curfew.account.AccountRecoveryState
import studio.hypertext.curfew.alarm.SavedSunriseAlarm
import studio.hypertext.curfew.alarm.SunriseSchedule

@Composable
fun CurfewApp(
    readiness: AlarmReadiness,
    accountEnrolled: Boolean,
    accountSignedIn: Boolean,
    accountRecoveryState: AccountRecoveryState?,
    onGrantExactAlarm: () -> Unit,
    onGrantNotifications: () -> Unit,
    onRepairAlarmChannel: () -> Unit,
    onPlayTestSound: () -> Unit,
    onAcknowledgeLimitations: () -> Unit,
    onOpenAccount: () -> Unit,
    onEnrollAccount: () -> Unit,
    onRestoreRecoveryKey: (String) -> Unit,
    onAcknowledgeRecoveryKey: () -> Unit,
    onSaveCallback: (String, String, String?) -> Unit,
    sunriseAlarm: SavedSunriseAlarm?,
    callbackId: String?,
    conditionLabel: String?,
    alarmBusy: Boolean,
    alarmError: String?,
    onSaveAlarm: (SunriseSchedule) -> Unit,
    onToggleAlarm: (Boolean) -> Unit,
    onRaiseAlarmVolume: () -> Unit = onRepairAlarmChannel,
    conditionVerified: Boolean = false,
    conditionBusy: Boolean = false,
    conditionMessage: String? = null,
    onExportCondition: () -> Unit = {},
    onVerifyCondition: () -> Unit = {},
) {
    Scaffold { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val layoutMode = CurfewLayoutMode.forWidthDp(maxWidth.value.toInt())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = if (layoutMode == CurfewLayoutMode.COMPACT) 20.dp else 32.dp,
                        vertical = 28.dp,
                    ),
            ) {
                Header(readiness.canArm)
                Spacer(Modifier.height(24.dp))
                alarmError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                }
                if (layoutMode == CurfewLayoutMode.COMPACT) {
                    SunriseAlarmCard(
                        sunriseAlarm, callbackId, conditionLabel,
                        readiness.canArm && conditionLabel != null && conditionVerified,
                        alarmBusy, onSaveAlarm, onToggleAlarm,
                    )
                    Spacer(Modifier.height(16.dp))
                    ReadinessCard(
                        readiness,
                        onGrantExactAlarm,
                        onGrantNotifications,
                        onRepairAlarmChannel,
                        onPlayTestSound,
                        onAcknowledgeLimitations,
                        onRaiseAlarmVolume,
                    )
                    Spacer(Modifier.height(16.dp))
                    WakeConditionCard(conditionLabel, conditionVerified, conditionBusy, conditionMessage, onSaveCallback, onExportCondition, onVerifyCondition)
                    Spacer(Modifier.height(16.dp))
                    AccountCard(
                        accountEnrolled,
                        accountSignedIn,
                        accountRecoveryState,
                        onOpenAccount,
                        onEnrollAccount,
                        onRestoreRecoveryKey,
                        onAcknowledgeRecoveryKey,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(Modifier.weight(1.15f)) {
                            SunriseAlarmCard(
                                sunriseAlarm, callbackId, conditionLabel,
                                readiness.canArm && conditionLabel != null && conditionVerified,
                                alarmBusy, onSaveAlarm, onToggleAlarm,
                            )
                            Spacer(Modifier.height(20.dp))
                            WakeConditionCard(conditionLabel, conditionVerified, conditionBusy, conditionMessage, onSaveCallback, onExportCondition, onVerifyCondition)
                        }
                        Column(Modifier.weight(0.85f)) {
                            ReadinessCard(
                                readiness,
                                onGrantExactAlarm,
                                onGrantNotifications,
                                onRepairAlarmChannel,
                                onPlayTestSound,
                                onAcknowledgeLimitations,
                                onRaiseAlarmVolume,
                            )
                            Spacer(Modifier.height(20.dp))
                            AccountCard(
                                accountEnrolled,
                                accountSignedIn,
                                accountRecoveryState,
                                onOpenAccount,
                                onEnrollAccount,
                                onRestoreRecoveryKey,
                                onAcknowledgeRecoveryKey,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Android can prevent ordinary dismissal, but force-stop, uninstall, power-off, permission revocation, and some manufacturer battery controls remain outside Curfew’s control.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun Header(ready: Boolean) {
    Text(
        text = "CURFEW / SUNRISE",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Start the day you planned.",
        style = MaterialTheme.typography.displaySmall,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = if (ready) {
            "Alarm permissions and sound checks are ready."
        } else {
            "Finish the sound and permission checks before turning Sunrise on."
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ReadinessCard(
    readiness: AlarmReadiness,
    onGrantExactAlarm: () -> Unit,
    onGrantNotifications: () -> Unit,
    onRepairAlarmChannel: () -> Unit,
    onPlayTestSound: () -> Unit,
    onAcknowledgeLimitations: () -> Unit,
    onRaiseAlarmVolume: () -> Unit,
) {
    CurfewCard("Alarm readiness") {
        Text(
            if (readiness.canArm) "Ready" else "${readiness.requiredActions.size} actions remain",
            style = MaterialTheme.typography.titleMedium,
            color = if (readiness.canArm) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(12.dp))
        readiness.requiredActions.forEach { action ->
            val (label, callback) = when (action) {
                ReadinessAction.GRANT_EXACT_ALARM_ACCESS -> "Allow exact alarms" to onGrantExactAlarm
                ReadinessAction.GRANT_NOTIFICATIONS -> "Allow notifications" to onGrantNotifications
                ReadinessAction.REPAIR_ALARM_CHANNEL -> "Make alarm channel audible" to onRepairAlarmChannel
                ReadinessAction.RAISE_ALARM_VOLUME -> "Turn up alarm volume" to onRaiseAlarmVolume
                ReadinessAction.PLAY_TEST_SOUND -> "Play a real test sound" to onPlayTestSound
                ReadinessAction.ACKNOWLEDGE_LIMITATIONS -> "Acknowledge device limits" to onAcknowledgeLimitations
            }
            FilledTonalButton(
                onClick = callback,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(top = 6.dp),
            ) { Text(label) }
        }
        if (ReadinessDisclosure.FULL_SCREEN_FALLBACK in readiness.disclosures) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Full-screen alarm access is off. Curfew will use a high-priority lock-screen notification.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Curfew uses alarm audio focus and your audible alarm channel. It never changes system volume. Do Not Disturb and battery behavior remain visible here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccountCard(
    accountEnrolled: Boolean,
    accountSignedIn: Boolean,
    recoveryState: AccountRecoveryState?,
    onOpenAccount: () -> Unit,
    onEnrollAccount: () -> Unit,
    onRestoreRecoveryKey: (String) -> Unit,
    onAcknowledgeRecoveryKey: () -> Unit,
) {
    var recoveryKeyInput by rememberSaveable { mutableStateOf("") }
    CurfewCard("Account sync") {
        Text(
            when {
                accountEnrolled -> "This device is enrolled for encrypted account sync."
                accountSignedIn -> "Authentication is complete. Finish device-key and Recovery Key enrollment before sync begins."
                else -> "Optional. Local alarms and callbacks work without an account."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Enrollment keeps the account root key on enrolled devices. Restoring encrypted settings also requires your separate Curfew Recovery Key.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        if (!accountSignedIn) {
            Button(
                onClick = onEnrollAccount,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) { Text("Sign in and enroll device") }
            Spacer(Modifier.height(8.dp))
        }
        when (recoveryState) {
            AccountRecoveryState.EnterRecoveryKey -> {
                OutlinedTextField(
                    value = recoveryKeyInput,
                    onValueChange = { recoveryKeyInput = it.trim() },
                    label = { Text("Curfew Recovery Key") },
                    supportingText = { Text("The key stays on this device and never goes to Curfew.") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onRestoreRecoveryKey(recoveryKeyInput) },
                    enabled = recoveryKeyInput.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) { Text("Restore encrypted account") }
                Spacer(Modifier.height(8.dp))
            }
            is AccountRecoveryState.SaveRecoveryKey -> {
                Text(
                    "Save this key outside Curfew. Better Auth backup codes cannot recover encrypted Curfew data.",
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Text(
                        recoveryState.encodedRecoveryKey,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.semantics {
                            contentDescription = "Curfew Recovery Key"
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onAcknowledgeRecoveryKey,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) { Text("I saved the Recovery Key") }
                Spacer(Modifier.height(8.dp))
            }
            AccountRecoveryState.Ready, null -> Unit
        }
        OutlinedButton(
            onClick = onOpenAccount,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(
                when {
                    accountEnrolled -> "Manage account"
                    accountSignedIn -> "Finish encryption enrollment"
                    else -> "Open Account"
                },
            )
        }
    }
}

@Composable
internal fun CurfewCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
