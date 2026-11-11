package studio.hypertext.curfew.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import studio.hypertext.curfew.R
import studio.hypertext.curfew.alarm.AlarmSaveFailure

/**
 * Converts a typed alarm save failure into localized screen copy. A null failure has no
 * message.
 */
@Composable
internal fun alarmSaveMessage(failure: AlarmSaveFailure?): String? = when (failure) {
    AlarmSaveFailure.FUTURE_TIME_REQUIRED -> stringResource(R.string.alarm_choose_future_date_time)
    AlarmSaveFailure.SAVE_FAILED -> stringResource(R.string.alarm_save_failed)
    null -> null
}
