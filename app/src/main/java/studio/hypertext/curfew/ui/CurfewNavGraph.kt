package studio.hypertext.curfew.ui

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import studio.hypertext.curfew.alarm.AlarmSetupDraft
import studio.hypertext.curfew.enforcement.EnforcementSessionState

/**
 * Encapsulated Navigation3 NavDisplay graph dispatching to destination screens.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CurfewNavGraph(
    navState: CurfewNavigationState,
    state: CurfewUiState,
    actions: CurfewUiActions,
    draft: AlarmSetupDraft?,
    setupMode: Boolean,
    saving: Boolean,
    preparing: Boolean,
    setupError: String?,
    choiceError: String?,
    checkInState: CheckInState,
    compactKeyboard: Boolean,
    taskTopBar: @Composable () -> Unit,
    onDraftPrepared: (AlarmSetupDraft) -> Unit,
    onSetupError: (String?) -> Unit,
    onSaveStarted: () -> Unit,
    onEnterSetupMode: (Boolean) -> Unit,
    onBack: () -> Unit,
    onNextSetupTask: () -> Unit,
    onDeferSetup: () -> Unit,
    modifier: Modifier = Modifier,
    placeCheckInContent: (@Composable (() -> Unit, () -> Unit, (() -> Unit)?) -> Unit)? = null,
) {
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

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
            onBack = onBack,
            modifier = modifier.fillMaxSize(),
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
                                        onEnterSetupMode(true)
                                        navState.navigate(CurfewDestination.Edit)
                                    },
                                )

                                CurfewDestination.Edit -> SunriseScheduleEditor(
                                    state.sunriseAlarm,
                                    state.callbackId,
                                    state.canArm,
                                    state.alarmBusy || saving || preparing,
                                    state.alarmError ?: setupError,
                                    onSave = {
                                        onSaveStarted()
                                        actions.onSaveAlarm(it)
                                    },
                                    onFinishSetup = {
                                        navState.navigate(if (state.readiness.canArm) CurfewDestination.CheckIn else CurfewDestination.Readiness)
                                    },
                                    setupMode = setupMode && state.sunriseAlarm == null,
                                    initialDraft = draft,
                                    onPrepare = { prepared ->
                                        onDraftPrepared(prepared)
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
                                            onNextSetupTask()
                                        } else {
                                            onBack()
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
                                        if (setupMode) onNextSetupTask() else onBack()
                                    },
                                    inSetup = setupMode,
                                    state = checkInState,
                                    inlineHeader = if (compactKeyboard) taskTopBar else null,
                                    onDefer = onDeferSetup,
                                    deferBusy = preparing,
                                    deferError = choiceError,
                                    setupFileSaved = state.conditionSetupFileSaved,
                                    placeCodeSelected = state.placeCodeSelected,
                                    consumerContent = placeCheckInContent,
                                    onUseForAlarm = if (state.sunriseAlarm != null &&
                                        state.sunriseAlarm.schedule.callbackId != state.callbackId &&
                                        state.conditionVerified
                                    ) {
                                        { navState.navigate(CurfewDestination.Edit) }
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
                                        onEdit = { navState.navigate(CurfewDestination.Edit) },
                                    ) {
                                        if (!state.canArm) {
                                            onNextSetupTask()
                                        } else {
                                            val schedule = prepared.toSchedule(requireNotNull(state.callbackId))
                                            if (schedule.nextOccurrence(java.time.Instant.now(), java.time.ZoneId.systemDefault()) == null) {
                                                onSetupError("Choose a future wake time.")
                                            } else {
                                                onSetupError(null)
                                                onSaveStarted()
                                                actions.onSaveAlarm(schedule)
                                            }
                                        }
                                    }
                                } ?: run {
                                    navState.navigate(CurfewDestination.Edit)
                                }

                                CurfewDestination.Settings -> CurfewSettingsScreen(
                                    state.conditionLabel,
                                    state.conditionVerified,
                                    state.readiness.canArm,
                                    state.accountSignedIn,
                                    enforcementSummary = if (state.enforcementSession?.state == EnforcementSessionState.ACTIVE) {
                                        "Active session"
                                    } else if (state.isAccessibilityReady) {
                                        "Ready"
                                    } else {
                                        "Needs attention"
                                    },
                                    onCheckIn = { navState.navigate(CurfewDestination.CheckIn) },
                                    onReadiness = { navState.navigate(CurfewDestination.Readiness) },
                                    onAccount = { navState.navigate(CurfewDestination.Account) },
                                    onBedtimeProtection = { navState.navigate(CurfewDestination.Enforcement) },
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
                                        onEnterSetupMode(state.sunriseAlarm == null)
                                        navState.navigate(CurfewDestination.Edit)
                                    },
                                    onCheckIn = { navState.navigate(CurfewDestination.CheckIn) },
                                    onSetup = {
                                        val isSetupMode = state.sunriseAlarm == null
                                        onEnterSetupMode(isSetupMode)
                                        if (isSetupMode) {
                                            if (draft != null) navState.navigate(CurfewDestination.Edit)
                                            onNextSetupTask()
                                        } else {
                                            navState.navigate(
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
                        }
                    }
                }
            },
        )
    }
}
