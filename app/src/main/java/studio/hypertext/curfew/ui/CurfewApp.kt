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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    onEnrollAccount: () -> Unit,
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
    val ringingSnap = state.ringingSnapshot
    if (state.isAlarmRinging && ringingSnap != null) {
        key(ringingSnap.state.campaignId) {
            AlarmRingingHost(
                snapshot = ringingSnap,
                ringingCondition = state.ringingCondition,
            )
        }
    } else {
        CurfewApp(
            state = state,
            actions = actions,
            modifier = modifier,
            placeCheckInContent = placeCheckInContent,
        )
    }
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
    val appState = rememberCurfewAppState(
        state = state,
        navigationState = navigationState,
        placeCheckInContent = placeCheckInContent,
    )

    LaunchedEffect(state.accountRecoveryState) {
        if (state.accountRecoveryState is AccountRecoveryState.EnterRecoveryKey ||
            state.accountRecoveryState is AccountRecoveryState.SaveRecoveryKey
        ) {
            appState.navigate(CurfewDestination.Account)
        }
    }

    LaunchedEffect(state.interceptedPackage) {
        if (state.interceptedPackage != null) {
            appState.navigate(CurfewDestination.Enforcement)
        }
    }

    LaunchedEffect(state.alarmBusy, state.alarmError, appState.saving, state.sunriseAlarm, appState.setupMode, appState.draft?.id) {
        val activated = appState.setupMode && state.sunriseAlarm?.schedule?.let { it.id == appState.draft?.id && it.enabled } == true
        if (!state.alarmBusy && activated) {
            appState.saving = false
            actions.onSetupActivated()
            appState.setupMode = false
            appState.draft = null
            appState.navigateToRoot()
        } else if (appState.saving && !state.alarmBusy) {
            if (state.alarmError != null) {
                appState.saving = false
            } else if (!appState.setupMode) {
                appState.saving = false
                appState.draft = null
            }
        }
    }

    // Responsive window layout detection
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val useNavRail = widthDp >= 600 && heightDp >= 480
    val useLandscapeNavBar = isLandscape && heightDp < 480 && widthDp >= 600

    val density = LocalDensity.current
    val imeHeight = WindowInsets.ime.getBottom(density)
    val availableHeight = with(density) { (LocalWindowInfo.current.containerSize.height - imeHeight).toDp() }
    val compactKeyboard = appState.currentDestination == CurfewDestination.CheckIn && imeHeight > 0 && availableHeight < 200.dp

    val topBarComposable: @Composable () -> Unit = {
        if (!compactKeyboard) {
            CurfewTopBar(
                currentDestination = appState.currentDestination,
                onBack = { appState.back(state) },
                onOpenSettings = {
                    appState.setupMode = false
                    appState.navigate(CurfewDestination.Settings)
                },
                showSettingsAction = !useNavRail && !useLandscapeNavBar,
            )
        }
    }

    if (useNavRail) {
        Row(modifier = modifier.fillMaxSize()) {
            if (appState.currentDestination != CurfewDestination.Welcome) {
                CurfewNavigationRail(
                    currentDestination = appState.currentDestination,
                    onNavigate = { appState.navigate(it) },
                    onEditAlarm = {
                        appState.setupMode = state.sunriseAlarm == null
                        appState.navigate(CurfewDestination.Edit)
                    },
                )
            }
            Scaffold(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentWindowInsets = if (appState.currentDestination == CurfewDestination.Welcome) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
                topBar = topBarComposable,
            ) { padding ->
                CurfewNavGraph(
                    appState = appState,
                    state = state,
                    actions = actions,
                    compactKeyboard = compactKeyboard,
                    taskTopBar = topBarComposable,
                    modifier = Modifier
                        .padding(padding)
                        .consumeWindowInsets(padding),
                )
            }
        }
    } else {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            contentWindowInsets = if (appState.currentDestination == CurfewDestination.Welcome) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
            topBar = topBarComposable,
            bottomBar = {
                if (useLandscapeNavBar && appState.currentDestination != CurfewDestination.Welcome && !compactKeyboard) {
                    CurfewNavigationBar(
                        currentDestination = appState.currentDestination,
                        onNavigate = { appState.navigate(it) },
                    )
                }
            },
        ) { padding ->
            CurfewNavGraph(
                appState = appState,
                state = state,
                actions = actions,
                compactKeyboard = compactKeyboard,
                taskTopBar = topBarComposable,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
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
