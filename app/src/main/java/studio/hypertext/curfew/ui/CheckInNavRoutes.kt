package studio.hypertext.curfew.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import studio.hypertext.curfew.alarm.AlarmSetupUiState
import studio.hypertext.curfew.callback.CheckInViewModel
import studio.hypertext.curfew.platform.rememberVerifierSetupExport

@Composable
internal fun CheckInEntry(
    journey: AlarmSetupJourney,
    alarm: AlarmSetupUiState,
    onWelcomeChoice: suspend (Boolean) -> Unit,
    inlineHeader: (@Composable () -> Unit)?,
) {
    val model: CheckInViewModel = hiltViewModel()
    val operation by model.state.collectAsStateWithLifecycle()
    val export = rememberVerifierSetupExport(model::exportSetupFile)
    CheckInRoute(
        journey = journey,
        alarm = alarm,
        busy = operation.busy || export.pickerOpen,
        message = operation.message?.let { stringResource(it) },
        fileSaved = operation.exportedId != null && operation.exportedId == alarm.callbackId,
        onSave = model::saveCallback,
        onExport = { alarm.callbackId?.let(export.launch) },
        onVerify = { model.verifyCondition(alarm.callbackId) },
        onWelcomeChoice = onWelcomeChoice,
        inlineHeader = inlineHeader,
        placeCheckInContent = { advanced, done, useForAlarm ->
            PlaceCheckInHost(
                selectedId = alarm.callbackId,
                places = model.placeSetup,
                onAdvanced = advanced,
                onDone = done,
                onUseForAlarm = useForAlarm,
            )
        },
    )
}

@Composable
internal fun CheckInRoute(
    journey: AlarmSetupJourney,
    alarm: AlarmSetupUiState,
    busy: Boolean,
    message: String?,
    fileSaved: Boolean,
    onSave: suspend (String, String, String?) -> String,
    onExport: () -> Unit,
    onVerify: () -> Unit,
    onWelcomeChoice: suspend (Boolean) -> Unit,
    inlineHeader: (@Composable () -> Unit)?,
    placeCheckInContent: (@Composable (() -> Unit, () -> Unit, (() -> Unit)?) -> Unit)?,
) {
    val form = rememberCheckInState()
    val hasPlaceFlow = placeCheckInContent != null
    DisposableEffect(journey, form, hasPlaceFlow) {
        val interceptBack = {
            when {
                form.editing -> {
                    form.editing = false
                    true
                }
                form.advanced && hasPlaceFlow -> {
                    form.advanced = false
                    form.preferCode = true
                    true
                }
                else -> false
            }
        }
        journey.onCheckInBack = interceptBack
        onDispose {
            if (journey.onCheckInBack === interceptBack) journey.onCheckInBack = null
        }
    }
    val scheduled = alarm.sunriseAlarm
    CheckInScreen(
        callbackId = alarm.callbackId,
        savedLabel = alarm.conditionLabel,
        verified = alarm.conditionVerified,
        busy = busy,
        message = message,
        onSave = onSave,
        onExport = onExport,
        onVerify = onVerify,
        onDone = {
            if (journey.setupMode) journey.nextSetupTask(alarm) else journey.back(alarm.alarmBusy)
        },
        inSetup = journey.setupMode,
        state = form,
        inlineHeader = inlineHeader,
        onDefer = { journey.deferSetup(onWelcomeChoice) },
        deferBusy = journey.preparing,
        deferError = journey.choiceError?.let { stringResource(it) },
        setupFileSaved = fileSaved,
        placeCodeSelected = alarm.placeCodeSelected,
        consumerContent = placeCheckInContent,
        onUseForAlarm = if (scheduled != null && scheduled.schedule.callbackId != alarm.callbackId && alarm.conditionVerified) {
            { journey.navigate(CurfewDestination.Edit) }
        } else {
            null
        },
    )
}
