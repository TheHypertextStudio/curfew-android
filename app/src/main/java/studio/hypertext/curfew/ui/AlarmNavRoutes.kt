package studio.hypertext.curfew.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
internal fun DailyRoute(
    appState: CurfewAppState,
    state: CurfewUiState,
    actions: CurfewUiActions,
) {
    SunriseDailyScreen(
        state.sunriseAlarm,
        state.alarmConditionLabel ?: state.conditionLabel,
        state.conditionVerified && state.sunriseAlarm?.schedule?.callbackId == state.callbackId,
        state.canArm && (state.sunriseAlarm == null || state.sunriseAlarm.schedule.callbackId == state.callbackId),
        state.alarmBusy,
        state.alarmError,
        onEdit = {
            appState.setupMode = state.sunriseAlarm == null
            appState.navigate(CurfewDestination.Edit)
        },
        onCheckIn = { appState.navigate(CurfewDestination.CheckIn) },
        onSetup = {
            val isSetupMode = state.sunriseAlarm == null
            appState.setupMode = isSetupMode
            if (isSetupMode) {
                if (appState.draft != null) appState.navigate(CurfewDestination.Edit)
                appState.nextSetupTask(state)
            } else {
                appState.navigate(
                    if (state.sunriseAlarm.schedule.callbackId != state.callbackId) {
                        CurfewDestination.Edit
                    } else if (state.readiness.canArm) {
                        CurfewDestination.CheckIn
                    } else {
                        CurfewDestination.Readiness
                    },
                )
            }
        },
        onToggle = actions.onToggleAlarm,
    )
}

@Composable
internal fun EditRoute(
    appState: CurfewAppState,
    state: CurfewUiState,
    actions: CurfewUiActions,
) {
    SunriseScheduleEditor(
        state.sunriseAlarm,
        state.callbackId,
        state.canArm,
        state.alarmBusy || appState.saving || appState.preparing,
        state.alarmError ?: appState.setupError,
        onSave = {
            appState.saving = true
            actions.onSaveAlarm(it)
        },
        onFinishSetup = {
            appState.navigate(if (state.readiness.canArm) CurfewDestination.CheckIn else CurfewDestination.Readiness)
        },
        setupMode = appState.setupMode && state.sunriseAlarm == null,
        initialDraft = appState.draft,
        onPrepare = { prepared ->
            appState.prepareDraft(prepared, actions, state)
        },
    )
}

@Composable
internal fun ReadinessRoute(
    appState: CurfewAppState,
    state: CurfewUiState,
    actions: CurfewUiActions,
) {
    LaunchedEffect(Unit) { actions.onEnterAlarmReadiness() }
    AlarmReadinessScreen(
        state.readiness,
        actions.onGrantExactAlarm,
        actions.onGrantNotifications,
        actions.onRepairAlarmChannel,
        actions.onPlayTestSound,
        actions.onAcknowledgeLimitations,
        actions.onRaiseAlarmVolume,
        onDone = {
            if (appState.setupMode) {
                appState.navState.backStack.remove(CurfewDestination.Readiness)
                appState.nextSetupTask(state)
            } else {
                appState.back(state)
            }
        },
        onGrantFullScreen = actions.onGrantFullScreen,
        inSetup = appState.setupMode,
        soundState = state.soundCheckState,
        onConfirmSound = actions.onConfirmSound,
        onCancelSound = actions.onCancelSound,
    )
}

@Composable
internal fun ReviewRoute(
    appState: CurfewAppState,
    state: CurfewUiState,
    actions: CurfewUiActions,
) {
    val prepared = appState.draft
    if (prepared == null) {
        appState.navigate(CurfewDestination.Edit)
    } else {
        AlarmSetupReview(
            prepared,
            state.conditionLabel ?: "Check-in",
            state.alarmBusy || appState.saving,
            state.alarmError ?: appState.setupError,
            onEdit = { appState.navigate(CurfewDestination.Edit) },
        ) {
            if (!state.canArm) {
                appState.nextSetupTask(state)
            } else {
                val schedule = prepared.toSchedule(requireNotNull(state.callbackId))
                if (schedule.nextOccurrence(java.time.Instant.now(), java.time.ZoneId.systemDefault()) == null) {
                    appState.setupError = "Choose a future wake time."
                } else {
                    appState.setupError = null
                    appState.saving = true
                    actions.onSaveAlarm(schedule)
                }
            }
        }
    }
}
