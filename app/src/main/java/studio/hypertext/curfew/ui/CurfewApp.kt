package studio.hypertext.curfew.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import studio.hypertext.curfew.account.AccountRecoveryState
import studio.hypertext.curfew.alarm.AlarmSetupDraft
import studio.hypertext.curfew.alarm.SavedSunriseAlarm
import studio.hypertext.curfew.alarm.SunriseSchedule
import studio.hypertext.curfew.enforcement.EnforcementPurpose
import studio.hypertext.curfew.enforcement.EnforcementSession
import studio.hypertext.curfew.readiness.AlarmReadiness
import studio.hypertext.curfew.readiness.AlarmSoundCheckState

/**
 * Modern ViewModel-driven entry point for Curfew app shell.
 */
@Composable
fun CurfewApp(
    viewModel: CurfewViewModel,
    modifier: Modifier = Modifier,
    onGrantExactAlarm: () -> Unit = {},
    onGrantNotifications: () -> Unit = {},
    onGrantFullScreen: () -> Unit = {},
    onRepairAlarmChannel: () -> Unit = {},
    onRaiseAlarmVolume: () -> Unit = {},
    onOpenAccount: () -> Unit = {},
    onEnrollAccount: () -> Unit = {},
    onOpenAccessibilitySettings: () -> Unit = {},
    placeCheckInContent: (@Composable (() -> Unit, () -> Unit, (() -> Unit)?) -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        CurfewUiActions(
            onGrantExactAlarm = onGrantExactAlarm,
            onGrantNotifications = onGrantNotifications,
            onGrantFullScreen = onGrantFullScreen,
            onRepairAlarmChannel = onRepairAlarmChannel,
            onRaiseAlarmVolume = onRaiseAlarmVolume,
            onPlayTestSound = viewModel::playTestSound,
            onConfirmSound = viewModel::confirmSound,
            onCancelSound = viewModel::cancelTestSound,
            onAcknowledgeLimitations = viewModel::acknowledgeLimitations,
            onOpenAccount = onOpenAccount,
            onEnrollAccount = onEnrollAccount,
            onRestoreRecoveryKey = { key -> viewModel.restoreRecoveryKey(key) {} },
            onAcknowledgeRecoveryKey = viewModel::acknowledgeRecoveryKey,
            onSaveCallback = viewModel::saveCallback,
            onVerifyCondition = { viewModel.verifyCondition(state.callbackId) },
            onSaveAlarm = viewModel::saveAlarm,
            onToggleAlarm = viewModel::toggleAlarm,
            onWelcomeChoice = viewModel::chooseWelcome,
            onPrepareDraft = viewModel::prepareDraft,
            onSetupActivated = viewModel::clearDraft,
            onToggleChosenApp = viewModel::toggleChosenApp,
            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            onStartEnforcementSession = viewModel::startEnforcementSession,
            onEmergencyStopEnforcement = viewModel::emergencyStopEnforcement,
            onDismissInterceptionBanner = viewModel::dismissInterceptionBanner,
        )
    }
    CurfewApp(
        state = state,
        actions = actions,
        modifier = modifier,
        placeCheckInContent = placeCheckInContent,
    )
}

/**
 * Core Composable structured with clear UI state, actions, and adaptive layouts.
 */
