package studio.hypertext.curfew.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import studio.hypertext.curfew.alarm.AlarmSetupDraft

/**
 * State holder for top-level Curfew app navigation, setup coordination, and transient form state.
 * Follows Google's Android architecture guidance on Compose state holders.
 */
@Stable
internal class CurfewAppState(
    val navState: CurfewNavigationState,
    val coroutineScope: CoroutineScope,
    initialSetupMode: Boolean,
    initialDraft: AlarmSetupDraft?,
    val checkInState: CheckInState,
    val placeCheckInContent: (@Composable (() -> Unit, () -> Unit, (() -> Unit)?) -> Unit)? = null,
) {
    var setupMode by mutableStateOf(initialSetupMode)
    var draft by mutableStateOf(initialDraft)
    var saving by mutableStateOf(false)
    var preparing by mutableStateOf(false)
    var setupError by mutableStateOf<String?>(null)
    var choiceError by mutableStateOf<String?>(null)

    val currentDestination: CurfewDestination
        get() = navState.currentDestination

    val backStack: SnapshotStateList<CurfewDestination>
        get() = navState.backStack

    fun navigate(destination: CurfewDestination) {
        navState.navigate(destination)
    }

    fun back(state: CurfewUiState) {
        if (preparing || saving || state.alarmBusy) return
        if (currentDestination == CurfewDestination.CheckIn && checkInState.editing) {
            checkInState.editing = false
            return
        }
        if (currentDestination == CurfewDestination.CheckIn && checkInState.advanced && placeCheckInContent != null) {
            checkInState.advanced = false
            checkInState.preferCode = true
            return
        }
        navState.popBack()
    }

    fun navigateToRoot() {
        navState.navigateToRoot()
    }

    fun nextSetupTask(state: CurfewUiState) {
        navigate(
            when {
                draft == null -> CurfewDestination.Edit
                !state.conditionVerified || state.callbackId == null || state.conditionLabel == null -> CurfewDestination.CheckIn
                !state.readiness.canArm -> CurfewDestination.Readiness
                else -> CurfewDestination.Review
            },
        )
    }

    fun prepareDraft(
        prepared: AlarmSetupDraft,
        actions: CurfewUiActions,
        state: CurfewUiState,
    ) {
        coroutineScope.launch {
            preparing = true
            setupError = null
            try {
                actions.onPrepareDraft(prepared)
                draft = prepared
                nextSetupTask(state)
            } catch (_: java.io.IOException) {
                setupError = "Couldn’t save your wake time. Try again."
            } finally {
                preparing = false
            }
        }
    }

    fun deferSetup(actions: CurfewUiActions) {
        if (preparing) return
        preparing = true
        choiceError = null
        coroutineScope.launch {
            try {
                actions.onWelcomeChoice(false)
                setupMode = false
                navigateToRoot()
            } catch (_: java.io.IOException) {
                choiceError = "Couldn’t save your wake time. Try again."
            } finally {
                preparing = false
            }
        }
    }
}

/**
 * Remember factory creating and preserving a [CurfewAppState] instance across recompositions.
 */
@Composable
internal fun rememberCurfewAppState(
    state: CurfewUiState,
    navigationState: CurfewNavigationState? = null,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    placeCheckInContent: (@Composable (() -> Unit, () -> Unit, (() -> Unit)?) -> Unit)? = null,
): CurfewAppState {
    val initialDestination = when {
        state.showWelcome -> CurfewDestination.Welcome
        state.startSetup && state.setupDraft != null -> {
            if (!state.conditionVerified || state.callbackId == null || state.conditionLabel == null) {
                CurfewDestination.CheckIn
            } else if (!state.readiness.canArm) {
                CurfewDestination.Readiness
            } else {
                CurfewDestination.Review
            }
        }
        state.startSetup -> CurfewDestination.Edit
        else -> CurfewDestination.Daily
    }

    val navState = navigationState ?: rememberCurfewNavigationState(
        initialDestination = initialDestination,
        startSetup = state.startSetup,
        hasDraft = state.setupDraft != null,
    )

    val setupMode = rememberSaveable { mutableStateOf(state.startSetup || state.showWelcome) }
    val draft = rememberSaveable(stateSaver = AlarmSetupDraftSaver) { mutableStateOf(state.setupDraft) }
    val checkInState = rememberCheckInState()

    return remember(navState, coroutineScope) {
        CurfewAppState(
            navState = navState,
            coroutineScope = coroutineScope,
            initialSetupMode = setupMode.value,
            initialDraft = draft.value,
            checkInState = checkInState,
            placeCheckInContent = placeCheckInContent,
        )
    }
}
