package studio.hypertext.curfew.ui

import studio.hypertext.curfew.alarm.AlarmSetupDraft
import studio.hypertext.curfew.alarm.SunriseSchedule
import studio.hypertext.curfew.enforcement.EnforcementPurpose

/**
 * Consolidated UI action handlers dispatched from UI to ViewModel.
 */
data class CurfewUiActions(
    val onGrantExactAlarm: () -> Unit = {},
    val onGrantNotifications: () -> Unit = {},
    val onGrantFullScreen: () -> Unit = {},
    val onRepairAlarmChannel: () -> Unit = {},
    val onRaiseAlarmVolume: () -> Unit = {},
    val onPlayTestSound: () -> Unit = {},
    val onConfirmSound: () -> Unit = {},
    val onCancelSound: () -> Unit = {},
    val onAcknowledgeLimitations: () -> Unit = {},
    val onOpenAccount: () -> Unit = {},
    val onEnrollAccount: () -> Unit = {},
    val onRestoreRecoveryKey: (String) -> Unit = {},
    val onAcknowledgeRecoveryKey: () -> Unit = {},
    val onSaveCallback: suspend (String, String, String?) -> String = { _, _, _ -> "" },
    val onExportCondition: () -> Unit = {},
    val onVerifyCondition: () -> Unit = {},
    val onSaveAlarm: (SunriseSchedule) -> Unit = {},
    val onToggleAlarm: (Boolean) -> Unit = {},
    val onWelcomeChoice: suspend (Boolean) -> Unit = {},
    val onPrepareDraft: suspend (AlarmSetupDraft) -> Unit = {},
    val onSetupActivated: () -> Unit = {},
    val onToggleChosenApp: (String, Boolean) -> Unit = { _, _ -> },
    val onOpenAccessibilitySettings: () -> Unit = {},
    val onStartEnforcementSession: (EnforcementPurpose, Int) -> Unit = { _, _ -> },
    val onEmergencyStopEnforcement: () -> Unit = {},
    val onDismissInterceptionBanner: () -> Unit = {},
    val onEnforcementOpened: () -> Unit = {},
)