@Composable
fun CurfewApp(
    state: CurfewUiState,
    actions: CurfewUiActions,
    modifier: Modifier = Modifier,
    navigationState: CurfewNavigationState? = null,
    placeCheckInContent: (@Composable (() -> Unit, () -> Unit, (() -> Unit)?) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var preparing by remember { mutableStateOf(false) }
    var setupMode by rememberSaveable { mutableStateOf(state.startSetup || state.showWelcome) }
    var draft by rememberSaveable(stateSaver = AlarmSetupDraftSaver) { mutableStateOf(state.setupDraft) }
    val checkInState = rememberCheckInState()
    var setupError by remember { mutableStateOf<String?>(null) }
    var choiceError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

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

    fun open(destination: CurfewDestination) {
        navState.navigate(destination)
    }

    fun back() {
        if (preparing || saving || state.alarmBusy) return
        if (navState.currentDestination == CurfewDestination.CheckIn && checkInState.editing) {
            checkInState.editing = false
            return
        }
        if (navState.currentDestination == CurfewDestination.CheckIn && checkInState.advanced && placeCheckInContent != null) {
            checkInState.advanced = false
            checkInState.preferCode = true
            return
        }
        navState.popBack()
    }

    fun daily() {
        navState.navigateToRoot()
    }

    fun nextSetupTask() {
        open(
            when {
                draft == null -> CurfewDestination.Edit
                !state.conditionVerified || state.callbackId == null || state.conditionLabel == null -> CurfewDestination.CheckIn
                !state.readiness.canArm -> CurfewDestination.Readiness
                else -> CurfewDestination.Review
            },
        )
    }

    fun deferSetup() {
        if (preparing) return
        preparing = true
        choiceError = null
        scope.launch {
            try {
                actions.onWelcomeChoice(false)
                setupMode = false
                daily()
            } catch (_: java.io.IOException) {
                choiceError = "Couldn’t save your wake time. Try again."
            } finally {
                preparing = false
            }
        }
    }

    LaunchedEffect(state.accountRecoveryState) {
        if (state.accountRecoveryState is AccountRecoveryState.EnterRecoveryKey ||
            state.accountRecoveryState is AccountRecoveryState.SaveRecoveryKey
        ) {
            open(CurfewDestination.Account)
        }
    }

    LaunchedEffect(state.interceptedPackage) {
        if (state.interceptedPackage != null) {
            open(CurfewDestination.Enforcement)
        }
    }

    LaunchedEffect(state.alarmBusy, state.alarmError, saving, state.sunriseAlarm, setupMode, draft?.id) {
        val activated = setupMode && state.sunriseAlarm?.schedule?.let { it.id == draft?.id && it.enabled } == true
        if (!state.alarmBusy && activated) {
            saving = false
            actions.onSetupActivated()
            setupMode = false
            draft = null
            daily()
        } else if (saving && !state.alarmBusy) {
            if (state.alarmError != null) {
                saving = false
            } else if (!setupMode) {
                saving = false
                draft = null
            }
        }
    }

    // Window size and orientation detection for adaptive layout
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val useNavRail = widthDp >= 600 && heightDp >= 480
    val useLandscapeNavBar = isLandscape && heightDp < 480 && widthDp >= 600

    val density = LocalDensity.current
    val imeHeight = WindowInsets.ime.getBottom(density)
    val availableHeight = with(density) { (LocalWindowInfo.current.containerSize.height - imeHeight).toDp() }
    val compactKeyboard = navState.currentDestination == CurfewDestination.CheckIn && imeHeight > 0 && availableHeight < 200.dp

    val topBarComposable: @Composable () -> Unit = {
        if (!compactKeyboard) {
            CurfewTopBar(
                currentDestination = navState.currentDestination,
                onBack = { back() },
                onOpenSettings = {
                    setupMode = false
                    open(CurfewDestination.Settings)
                },
                showSettingsAction = !useNavRail && !useLandscapeNavBar,
            )
        }
    }

    if (useNavRail) {
        Row(modifier = modifier.fillMaxSize()) {
            if (navState.currentDestination != CurfewDestination.Welcome) {
                CurfewNavigationRail(
                    currentDestination = navState.currentDestination,
                    onNavigate = { open(it) },
                    onEditAlarm = {
                        setupMode = state.sunriseAlarm == null
                        open(CurfewDestination.Edit)
                    },
                )
            }
            Scaffold(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentWindowInsets = if (navState.currentDestination == CurfewDestination.Welcome) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
                topBar = topBarComposable,
            ) { padding ->
                CurfewNavGraph(
                    navState = navState,
                    state = state,
                    actions = actions,
                    draft = draft,
                    setupMode = setupMode,
                    saving = saving,
                    preparing = preparing,
                    setupError = setupError,
                    choiceError = choiceError,
                    checkInState = checkInState,
                    compactKeyboard = compactKeyboard,
                    taskTopBar = topBarComposable,
                    onDraftPrepared = { prepared ->
                        scope.launch {
                            preparing = true
                            setupError = null
                            try {
                                actions.onPrepareDraft(prepared)
                                draft = prepared
                                nextSetupTask()
                            } catch (_: java.io.IOException) {
                                setupError = "Couldn’t save your wake time. Try again."
                            } finally {
                                preparing = false
                            }
                        }
                    },
                    onSetupError = { setupError = it },
                    onSaveStarted = { saving = true },
                    onEnterSetupMode = { setupMode = it },
                    onBack = { back() },
                    onNextSetupTask = { nextSetupTask() },
                    onDeferSetup = { deferSetup() },
                    modifier = Modifier
                        .padding(padding)
                        .consumeWindowInsets(padding),
                    placeCheckInContent = placeCheckInContent,
                )
            }
        }
    } else {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            contentWindowInsets = if (navState.currentDestination == CurfewDestination.Welcome) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
            topBar = topBarComposable,
            bottomBar = {
                if (useLandscapeNavBar && navState.currentDestination != CurfewDestination.Welcome && !compactKeyboard) {
                    CurfewNavigationBar(
                        currentDestination = navState.currentDestination,
                        onNavigate = { open(it) },
                    )
                }
            },
        ) { padding ->
            CurfewNavGraph(
                navState = navState,
                state = state,
                actions = actions,
                draft = draft,
                setupMode = setupMode,
                saving = saving,
                preparing = preparing,
                setupError = setupError,
                choiceError = choiceError,
                checkInState = checkInState,
                compactKeyboard = compactKeyboard,
                taskTopBar = topBarComposable,
                onDraftPrepared = { prepared ->
                    scope.launch {
                        preparing = true
                        setupError = null
                        try {
                            actions.onPrepareDraft(prepared)
                            draft = prepared
                            nextSetupTask()
                        } catch (_: java.io.IOException) {
                            setupError = "Couldn’t save your wake time. Try again."
                        } finally {
                            preparing = false
                        }
                    }
                },
                onSetupError = { setupError = it },
                onSaveStarted = { saving = true },
                onEnterSetupMode = { setupMode = it },
                onBack = { back() },
                onNextSetupTask = { nextSetupTask() },
                onDeferSetup = { deferSetup() },
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
                placeCheckInContent = placeCheckInContent,
            )
        }
    }
}

