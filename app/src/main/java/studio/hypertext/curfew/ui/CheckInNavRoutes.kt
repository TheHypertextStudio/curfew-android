package studio.hypertext.curfew.ui

import androidx.compose.runtime.Composable

@Composable
internal fun CheckInRoute(
    appState: CurfewAppState,
    state: CurfewUiState,
    actions: CurfewUiActions,
    inlineHeader: @Composable (() -> Unit)? = null,
) {
    CheckInScreen(
        state.callbackId,
        state.conditionLabel,
        state.conditionVerified,
        state.conditionBusy,
        state.conditionMessage,
        actions.onSaveCallback,
        actions.onExportCondition,
        actions.onVerifyCondition,
        onDone = {
            if (appState.setupMode) appState.nextSetupTask(state) else appState.back(state)
        },
        inSetup = appState.setupMode,
        state = appState.checkInState,
        inlineHeader = inlineHeader,
        onDefer = { appState.deferSetup(actions) },
        deferBusy = appState.preparing,
        deferError = appState.choiceError,
        setupFileSaved = state.conditionSetupFileSaved,
        placeCodeSelected = state.placeCodeSelected,
        consumerContent = appState.placeCheckInContent,
        onUseForAlarm = if (state.sunriseAlarm != null &&
            state.sunriseAlarm.schedule.callbackId != state.callbackId &&
            state.conditionVerified
        ) {
            { appState.navigate(CurfewDestination.Edit) }
        } else {
            null
        },
    )
}
