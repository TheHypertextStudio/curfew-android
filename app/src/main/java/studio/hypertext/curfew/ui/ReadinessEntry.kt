package studio.hypertext.curfew.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import studio.hypertext.curfew.alarm.AlarmSetupUiState
import studio.hypertext.curfew.platform.SystemSettingsLauncher
import studio.hypertext.curfew.readiness.AlarmSoundCheckViewModel

/**
 * Resolves the readiness sound-check model and permission launcher at the NavEntry. When that
 * entry pauses or leaves composition, it stops any unconfirmed preview.
 */
@Composable
internal fun ReadinessEntry(
    journey: AlarmSetupJourney,
    alarm: AlarmSetupUiState,
    systemSettings: SystemSettingsLauncher,
    refreshCapabilities: () -> Unit,
    acknowledgeLimitations: () -> Unit,
) {
    val sound: AlarmSoundCheckViewModel = hiltViewModel()
    val soundState by sound.state.collectAsStateWithLifecycle()
    val requestNotifications = rememberRequestNotifications(refreshCapabilities)
    LifecycleResumeEffect(sound) {
        onPauseOrDispose { sound.cancel() }
    }

    ReadinessRoute(
        appState = journey,
        state = alarm,
        actions = ReadinessActions(
            onEnter = {
                systemSettings.prepareAlarmNotifications()
                refreshCapabilities()
            },
            onGrantExactAlarm = systemSettings::requestExactAlarmAccess,
            onGrantNotifications = requestNotifications,
            onRepairAlarmChannel = systemSettings::openAlarmChannel,
            onPlayTestSound = sound::play,
            onAcknowledgeLimitations = acknowledgeLimitations,
            onRaiseAlarmVolume = systemSettings::openSoundSettings,
            onGrantFullScreen = systemSettings::requestFullScreenAccess,
            onConfirmSound = sound::confirm,
            onCancelSound = sound::cancel,
        ),
        soundState = soundState,
    )
}
