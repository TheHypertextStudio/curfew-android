package studio.hypertext.curfew.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.launch
import studio.hypertext.curfew.R
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
 * Core Composable structured with clear UI state and actions.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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

    val title = when (navState.currentDestination) {
        CurfewDestination.Edit -> "Wake time"
        CurfewDestination.Readiness -> "Alarm setup"
        CurfewDestination.CheckIn -> "Check-in"
        CurfewDestination.Settings -> "Settings"
        CurfewDestination.Account -> "Account"
        CurfewDestination.Review -> "Your alarm"
        CurfewDestination.Enforcement -> "Bedtime & focus"
        else -> "Curfew"
    }

    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val density = LocalDensity.current
    val imeHeight = WindowInsets.ime.getBottom(density)
    val availableHeight = with(density) { (LocalWindowInfo.current.containerSize.height - imeHeight).toDp() }
    val compactKeyboard = navState.currentDestination == CurfewDestination.CheckIn && imeHeight > 0 && availableHeight < 200.dp

    val taskTopBar: @Composable () -> Unit = {
        if (navState.currentDestination != CurfewDestination.Welcome) {
            TopAppBar(
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                title = {
                    if (navState.currentDestination == CurfewDestination.Daily) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CurfewHorizonMark()
                            Text(
                                title,
                                style = MaterialTheme.typography.headlineSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Text(
                            title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                },
                navigationIcon = {
                    if (navState.currentDestination != CurfewDestination.Daily) {
                        IconButton(onClick = { back() }) {
                            Icon(painterResource(R.drawable.ic_arrow_back_rounded), "Back")
                        }
                    }
                },
                actions = {
                    if (navState.currentDestination == CurfewDestination.Daily) {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                            tooltip = {
                                PlainTooltip(
                                    Modifier.semantics {
                                        liveRegion = LiveRegionMode.Assertive
                                        paneTitle = "Settings"
                                    },
                                ) { Text("Settings") }
                            },
                            state = rememberTooltipState(),
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    setupMode = false
                                    open(CurfewDestination.Settings)
                                },
                                shapes = IconButtonDefaults.shapes(),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                                modifier = Modifier.padding(end = 8.dp),
                            ) {
                                Icon(painterResource(R.drawable.ic_settings_rounded), "Settings")
                            }
                        }
                    }
                },
            )
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = if (navState.currentDestination == CurfewDestination.Welcome) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
        topBar = { if (!compactKeyboard) taskTopBar() },
    ) { padding ->
        SharedTransitionLayout {
            val sharedScope = this
            NavDisplay(
                backStack = navState.backStack,
                transitionSpec = {
                    (slideInHorizontally(spatial) { it / 8 } + fadeIn(effects)) togetherWith
                        (slideOutHorizontally(spatial) { -it / 8 } + fadeOut(effects))
                },
                popTransitionSpec = {
                    (slideInHorizontally(spatial) { -it / 8 } + fadeIn(effects)) togetherWith
                        (slideOutHorizontally(spatial) { it / 8 } + fadeOut(effects))
                },
                onBack = { back() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding),
                entryProvider = { destination ->
                    NavEntry(destination) {
                        CompositionLocalProvider(
                            LocalCurfewMotion provides CurfewMotionScope(sharedScope, LocalNavAnimatedContentScope.current),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .semantics {
                                        paneTitle = when (destination) {
                                            CurfewDestination.Daily -> "Alarm"
                                            CurfewDestination.Readiness -> "Alarm setup"
                                            CurfewDestination.Edit -> "Wake time"
                                            CurfewDestination.CheckIn -> "Check-in"
                                            CurfewDestination.Review -> "Your alarm"
                                            CurfewDestination.Settings -> "Settings"
                                            CurfewDestination.Account -> "Account"
                                            CurfewDestination.Enforcement -> "Bedtime & focus"
                                            CurfewDestination.Welcome -> "Welcome to Curfew"
                                        }
                                    },
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                Box(
                                    if (destination == CurfewDestination.Welcome) {
                                        Modifier.fillMaxSize()
                                    } else if (destination == CurfewDestination.Daily) {
                                        Modifier.widthIn(max = 420.dp).fillMaxSize()
                                    } else {
                                        Modifier.fillMaxSize()
                                    },
                                ) {
                                    if (destination != CurfewDestination.Readiness) {
                                        AlarmSoundCheckDialog(
                                            state.soundCheckState,
                                            actions.onConfirmSound,
                                            actions.onPlayTestSound,
                                            actions.onCancelSound,
                                        )
                                    }
                                    when (destination) {
                                        CurfewDestination.Welcome -> CurfewWelcomeScreen(
                                            onContinue = {
                                                setupMode = true
                                                open(CurfewDestination.Edit)
                                            },
                                        )

                                        CurfewDestination.Edit -> SunriseScheduleEditor(
                                            state.sunriseAlarm,
                                            state.callbackId,
                                            state.canArm,
                                            state.alarmBusy || saving || preparing,
                                            state.alarmError ?: setupError,
                                            onSave = {
                                                saving = true
                                                actions.onSaveAlarm(it)
                                            },
                                            onFinishSetup = {
                                                open(if (state.readiness.canArm) CurfewDestination.CheckIn else CurfewDestination.Readiness)
                                            },
                                            setupMode = setupMode && state.sunriseAlarm == null,
                                            initialDraft = draft,
                                            onPrepare = { prepared ->
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
                                        )

                                        CurfewDestination.Readiness -> AlarmReadinessScreen(
                                            state.readiness,
                                            actions.onGrantExactAlarm,
                                            actions.onGrantNotifications,
                                            actions.onRepairAlarmChannel,
                                            actions.onPlayTestSound,
                                            actions.onAcknowledgeLimitations,
                                            actions.onRaiseAlarmVolume,
                                            onDone = {
                                                if (setupMode) {
                                                    navState.backStack.remove(CurfewDestination.Readiness)
                                                    nextSetupTask()
                                                } else {
                                                    back()
                                                }
                                            },
                                            onGrantFullScreen = actions.onGrantFullScreen,
                                            inSetup = setupMode,
                                            soundState = state.soundCheckState,
                                            onConfirmSound = actions.onConfirmSound,
                                            onCancelSound = actions.onCancelSound,
                                        )

                                        CurfewDestination.CheckIn -> CheckInScreen(
                                            state.callbackId,
                                            state.conditionLabel,
                                            state.conditionVerified,
                                            state.conditionBusy,
                                            state.conditionMessage,
                                            actions.onSaveCallback,
                                            actions.onExportCondition,
                                            actions.onVerifyCondition,
                                            onDone = {
                                                if (setupMode) nextSetupTask() else back()
                                            },
                                            inSetup = setupMode,
                                            state = checkInState,
                                            inlineHeader = if (compactKeyboard) taskTopBar else null,
                                            onDefer = { deferSetup() },
                                            deferBusy = preparing,
                                            deferError = choiceError,
                                            setupFileSaved = state.conditionSetupFileSaved,
                                            placeCodeSelected = state.placeCodeSelected,
                                            consumerContent = placeCheckInContent,
                                            onUseForAlarm = if (state.sunriseAlarm != null &&
                                                state.sunriseAlarm.schedule.callbackId != state.callbackId &&
                                                state.conditionVerified
                                            ) {
                                                { open(CurfewDestination.Edit) }
                                            } else {
                                                null
                                            },
                                        )

                                        CurfewDestination.Review -> draft?.let { prepared ->
                                            AlarmSetupReview(
                                                prepared,
                                                state.conditionLabel ?: "Check-in",
                                                state.alarmBusy || saving,
                                                state.alarmError ?: setupError,
                                                onEdit = { open(CurfewDestination.Edit) },
                                            ) {
                                                if (!state.canArm) {
                                                    nextSetupTask()
                                                } else {
                                                    val schedule = prepared.toSchedule(requireNotNull(state.callbackId))
                                                    if (schedule.nextOccurrence(java.time.Instant.now(), java.time.ZoneId.systemDefault()) == null) {
                                                        setupError = "Choose a future wake time."
                                                    } else {
                                                        setupError = null
                                                        saving = true
                                                        actions.onSaveAlarm(schedule)
                                                    }
                                                }
                                            }
                                        }

                                        CurfewDestination.Settings -> CurfewSettingsScreen(
                                            state.conditionLabel,
                                            state.conditionVerified,
                                            state.readiness.canArm,
                                            state.accountSignedIn,
                                            enforcementSummary = if (state.enforcementSession?.state == studio.hypertext.curfew.enforcement.EnforcementSessionState.ACTIVE) {
                                                "Active session"
                                            } else if (state.isAccessibilityReady) {
                                                "Ready"
                                            } else {
                                                "Needs attention"
                                            },
                                            onCheckIn = { open(CurfewDestination.CheckIn) },
                                            onReadiness = { open(CurfewDestination.Readiness) },
                                            onAccount = { open(CurfewDestination.Account) },
                                            onBedtimeProtection = { open(CurfewDestination.Enforcement) },
                                        )

                                        CurfewDestination.Enforcement -> BedtimeProtectionScreen(
                                            isAccessibilityReady = state.isAccessibilityReady,
                                            activeSession = state.enforcementSession,
                                            chosenApps = state.enforcementChosenApps,
                                            onToggleChosenApp = actions.onToggleChosenApp,
                                            onOpenAccessibilitySettings = actions.onOpenAccessibilitySettings,
                                            onStartSession = actions.onStartEnforcementSession,
                                            onEmergencyStop = actions.onEmergencyStopEnforcement,
                                            interceptedPackage = state.interceptedPackage,
                                            onDismissInterceptionBanner = actions.onDismissInterceptionBanner,
                                        )

                                        CurfewDestination.Account -> CurfewAccountScreen(
                                            state.accountEnrolled,
                                            state.accountSignedIn,
                                            state.accountRecoveryState,
                                            actions.onOpenAccount,
                                            actions.onEnrollAccount,
                                            actions.onRestoreRecoveryKey,
                                            actions.onAcknowledgeRecoveryKey,
                                        )

                                        CurfewDestination.Daily -> SunriseDailyScreen(
                                            state.sunriseAlarm,
                                            state.alarmConditionLabel ?: state.conditionLabel,
                                            state.conditionVerified && state.sunriseAlarm?.schedule?.callbackId == state.callbackId,
                                            state.canArm && (state.sunriseAlarm == null || state.sunriseAlarm.schedule.callbackId == state.callbackId),
                                            state.alarmBusy,
                                            state.alarmError,
                                            onEdit = {
                                                setupMode = state.sunriseAlarm == null
                                                open(CurfewDestination.Edit)
                                            },
                                            onCheckIn = { open(CurfewDestination.CheckIn) },
                                            onSetup = {
                                                setupMode = state.sunriseAlarm == null
                                                if (setupMode) {
                                                    if (draft != null) open(CurfewDestination.Edit)
                                                    nextSetupTask()
                                                } else {
                                                    open(
                                                        if (state.sunriseAlarm?.schedule?.callbackId != state.callbackId) {
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
                                }
                            }
                        }
                    }
                },
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
