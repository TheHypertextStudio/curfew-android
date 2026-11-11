package studio.hypertext.curfew.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import studio.hypertext.curfew.alarm.AlarmSetupEntry
import studio.hypertext.curfew.alarm.AlarmSetupUiState

/**
 * Connects the shared alarm setup journey to the navigation shell. Alarm routes collect their
 * state where they render; account, protection, and settings are supplied by their own entry
 * functions.
 */
@Composable
internal fun CurfewJourney(
    entry: AlarmSetupEntry,
    initialAlarm: AlarmSetupUiState,
    alarm: StateFlow<AlarmSetupUiState>,
    actions: AlarmSetupActions,
    navigationEffects: @Composable (CurfewNavigationState) -> Unit = {},
    otherRoute: @Composable (CurfewDestination, AlarmSetupJourney, (@Composable () -> Unit)?) -> Unit,
) {
    val setup = rememberAlarmSetupJourney(entry, initialAlarm)
    if (setup.saving || (setup.setupMode && setup.draft != null)) {
        ObserveAlarmSave(alarm, setup, actions.onSetupActivated)
    }
    navigationEffects(setup.navigation)

    CurfewApp(
        navigation = setup.navigation,
        onBack = { setup.back(alarm.value.alarmBusy) },
        onEditAlarm = { setup.openEditor(alarm.value.sunriseAlarm == null) },
        onOpenSettings = {
            setup.setupMode = false
            setup.navigate(CurfewDestination.Settings)
        },
    ) { destination, inlineHeader ->
        when (destination) {
            CurfewDestination.Welcome -> WelcomeRoute(setup)
            CurfewDestination.Daily -> DailyRoute(setup, alarm.collectAsStateWithLifecycle().value, actions)
            CurfewDestination.Edit -> EditRoute(setup, alarm.collectAsStateWithLifecycle().value, actions)
            CurfewDestination.Review -> ReviewRoute(setup, alarm.collectAsStateWithLifecycle().value, actions)
            else -> otherRoute(destination, setup, inlineHeader)
        }
    }
}

/**
 * Watches an in-progress schedule save and exits setup only when the enabled schedule matching
 * its draft has been saved. Errors keep the current task available for retry.
 */
@Composable
private fun ObserveAlarmSave(
    alarm: StateFlow<AlarmSetupUiState>,
    setup: AlarmSetupJourney,
    onActivated: () -> Unit,
) {
    val state by alarm.collectAsStateWithLifecycle()
    val enabledSchedule = state.sunriseAlarm?.schedule?.takeIf { it.enabled }
    val activated = setup.setupMode && enabledSchedule != null && enabledSchedule.id == setup.draft?.id
    LaunchedEffect(state.alarmBusy, state.alarmError, setup.saving, activated) {
        if (!state.alarmBusy && activated) {
            setup.saving = false
            onActivated()
            setup.setupMode = false
            setup.draft = null
            setup.navigation.navigateToRoot()
        } else if (setup.saving && !state.alarmBusy) {
            if (state.alarmError != null || !setup.setupMode) setup.saving = false
            if (!setup.setupMode && state.alarmError == null) setup.draft = null
        }
    }
}
