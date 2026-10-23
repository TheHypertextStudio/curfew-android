package studio.hypertext.curfew.screenshots

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.*
import studio.hypertext.curfew.ui.*
import studio.hypertext.curfew.ui.theme.CurfewTheme
import studio.hypertext.curfew.alarm.*
import studio.hypertext.curfew.account.AccountRecoveryState
import studio.hypertext.curfew.readiness.*

enum class CatalogState {
    EDIT_NEEDS_SETUP,
    EDIT_NO_DAYS,
    CHECKIN_EDIT_EXISTING,
    TIME_INPUT_INVALID,
    TIME_INPUT_EMPTY,
    READINESS_EXPANDED_LIMITS,
    WELCOME,
    SETUP_ONCE,
    SETUP_WEEKLY,
    EDIT_ONCE,
    EDIT_WEEKLY,
    EDIT_BUSY,
    EDIT_ERROR,
    CHECKIN_INTRO,
    CHECKIN_DEFER_BUSY,
    CHECKIN_DEFER_ERROR,
    CHECKIN_EMPTY,
    CHECKIN_VALID,
    CHECKIN_INVALID,
    CHECKIN_DETAILS,
    CHECKIN_INVALID_ACTION,
    CHECKIN_SAVING,
    CHECKIN_SAVE_ERROR,
    CHECKIN_TRANSFER,
    CHECKIN_TEST_READY,
    CHECKIN_CHECKING,
    CHECKIN_TEST_ERROR,
    CHECKIN_CONNECTED,
    CHECKIN_CONNECTION_DETAILS,
    CHECKIN_REPLACE,
    READINESS_EXACT,
    READINESS_NOTIFICATIONS,
    READINESS_FULLSCREEN,
    READINESS_CHANNEL,
    READINESS_VOLUME,
    READINESS_SOUND,
    READINESS_SOUND_PLAYING,
    READINESS_SOUND_HEARD,
    READINESS_SOUND_FAILED,
    READINESS_SOUND_SAVING,
    READINESS_SOUND_SAVE_FAILED,
    READINESS_LIMITS,
    READINESS_READY,
    REVIEW_ONCE,
    REVIEW_WEEKLY,
    REVIEW_BUSY,
    REVIEW_ERROR,
    DAILY_EMPTY,
    DAILY_ON,
    DAILY_OFF,
    DAILY_WEEKLY,
    DAILY_COMPLETE,
    DAILY_ATTENTION,
    DAILY_BUSY,
    DAILY_ERROR,
    DAILY_UNVERIFIED,
    SETTINGS_EMPTY,
    SETTINGS_READY,
    ACCOUNT_LOCAL,
    ACCOUNT_PENDING,
    ACCOUNT_ENROLLED,
    RECOVERY_EMPTY,
    RECOVERY_ENTERED,
    RECOVERY_SAVE,
    SOUND_PLAYING,
    SOUND_HEARD,
    SOUND_FAILED,
    SOUND_SAVING,
    SOUND_SAVE_FAILED,
    TIME_PICKER,
    DATE_PICKER,
    RINGING_ACTION,
    RINGING_NO_ACTION,
    RINGING_QUIET,
    RINGING_LONG,
    RECOVERY_REASON_EMPTY,
    RECOVERY_REASON_SHORT,
    RECOVERY_REASON_READY,
    RECOVERY_NO_LOCK,
    RECOVERY_BUSY,
    RECOVERY_ERROR,
    LOAD_ERROR,
}

