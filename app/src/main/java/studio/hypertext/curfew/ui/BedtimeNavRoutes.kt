package studio.hypertext.curfew.ui

import androidx.compose.runtime.Composable

@Composable
internal fun BedtimeRoute(
    state: CurfewUiState,
    actions: CurfewUiActions,
) {
    BedtimeProtectionScreen(
        isAccessibilityReady = state.isAccessibilityReady,
        activeSession = state.enforcementSession,
        chosenApps = state.enforcementChosenApps,
        onToggleChosenApp = actions.onToggleChosenApp,
        onOpenAccessibilitySettings = actions.onOpenAccessibilitySettings,
        onStartSession = actions.onStartEnforcementSession,
        onEmergencyStop = actions.onEmergencyStopEnforcement,
        interceptedPackage = state.interceptedPackage,
        onDismissInterceptionBanner = actions.onDismissInterceptionBanner,
    )
}
