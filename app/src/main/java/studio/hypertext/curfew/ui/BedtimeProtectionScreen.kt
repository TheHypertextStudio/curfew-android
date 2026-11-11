package studio.hypertext.curfew.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import studio.hypertext.curfew.enforcement.CuratedEntertainmentApps
import studio.hypertext.curfew.enforcement.EnforcementPurpose
import studio.hypertext.curfew.enforcement.EnforcementSession
import studio.hypertext.curfew.enforcement.EnforcementSessionState

/**
 * Displays opt-in protection, chosen apps, and the active session. It uses the supplied
 * controls for start and emergency stop rather than issuing platform commands itself.
 */
@Composable
internal fun BedtimeProtectionScreen(
    isAccessibilityReady: Boolean,
    activeSession: EnforcementSession?,
    chosenApps: Set<String>,
    onToggleChosenApp: (String, Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onStartSession: (EnforcementPurpose, Int) -> Unit,
    onEmergencyStop: () -> Unit,
    interceptedPackage: String? = null,
    onDismissInterceptionBanner: () -> Unit = {},
) {
    TaskContent {
        if (interceptedPackage != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "App Interrupted",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "An entertainment app ($interceptedPackage) was closed because an active protection boundary is running.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onEmergencyStop) {
                            Text("Emergency stop")
                        }
                        Button(onClick = onDismissInterceptionBanner) {
                            Text("Back to Curfew")
                        }
                    }
                }
            }
        }

        if (!isAccessibilityReady) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Accessibility service required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "Curfew can interrupt launches of apps you choose during bedtime or focus hours. To do this without administrative control, Android requires Accessibility service access.\n\nCurfew only checks the identity of apps opening while an active session is running; it never reads your screen text, captures typing, collects browsing history, or sends your activity anywhere.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onOpenAccessibilitySettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Open Accessibility Settings")
                    }
                }
            }
        }

        val isSessionActive = activeSession != null && activeSession.state == EnforcementSessionState.ACTIVE

        if (isSessionActive) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val purposeName = when (activeSession.purpose) {
                        EnforcementPurpose.BEDTIME -> "Bedtime protection"
                        EnforcementPurpose.PROTECTED_WORK -> "Protected work"
                    }
                    Text(
                        text = "$purposeName is active",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "Duration: ${activeSession.durationMinutes} minutes. Chosen entertainment apps are currently interrupted to protect your boundary.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Button(
                        onClick = onEmergencyStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Emergency stop")
                    }
                }
            }
        } else {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Start protection session",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "Start a bedtime boundary or focused work session. You can stop anytime with the emergency stop.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onStartSession(EnforcementPurpose.BEDTIME, 480) },
                            modifier = Modifier.weight(1f),
                            enabled = isAccessibilityReady,
                        ) {
                            Text("Bedtime (8h)")
                        }
                        OutlinedButton(
                            onClick = { onStartSession(EnforcementPurpose.PROTECTED_WORK, 60) },
                            modifier = Modifier.weight(1f),
                            enabled = isAccessibilityReady,
                        ) {
                            Text("Focus (1h)")
                        }
                    }
                }
            }
        }

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Chosen entertainment apps",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "Only the apps you turn on below are interrupted. Phone calls, messages, maps, authenticators, banking, and system settings always remain accessible.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                CuratedEntertainmentApps.ALL.forEach { app ->
                    val isChecked = app.packageName in chosenApps
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                onToggleChosenApp(app.packageName, checked)
                            },
                        )
                    }
                }
            }
        }
    }
}
