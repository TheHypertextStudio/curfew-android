package studio.hypertext.curfew.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import studio.hypertext.curfew.R
import studio.hypertext.curfew.alarm.AlarmSetupDraft
import studio.hypertext.curfew.alarm.AlarmSetupEntry
import studio.hypertext.curfew.alarm.AlarmSetupUiState
import studio.hypertext.curfew.alarm.SunriseSchedule

internal data class AlarmSetupActions(
    val onSaveAlarm: (SunriseSchedule) -> Unit,
    val onToggleAlarm: (Boolean) -> Unit,
    val onWelcomeChoice: suspend (Boolean) -> Unit,
    val onPrepareDraft: suspend (AlarmSetupDraft) -> Unit,
    val onSetupActivated: () -> Unit,
)

@Stable
internal class AlarmSetupJourney(
    val navigation: CurfewNavigationState,
    private val scope: CoroutineScope,
    setup: MutableState<Boolean>,
    prepared: MutableState<AlarmSetupDraft?>,
) {
    var setupMode by setup
    var draft by prepared
    var saving by mutableStateOf(false)
    var preparing by mutableStateOf(false)
        private set
    var setupError by mutableStateOf<Int?>(null)
    var choiceError by mutableStateOf<Int?>(null)
        private set
    var onCheckInBack: (() -> Boolean)? = null

    fun navigate(destination: CurfewDestination) = navigation.navigate(destination)

    fun openEditor(needsSetup: Boolean) {
        setupMode = needsSetup
        navigate(CurfewDestination.Edit)
    }

    fun back(alarmBusy: Boolean) {
        if (preparing || saving || alarmBusy) return
        if (navigation.currentDestination == CurfewDestination.CheckIn && onCheckInBack?.invoke() == true) return
        navigation.popBack()
    }

    fun nextSetupTask(state: AlarmSetupUiState) = navigate(nextAlarmSetupDestination(draft, state))

    fun prepareDraft(
        draft: AlarmSetupDraft,
        saveDraft: suspend (AlarmSetupDraft) -> Unit,
        state: AlarmSetupUiState,
    ) {
        if (preparing) return
        preparing = true
        setupError = null
        scope.launch {
            try {
                saveDraft(draft)
                this@AlarmSetupJourney.draft = draft
                nextSetupTask(state)
            } catch (_: IOException) {
                setupError = R.string.setup_save_wake_time_failed
            } finally {
                preparing = false
            }
        }
    }

    fun deferSetup(saveChoice: suspend (Boolean) -> Unit) {
        if (preparing) return
        preparing = true
        choiceError = null
        scope.launch {
            try {
                saveChoice(false)
                setupMode = false
                navigation.navigateToRoot()
            } catch (_: IOException) {
                choiceError = R.string.setup_save_wake_time_failed
            } finally {
                preparing = false
            }
        }
    }
}

private fun nextAlarmSetupDestination(draft: AlarmSetupDraft?, state: AlarmSetupUiState): CurfewDestination = when {
    draft == null -> CurfewDestination.Edit
    !state.conditionVerified || state.callbackId == null || state.conditionLabel == null -> CurfewDestination.CheckIn
    !state.readiness.canArm -> CurfewDestination.Readiness
    else -> CurfewDestination.Review
}

@Composable
internal fun rememberAlarmSetupJourney(entry: AlarmSetupEntry, state: AlarmSetupUiState): AlarmSetupJourney {
    val firstDestination = when {
        entry.showWelcome -> CurfewDestination.Welcome
        entry.startSetup -> nextAlarmSetupDestination(entry.draft, state)
        else -> CurfewDestination.Daily
    }
    val navigation = rememberCurfewNavigationState(firstDestination, entry.startSetup, entry.draft != null)
    val setup = rememberSaveable { mutableStateOf(entry.startSetup || entry.showWelcome) }
    val draft = rememberSaveable(stateSaver = AlarmSetupDraftSaver) { mutableStateOf(entry.draft) }
    val scope = rememberCoroutineScope()
    return remember(navigation, scope) { AlarmSetupJourney(navigation, scope, setup, draft) }
}
