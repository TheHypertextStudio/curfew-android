package studio.hypertext.curfew.alarm

import studio.hypertext.curfew.readiness.AlarmPresentation
import studio.hypertext.curfew.readiness.AlarmReadiness

/**
 * The failure categories the alarm screens can explain to the user. Raw exceptions and storage
 * details remain below the presentation layer.
 */
internal enum class AlarmSaveFailure { FUTURE_TIME_REQUIRED, SAVE_FAILED }

/**
 * Classifies known schedule validation by exception type. Unexpected errors map to a generic
 * save failure so internal messages never reach the UI.
 */
internal fun alarmSaveFailure(failure: Exception): AlarmSaveFailure = when (failure) {
    is FutureAlarmTimeRequired -> AlarmSaveFailure.FUTURE_TIME_REQUIRED
    else -> AlarmSaveFailure.SAVE_FAILED
}

/**
 * The startup information needed to choose the first destination: whether setup has loaded,
 * whether it can be retried, and whether an unfinished draft should resume.
 */
internal data class AlarmSetupEntry(
    val loaded: Boolean = false,
    val loadError: Boolean = false,
    val showWelcome: Boolean = false,
    val startSetup: Boolean = false,
    val draft: AlarmSetupDraft? = null,
)

/**
 * Shared inputs for alarm setup: the saved schedule, its check-in, the currently selected
 * check-in, and readiness. Routes that need these values collect this state; unrelated
 * destinations keep their own state.
 */
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
    val alarmError: AlarmSaveFailure? = null,
) {
    val canArm: Boolean
        get() = readiness.canArm && callbackId != null && conditionLabel != null && conditionVerified
}
