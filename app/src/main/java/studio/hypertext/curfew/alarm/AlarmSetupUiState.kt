package studio.hypertext.curfew.alarm

import studio.hypertext.curfew.readiness.AlarmPresentation
import studio.hypertext.curfew.readiness.AlarmReadiness

internal data class AlarmSetupEntry(
    val loaded: Boolean = false,
    val loadError: Boolean = false,
    val showWelcome: Boolean = false,
    val startSetup: Boolean = false,
    val draft: AlarmSetupDraft? = null,
)

internal data class AlarmSetupUiState(
    val loaded: Boolean = false,
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
    val placeCodeSelected: Boolean = false,
    val alarmBusy: Boolean = false,
    val alarmError: String? = null,
) {
    val canArm: Boolean
        get() = readiness.canArm && callbackId != null && conditionLabel != null && conditionVerified
}
