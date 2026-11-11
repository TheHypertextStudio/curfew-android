package studio.hypertext.curfew.ui

import androidx.compose.runtime.Composable
import studio.hypertext.curfew.enforcement.EnforcementSessionState

@Composable
internal fun SettingsRoute(
    appState: CurfewAppState,
    state: CurfewUiState,
) {
    CurfewSettingsScreen(
        state.conditionLabel,
        state.conditionVerified,
        state.readiness.canArm,
        state.accountSignedIn,
        enforcementSummary = if (state.enforcementSession?.state == EnforcementSessionState.ACTIVE) {
            "Active session"
        } else if (state.isAccessibilityReady) {
            "Ready"
        } else {
            "Needs attention"
        },
        onCheckIn = { appState.navigate(CurfewDestination.CheckIn) },
        onReadiness = { appState.navigate(CurfewDestination.Readiness) },
        onAccount = { appState.navigate(CurfewDestination.Account) },
        onBedtimeProtection = { appState.navigate(CurfewDestination.Enforcement) },
    )
}

@Composable
internal fun AccountRoute(
    state: CurfewUiState,
    actions: CurfewUiActions,
) {
    CurfewAccountScreen(
        state.accountEnrolled,
        state.accountSignedIn,
        state.accountRecoveryState,
        actions.onOpenAccount,
        actions.onEnrollAccount,
        actions.onRestoreRecoveryKey,
        actions.onAcknowledgeRecoveryKey,
    )
}