/**
 * Compatibility overload preserving constructor parameters for existing instrumented tests.
 */
@Composable
fun CurfewApp(
    readiness: AlarmReadiness,
    accountEnrolled: Boolean,
    accountSignedIn: Boolean,
    accountRecoveryState: AccountRecoveryState?,
    onGrantExactAlarm: () -> Unit,
    onGrantNotifications: () -> Unit,
    onRepairAlarmChannel: () -> Unit,
    onPlayTestSound: () -> Unit,
    soundCheckState: AlarmSoundCheckState = AlarmSoundCheckState.CLOSED,
    onConfirmSound: () -> Unit = {},
    onCancelSound: () -> Unit = {},
    onAcknowledgeLimitations: () -> Unit,
    onOpenAccount: () -> Unit,
    onEnrollAccount: () -> Unit,
    onRestoreRecoveryKey: (String) -> Unit,
    onAcknowledgeRecoveryKey: () -> Unit,
    onSaveCallback: suspend (String, String, String?) -> String,
    sunriseAlarm: SavedSunriseAlarm?,
    callbackId: String?,
    conditionLabel: String?,
    alarmConditionLabel: String? = conditionLabel.takeIf { callbackId == sunriseAlarm?.schedule?.callbackId },
    alarmBusy: Boolean,
    alarmError: String?,
    onSaveAlarm: (SunriseSchedule) -> Unit,
    onToggleAlarm: (Boolean) -> Unit,
    onRaiseAlarmVolume: () -> Unit = onRepairAlarmChannel,
    onGrantFullScreen: () -> Unit = {},
    showWelcome: Boolean = false,
    startSetup: Boolean = false,
    setupDraft: AlarmSetupDraft? = null,
    onWelcomeChoice: suspend (Boolean) -> Unit = {},
    onPrepareDraft: suspend (AlarmSetupDraft) -> Unit = {},
    onSetupActivated: () -> Unit = {},
    conditionVerified: Boolean = false,
    conditionBusy: Boolean = false,
    conditionMessage: String? = null,
    onExportCondition: () -> Unit = {},
    onVerifyCondition: () -> Unit = {},
    conditionSetupFileSaved: Boolean = false,
    placeCodeSelected: Boolean = false,
    placeCheckInContent: (@Composable (() -> Unit, () -> Unit, (() -> Unit)?) -> Unit)? = null,
    enforcementSession: EnforcementSession? = null,
    enforcementChosenApps: Set<String> = emptySet(),
    isAccessibilityReady: Boolean = false,
    onToggleChosenApp: (String, Boolean) -> Unit = { _, _ -> },
    onOpenAccessibilitySettings: () -> Unit = {},
    onStartEnforcementSession: (EnforcementPurpose, Int) -> Unit = { _, _ -> },
    onEmergencyStopEnforcement: () -> Unit = {},
    interceptedPackage: String? = null,
    onDismissInterceptionBanner: () -> Unit = {},
) {
    val state = CurfewUiState(
        readiness = readiness,
        sunriseAlarm = sunriseAlarm,
        callbackId = callbackId,
        conditionLabel = conditionLabel,
        alarmConditionLabel = alarmConditionLabel,
        conditionVerified = conditionVerified,
        conditionBusy = conditionBusy,
        conditionMessage = conditionMessage,
        conditionSetupFileSaved = conditionSetupFileSaved,
        placeCodeSelected = placeCodeSelected,
        alarmBusy = alarmBusy,
        alarmError = alarmError,
        accountEnrolled = accountEnrolled,
        accountSignedIn = accountSignedIn,
        accountRecoveryState = accountRecoveryState,
        soundCheckState = soundCheckState,
        showWelcome = showWelcome,
        startSetup = startSetup,
        setupDraft = setupDraft,
        enforcementSession = enforcementSession,
        enforcementChosenApps = enforcementChosenApps,
        isAccessibilityReady = isAccessibilityReady,
        interceptedPackage = interceptedPackage,
        initialDataReady = true,
    )
    val actions = CurfewUiActions(
        onGrantExactAlarm = onGrantExactAlarm,
        onGrantNotifications = onGrantNotifications,
        onGrantFullScreen = onGrantFullScreen,
        onRepairAlarmChannel = onRepairAlarmChannel,
        onRaiseAlarmVolume = onRaiseAlarmVolume,
        onPlayTestSound = onPlayTestSound,
        onConfirmSound = onConfirmSound,
        onCancelSound = onCancelSound,
        onAcknowledgeLimitations = onAcknowledgeLimitations,
        onOpenAccount = onOpenAccount,
        onEnrollAccount = onEnrollAccount,
        onRestoreRecoveryKey = onRestoreRecoveryKey,
        onAcknowledgeRecoveryKey = onAcknowledgeRecoveryKey,
        onSaveCallback = onSaveCallback,
        onExportCondition = onExportCondition,
        onVerifyCondition = onVerifyCondition,
        onSaveAlarm = onSaveAlarm,
        onToggleAlarm = onToggleAlarm,
        onWelcomeChoice = onWelcomeChoice,
        onPrepareDraft = onPrepareDraft,
        onSetupActivated = onSetupActivated,
        onToggleChosenApp = onToggleChosenApp,
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        onStartEnforcementSession = onStartEnforcementSession,
        onEmergencyStopEnforcement = onEmergencyStopEnforcement,
        onDismissInterceptionBanner = onDismissInterceptionBanner,
    )
    CurfewApp(
        state = state,
        actions = actions,
        placeCheckInContent = placeCheckInContent,
    )
}
