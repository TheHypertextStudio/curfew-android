package studio.hypertext.curfew.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import studio.hypertext.curfew.R
import studio.hypertext.curfew.alarm.AlarmSetupUiState
import studio.hypertext.curfew.readiness.AlarmSoundCheckState

@Composable
internal fun DailyRoute(
    appState: AlarmSetupJourney,
    state: AlarmSetupUiState,
    actions: AlarmSetupActions,
) {
    SunriseDailyScreen(
        state.sunriseAlarm,
        state.alarmConditionLabel ?: state.conditionLabel,
        state.conditionVerified && state.sunriseAlarm?.schedule?.callbackId == state.callbackId,
        state.canArm && (state.sunriseAlarm == null || state.sunriseAlarm.schedule.callbackId == state.callbackId),
        state.alarmBusy,
        alarmSaveMessage(state.alarmError),
        onEdit = {
            appState.setupMode = state.sunriseAlarm == null
            appState.navigate(CurfewDestination.Edit)
        },
        onCheckIn = { appState.navigate(CurfewDestination.CheckIn) },
        onSetup = {
            val alarm = state.sunriseAlarm
            appState.setupMode = alarm == null
            if (alarm == null) {
                if (appState.draft != null) appState.navigate(CurfewDestination.Edit)
                appState.nextSetupTask(state)
            } else {
                appState.navigate(
                    if (alarm.schedule.callbackId != state.callbackId) {
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
    appState: AlarmSetupJourney,
    state: AlarmSetupUiState,
    actions: AlarmSetupActions,
) {
    SunriseScheduleEditor(
        state.sunriseAlarm,
        state.callbackId,
        state.canArm,
        state.alarmBusy || appState.saving || appState.preparing,
        alarmSaveMessage(state.alarmError) ?: appState.setupError?.let { stringResource(it) },
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
            appState.prepareDraft(prepared, actions.onPrepareDraft, state)
        },
    )
}

internal data class ReadinessActions(
    val onEnter: () -> Unit = {},
    val onGrantExactAlarm: () -> Unit = {},
    val onGrantNotifications: () -> Unit = {},
    val onRepairAlarmChannel: () -> Unit = {},
    val onPlayTestSound: () -> Unit = {},
    val onAcknowledgeLimitations: () -> Unit = {},
    val onRaiseAlarmVolume: () -> Unit = {},
    val onGrantFullScreen: () -> Unit = {},
    val onConfirmSound: () -> Unit = {},
    val onCancelSound: () -> Unit = {},
)

@Composable
internal fun ReadinessRoute(
    appState: AlarmSetupJourney,
    state: AlarmSetupUiState,
    actions: ReadinessActions,
    soundState: AlarmSoundCheckState,
) {
    LaunchedEffect(Unit) { actions.onEnter() }
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
                appState.navigation.backStack.remove(CurfewDestination.Readiness)
                appState.nextSetupTask(state)
            } else {
                appState.back(state.alarmBusy)
            }
        },
        onGrantFullScreen = actions.onGrantFullScreen,
        inSetup = appState.setupMode,
        soundState = soundState,
        onConfirmSound = actions.onConfirmSound,
        onCancelSound = actions.onCancelSound,
    )
}

@Composable
internal fun ReviewRoute(
    appState: AlarmSetupJourney,
    state: AlarmSetupUiState,
    actions: AlarmSetupActions,
) {
    val prepared = appState.draft
    if (prepared == null) {
        LaunchedEffect(Unit) { appState.navigate(CurfewDestination.Edit) }
    } else {
        AlarmSetupReview(
            prepared,
            state.conditionLabel ?: stringResource(R.string.check_in_label),
            state.alarmBusy || appState.saving,
            alarmSaveMessage(state.alarmError) ?: appState.setupError?.let { stringResource(it) },
            onEdit = { appState.navigate(CurfewDestination.Edit) },
        ) {
            if (!state.canArm) {
                appState.nextSetupTask(state)
            } else {
                val schedule = prepared.toSchedule(requireNotNull(state.callbackId))
                if (schedule.nextOccurrence(java.time.Instant.now(), java.time.ZoneId.systemDefault()) == null) {
                    appState.setupError = R.string.setup_choose_future_time
                } else {
                    appState.setupError = null
                    appState.saving = true
                    actions.onSaveAlarm(schedule)
                }
            }
        }
    }
}
