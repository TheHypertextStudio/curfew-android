package studio.hypertext.curfew.ui

import androidx.compose.runtime.Composable
import studio.hypertext.curfew.account.AccountRecoveryState
import studio.hypertext.curfew.alarm.AlarmSetupDraft
import studio.hypertext.curfew.alarm.SavedSunriseAlarm
import studio.hypertext.curfew.alarm.SunriseSchedule
import studio.hypertext.curfew.enforcement.EnforcementPurpose
import studio.hypertext.curfew.enforcement.EnforcementSession
import studio.hypertext.curfew.readiness.AlarmReadiness
import studio.hypertext.curfew.readiness.AlarmSoundCheckState

/**
 * Compatibility overload preserving constructor parameters for existing instrumented tests.
 */
@Composable
internal fun CurfewAppFixture(
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
        openEnforcement = interceptedPackage != null,
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
