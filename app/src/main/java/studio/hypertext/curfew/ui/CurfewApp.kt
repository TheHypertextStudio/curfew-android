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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import studio.hypertext.curfew.readiness.AlarmReadiness
import studio.hypertext.curfew.readiness.ReadinessAction
import studio.hypertext.curfew.readiness.ReadinessDisclosure

@Composable
fun CurfewApp(
    readiness: AlarmReadiness,
    accountEnrolled: Boolean,
    accountSignedIn: Boolean,
    onGrantExactAlarm: () -> Unit,
    onGrantNotifications: () -> Unit,
    onRepairAlarmChannel: () -> Unit,
    onPlayTestSound: () -> Unit,
    onAcknowledgeLimitations: () -> Unit,
    onOpenAccount: () -> Unit,
    onEnrollAccount: () -> Unit,
    onSaveCallback: (String, String, String?) -> Unit,
    onArm: (LocalTime, Int, Int, Int) -> Unit,
) {
    var wakeTimeText by rememberSaveable { mutableStateOf("07:00") }
    var maximumAttempts by rememberSaveable { mutableIntStateOf(3) }
    var ringMinutes by rememberSaveable { mutableIntStateOf(2) }
    var quietMinutes by rememberSaveable { mutableIntStateOf(5) }
    val wakeTime = runCatching {
        LocalTime.parse(wakeTimeText, DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrNull()
    val totalMinutes = maximumAttempts * ringMinutes +
        (maximumAttempts - 1).coerceAtLeast(0) * quietMinutes
    val validCampaign = wakeTime != null && totalMinutes <= 120

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
                if (layoutMode == CurfewLayoutMode.COMPACT) {
                    AlarmConfigurationCard(
                        wakeTimeText,
                        maximumAttempts,
                        ringMinutes,
                        quietMinutes,
                        totalMinutes,
                        validCampaign,
                        readiness.canArm,
                        onWakeTimeChanged = { wakeTimeText = it.take(5) },
                        onAttemptsChanged = { maximumAttempts = it.coerceIn(1, 24) },
                        onRingChanged = { ringMinutes = it.coerceIn(1, 120) },
                        onQuietChanged = { quietMinutes = it.coerceIn(0, 120) },
                        onArm = { onArm(requireNotNull(wakeTime), maximumAttempts, ringMinutes, quietMinutes) },
                    )
                    Spacer(Modifier.height(16.dp))
                    ReadinessCard(
                        readiness,
                        onGrantExactAlarm,
                        onGrantNotifications,
                        onRepairAlarmChannel,
                        onPlayTestSound,
                        onAcknowledgeLimitations,
                    )
                    Spacer(Modifier.height(16.dp))
                    CallbackCard(onSaveCallback)
                    Spacer(Modifier.height(16.dp))
                    AccountCard(accountEnrolled, accountSignedIn, onOpenAccount, onEnrollAccount)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(Modifier.weight(1.15f)) {
                            AlarmConfigurationCard(
                                wakeTimeText,
                                maximumAttempts,
                                ringMinutes,
                                quietMinutes,
                                totalMinutes,
                                validCampaign,
                                readiness.canArm,
                                onWakeTimeChanged = { wakeTimeText = it.take(5) },
                                onAttemptsChanged = { maximumAttempts = it.coerceIn(1, 24) },
                                onRingChanged = { ringMinutes = it.coerceIn(1, 120) },
                                onQuietChanged = { quietMinutes = it.coerceIn(0, 120) },
                                onArm = {
                                    onArm(requireNotNull(wakeTime), maximumAttempts, ringMinutes, quietMinutes)
                                },
                            )
                            Spacer(Modifier.height(20.dp))
                            CallbackCard(onSaveCallback)
                        }
                        Column(Modifier.weight(0.85f)) {
                            ReadinessCard(
                                readiness,
                                onGrantExactAlarm,
                                onGrantNotifications,
                                onRepairAlarmChannel,
                                onPlayTestSound,
                                onAcknowledgeLimitations,
                            )
                            Spacer(Modifier.height(20.dp))
                            AccountCard(
                                accountEnrolled,
                                accountSignedIn,
                                onOpenAccount,
                                onEnrollAccount,
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
        text = "CURFEW / WAKE",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Wake without negotiating with yourself.",
        style = MaterialTheme.typography.displaySmall,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = if (ready) {
            "This device is ready to run a finite Perpetual Alarm campaign."
        } else {
            "Finish readiness once, then Curfew can reliably hold the morning boundary."
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AlarmConfigurationCard(
    wakeTimeText: String,
    maximumAttempts: Int,
    ringMinutes: Int,
    quietMinutes: Int,
    totalMinutes: Int,
    validCampaign: Boolean,
    ready: Boolean,
    onWakeTimeChanged: (String) -> Unit,
    onAttemptsChanged: (Int) -> Unit,
    onRingChanged: (Int) -> Unit,
    onQuietChanged: (Int) -> Unit,
    onArm: () -> Unit,
) {
    CurfewCard("Next wake campaign") {
        Text(
            "Three focused attempts are the default: two minutes of alarm, then five quiet minutes between attempts.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = wakeTimeText,
            onValueChange = onWakeTimeChanged,
            label = { Text("Wake time (24-hour)") },
            singleLine = true,
            isError = !validCampaign,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        CounterRow("Attempts", maximumAttempts, 1, 24, onAttemptsChanged)
        CounterRow("Ring minutes", ringMinutes, 1, 120, onRingChanged)
        CounterRow("Quiet minutes", quietMinutes, 0, 120, onQuietChanged)
        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Deterministic deadline", style = MaterialTheme.typography.labelLarge)
            Text(
                "$totalMinutes minutes",
                color = if (totalMinutes <= 120) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onArm,
            enabled = ready && validCampaign,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) {
            Text(if (ready) "Arm wake campaign" else "Finish readiness to arm")
        }
    }
}

@Composable
private fun CounterRow(
    label: String,
    value: Int,
    minimum: Int,
    maximum: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(
            onClick = { onChange(value - 1) },
            enabled = value > minimum,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Decrease $label" },
        ) { Text("−") }
        Text(
            value.toString(),
            modifier = Modifier.width(48.dp),
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        OutlinedButton(
            onClick = { onChange(value + 1) },
            enabled = value < maximum,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Increase $label" },
        ) { Text("+") }
    }
}

@Composable
private fun ReadinessCard(
    readiness: AlarmReadiness,
    onGrantExactAlarm: () -> Unit,
    onGrantNotifications: () -> Unit,
    onRepairAlarmChannel: () -> Unit,
    onPlayTestSound: () -> Unit,
    onAcknowledgeLimitations: () -> Unit,
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
private fun CallbackCard(onSaveCallback: (String, String, String?) -> Unit) {
    var label by rememberSaveable { mutableStateOf("") }
    var endpoint by rememberSaveable { mutableStateOf("") }
    var actionUrl by rememberSaveable { mutableStateOf("") }
    var saved by rememberSaveable { mutableStateOf(false) }
    val endpointValid = endpoint.isBlank() || endpoint.startsWith("https://")
    CurfewCard("Wake condition") {
        Text(
            "Optional and generic. Curfew polls a signed HTTPS callback; transport failures stay pending.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = label,
            onValueChange = { label = it; saved = false },
            label = { Text("Display label") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it; saved = false },
            label = { Text("HTTPS callback endpoint") },
            isError = !endpointValid,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = actionUrl,
            onValueChange = { actionUrl = it; saved = false },
            label = { Text("Optional action URL") },
            supportingText = { Text("Opened as an opaque link while the campaign runs") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        if (endpoint.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    onSaveCallback(label, endpoint, actionUrl.takeIf(String::isNotBlank))
                    saved = true
                },
                enabled = label.isNotBlank() && endpointValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) { Text(if (saved) "Saved on this device" else "Save condition") }
        }
    }
}

@Composable
private fun AccountCard(
    accountEnrolled: Boolean,
    accountSignedIn: Boolean,
    onOpenAccount: () -> Unit,
    onEnrollAccount: () -> Unit,
) {
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
private fun CurfewCard(title: String, content: @Composable () -> Unit) {
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
