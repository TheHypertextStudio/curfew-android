package studio.hypertext.curfew.ui

import studio.hypertext.curfew.account.AccountRecoveryState
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRuntimeSnapshot
import studio.hypertext.curfew.alarm.AlarmSetupDraft
import studio.hypertext.curfew.alarm.SavedSunriseAlarm
import studio.hypertext.curfew.enforcement.EnforcementSession
import studio.hypertext.curfew.readiness.AlarmPresentation
import studio.hypertext.curfew.readiness.AlarmReadiness
import studio.hypertext.curfew.readiness.AlarmSoundCheckState

/**
 * Consolidated UI state representing the single source of truth for Curfew screens.
 */
data class CurfewUiState(
    val readiness: AlarmReadiness = AlarmReadiness(
        canArm = false,
        requiredActions = emptySet(),
        disclosures = emptySet(),
        presentation = AlarmPresentation.FULL_SCREEN,
    ),
    val sunriseAlarm: SavedSunriseAlarm? = null,
    val callbackId: String? = null,
    val conditionLabel: String? = null,
    val alarmConditionLabel: String? = null,
    val conditionVerified: Boolean = false,
    val conditionBusy: Boolean = false,
    val conditionMessage: String? = null,
    val conditionSetupFileSaved: Boolean = false,
    val placeCodeSelected: Boolean = false,
    val alarmBusy: Boolean = false,
    val alarmError: String? = null,
    val accountEnrolled: Boolean = false,
    val accountSignedIn: Boolean = false,
    val accountRecoveryState: AccountRecoveryState? = null,
    val soundCheckState: AlarmSoundCheckState = AlarmSoundCheckState.CLOSED,
    val showWelcome: Boolean = false,
    val startSetup: Boolean = false,
    val setupDraft: AlarmSetupDraft? = null,
    val enforcementSession: EnforcementSession? = null,
    val enforcementChosenApps: Set<String> = emptySet(),
    val isAccessibilityReady: Boolean = false,
    val interceptedPackage: String? = null,
    val initialDataReady: Boolean = false,
    val firstUseLoadError: Boolean = false,
    val ringingAlarm: Boolean = false,
    val campaignId: String? = null,
    val ringingSnapshot: AlarmRuntimeSnapshot? = null,
) {
    val canArm: Boolean
        get() = readiness.canArm && callbackId != null && conditionLabel != null && conditionVerified

    val isAlarmRinging: Boolean
        get() = ringingSnapshot?.state is AlarmCampaignState.Ringing ||
            ringingSnapshot?.state is AlarmCampaignState.Quiet
}
