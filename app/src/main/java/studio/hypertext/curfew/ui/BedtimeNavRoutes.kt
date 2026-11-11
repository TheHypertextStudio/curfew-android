package studio.hypertext.curfew.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import studio.hypertext.curfew.LaunchRequest
import studio.hypertext.curfew.enforcement.BedtimeProtectionUiState
import studio.hypertext.curfew.enforcement.BedtimeProtectionViewModel
import studio.hypertext.curfew.enforcement.EnforcementPurpose
import studio.hypertext.curfew.platform.SystemSettingsLauncher

/**
 * Resolves the protection model from the current NavEntry and refreshes accessibility on
 * resume. The intercepted package is saveable within this entry and is consumed after
 * navigation.
 */
@Composable
internal fun BedtimeEntry(
    systemSettings: SystemSettingsLauncher,
    entries: StateFlow<LaunchRequest.Enforcement?>,
    onEntryConsumed: (LaunchRequest.Enforcement) -> Unit,
) {
    val model: BedtimeProtectionViewModel = hiltViewModel()
    val state by model.state.collectAsStateWithLifecycle()
    val entry by entries.collectAsStateWithLifecycle()
    var interceptedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(entry) {
        entry?.let {
            interceptedPackage = it.interceptedPackage
            onEntryConsumed(it)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refreshAccessibility() }
    BedtimeRoute(
        state = state,
        onToggleChosenApp = model::toggleChosenApp,
        onOpenAccessibilitySettings = systemSettings::openAccessibilitySettings,
        onStartSession = model::startSession,
        onEmergencyStop = model::emergencyStop,
        interceptedPackage = interceptedPackage,
        onDismissInterception = { interceptedPackage = null },
    )
}

/**
 * Renders protection state and sends user actions to the entry's callbacks. It has no service
 * or persistence dependencies.
 */
@Composable
internal fun BedtimeRoute(
    state: BedtimeProtectionUiState,
    onToggleChosenApp: (String, Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onStartSession: (EnforcementPurpose, Int) -> Unit,
    onEmergencyStop: () -> Unit,
    interceptedPackage: String?,
    onDismissInterception: () -> Unit,
) {
    BedtimeProtectionScreen(
        isAccessibilityReady = state.accessibilityEnabled,
        activeSession = state.session,
        chosenApps = state.chosenApps,
        onToggleChosenApp = onToggleChosenApp,
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        onStartSession = onStartSession,
        onEmergencyStop = onEmergencyStop,
        interceptedPackage = interceptedPackage,
        onDismissInterceptionBanner = onDismissInterception,
    )
}