private val fixedDate = LocalDate.of(2030, 10, 23)
private val fixedTime = LocalTime.of(7, 0)
private val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
private fun draft(weekly: Boolean) = AlarmSetupDraft("example-alarm", fixedTime, if (weekly) null else fixedDate, if (weekly) weekdays else emptySet())
private fun alarm(weekly: Boolean = false, enabled: Boolean = true, complete: Boolean = false) = SavedSunriseAlarm(
    SunriseSchedule("example-alarm", fixedTime, if (weekly) null else fixedDate, if (weekly) weekdays else emptySet(), enabled, "example-checkin"),
    null, if (complete) fixedDate else null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogFixture(state: CatalogState, position: String, dark: Boolean) {
    val id = state.name.lowercase()
    val ready = AlarmReadiness(true, emptySet(), emptySet(), AlarmPresentation.FULL_SCREEN)
    CurfewTheme(darkTheme = dark, dynamicColor = false) {
        CompositionLocalProvider(LocalTaskInitialScroll provides if (position == "bottom") Int.MAX_VALUE else 0) {
        Surface(Modifier.fillMaxSize()) {
            when {
                id == "welcome" -> CurfewWelcomeScreen({}, motionEnabled = false)
                id.startsWith("setup_") || id.startsWith("edit_") -> SunriseScheduleEditor(
                    if (id.startsWith("setup")) null else alarm(id in setOf("edit_weekly", "edit_no_days")), "example-checkin", id != "edit_needs_setup",
                    id == "edit_busy", if (id == "edit_error") "Couldn’t save alarm. Try again." else null, {}, {},
                    setupMode = id.startsWith("setup"), initialDraft = draft(id.endsWith("weekly") || id == "edit_no_days"), initialWeekdayMask = if (id == "edit_no_days") 0 else null,
                )
                id.startsWith("checkin_") -> {
                    val editing = id in setOf("checkin_empty", "checkin_valid", "checkin_invalid", "checkin_details", "checkin_invalid_action", "checkin_saving", "checkin_save_error", "checkin_edit_existing")
                    val connected = id in setOf("checkin_transfer", "checkin_test_ready", "checkin_checking", "checkin_test_error", "checkin_connected", "checkin_connection_details", "checkin_replace", "checkin_edit_existing")
                    val verified = id in setOf("checkin_connected", "checkin_connection_details", "checkin_replace")
                    val form = remember(id) { CheckInState().apply {
                        this.editing = editing
                        endpoint = when(id) { "checkin_empty" -> ""; "checkin_invalid" -> "http://example.invalid/check-in"; else -> "https://example.invalid/check-in" }
                        name = "Morning check-in"
                        actionUrl = if (id == "checkin_invalid_action") "not a link" else "https://example.invalid/wake"
                        optionalDetails = id in setOf("checkin_details", "checkin_invalid_action")
                        connectionDetails = id == "checkin_connection_details"
                    } }
                    CheckInScreen(if (connected) "example-checkin" else null, if (connected) "Morning check-in" else null, verified,
                        id in setOf("checkin_saving", "checkin_checking"),
                        when(id) { "checkin_save_error" -> "Couldn’t save the check-in. Try again."; "checkin_test_error" -> "Couldn’t connect. Try again."; else -> null },
                        { _, _, _ -> "example-checkin" }, {}, {}, {}, inSetup = id != "checkin_replace", state = form,
                        onDefer = {}, deferBusy = id == "checkin_defer_busy", deferError = if (id == "checkin_defer_error") "Couldn’t save your wake time. Try again." else null,
                        onUseForAlarm = if (id == "checkin_replace") ({}) else null, setupFileSaved = id == "checkin_test_ready",
                    )
                }
                id.startsWith("readiness_") -> {
                    val action = when(id) {
                        "readiness_exact" -> ReadinessAction.GRANT_EXACT_ALARM_ACCESS
                        "readiness_notifications" -> ReadinessAction.GRANT_NOTIFICATIONS
                        "readiness_fullscreen" -> ReadinessAction.GRANT_FULL_SCREEN_ACCESS
                        "readiness_channel" -> ReadinessAction.REPAIR_ALARM_CHANNEL
                        "readiness_volume" -> ReadinessAction.RAISE_ALARM_VOLUME
                        "readiness_sound", "readiness_sound_playing", "readiness_sound_heard", "readiness_sound_failed", "readiness_sound_saving", "readiness_sound_save_failed" -> ReadinessAction.PLAY_TEST_SOUND
                        "readiness_limits" -> ReadinessAction.ACKNOWLEDGE_LIMITATIONS
                        else -> null
                    }
                    AlarmReadinessScreen(ready.copy(canArm = action == null, requiredActions = action?.let { setOf(it) } ?: emptySet()), {}, {}, {}, {}, {}, {}, {}, inSetup = action != null, initiallyShowLimits = id == "readiness_expanded_limits", soundState = when(id) {
                        "readiness_sound_playing" -> AlarmSoundCheckState.PLAYING
                        "readiness_sound_heard" -> AlarmSoundCheckState.AWAITING_CONFIRMATION
                        "readiness_sound_failed" -> AlarmSoundCheckState.FAILED
                        "readiness_sound_saving" -> AlarmSoundCheckState.SAVING
                        "readiness_sound_save_failed" -> AlarmSoundCheckState.SAVE_FAILED
                        else -> AlarmSoundCheckState.CLOSED
                    })
                }
                id.startsWith("review_") -> AlarmSetupReview(draft(id == "review_weekly"), "Morning check-in", id == "review_busy", if (id == "review_error") "Choose a future wake time." else null, onActivate = {})
                id.startsWith("daily_") -> SunriseDailyScreen(
                    if (id == "daily_empty") null else alarm(id == "daily_weekly", id != "daily_off", id == "daily_complete"),
                    if (id == "daily_empty") null else "Morning check-in", id != "daily_unverified", id != "daily_attention", id == "daily_busy",
                    if (id == "daily_error") "Couldn’t update alarm. Try again." else null, {}, {}, {}, {},
                )
                id.startsWith("settings_") -> CurfewSettingsScreen(if (id == "settings_ready") "Morning check-in" else null, id == "settings_ready", id == "settings_ready", id == "settings_ready", {}, {}, {})
                id.startsWith("account_") || id in setOf("recovery_empty", "recovery_entered", "recovery_save") -> CurfewAccountScreen(
                    id == "account_enrolled", id != "account_local",
                    when(id) { "recovery_empty", "recovery_entered" -> AccountRecoveryState.EnterRecoveryKey; "recovery_save" -> AccountRecoveryState.SaveRecoveryKey("EXAMPLE-NOT-A-REAL-RECOVERY-KEY"); else -> null },
                    {}, {}, {}, {}, initialRecoveryKey = if (id == "recovery_entered") "EXAMPLE-NOT-A-REAL-RECOVERY-KEY" else "",
                )
                id.startsWith("sound_") -> AlarmSoundCheckDialog(when(id) { "sound_playing" -> AlarmSoundCheckState.PLAYING; "sound_heard" -> AlarmSoundCheckState.AWAITING_CONFIRMATION; "sound_failed" -> AlarmSoundCheckState.FAILED; "sound_saving" -> AlarmSoundCheckState.SAVING; else -> AlarmSoundCheckState.SAVE_FAILED }, {}, {}, {})
                id == "time_picker" || id.startsWith("time_input_") -> AlarmTimePicker(fixedTime, {}, {}, initialInputMode = id.startsWith("time_input_"), initialHourText = if (id == "time_input_invalid") "99" else if (id == "time_input_empty") "" else null, initialMinuteText = if (id == "time_input_invalid") "99" else null)
                id == "date_picker" -> AlarmDatePicker(fixedDate, {}, {})
                id.startsWith("ringing_") -> AlarmRingingScreen("07:00", if (id == "ringing_long") "Complete your morning check-in with the service you connected" else "Morning check-in", if (id == "ringing_no_action") null else "Open check-in", {}, {}, if (id == "ringing_quiet") "07:03" else null)
                id.startsWith("recovery_") -> AlarmRecoveryScreen("Morning check-in", id != "recovery_no_lock", id == "recovery_busy", if (id == "recovery_error") "Couldn’t confirm recovery. Try again." else null, {}, {}, {}, {}, {}, initialReason = when(id) { "recovery_reason_short" -> "My service is unavailable."; "recovery_reason_ready", "recovery_busy", "recovery_error" -> "My check-in service is unavailable this morning, and I need to recover this alarm."; else -> "" })
                id == "load_error" -> FirstUseLoadErrorScreen({})
            }
        }
    }
}

}