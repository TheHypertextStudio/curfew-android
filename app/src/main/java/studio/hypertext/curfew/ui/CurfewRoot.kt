package studio.hypertext.curfew.ui

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import studio.hypertext.curfew.R
import studio.hypertext.curfew.platform.SystemSettingsLauncher
import studio.hypertext.curfew.ui.theme.CurfewTheme

@Composable
internal fun CurfewRoot(viewModel: CurfewViewModel, systemSettings: SystemSettingsLauncher) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val requestNotifications = rememberRequestNotifications(viewModel::refreshCapabilities)

    RestoreOnResumeEffect(viewModel::onForegroundResume)
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.accountMessages.collect { message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }
    if (activity != null) {
        ShowOverKeyguardEffect(activity, active = state.isAlarmRinging)
    }

    CurfewTheme {
        val ringingSnapshot = state.ringingSnapshot
        if (state.isAlarmRinging && ringingSnapshot != null) {
            key(ringingSnapshot.state.campaignId) {
                AlarmRingingHost(snapshot = ringingSnapshot, ringingCondition = state.ringingCondition)
            }
            return@CurfewTheme
        }
        val actions = CurfewUiActions(
            onGrantExactAlarm = systemSettings::requestExactAlarmAccess,
            onGrantNotifications = requestNotifications,
            onGrantFullScreen = systemSettings::requestFullScreenAccess,
            onRepairAlarmChannel = systemSettings::openAlarmChannel,
            onRaiseAlarmVolume = systemSettings::openSoundSettings,
            onPlayTestSound = viewModel::playTestSound,
            onConfirmSound = viewModel::confirmSound,
            onCancelSound = viewModel::cancelTestSound,
            onAcknowledgeLimitations = viewModel::acknowledgeLimitations,
            onOpenAccount = systemSettings::openAccountPortal,
            onEnrollAccount = {
                scope.launch {
                    try {
                        systemSettings.enrollAccount()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        Toast.makeText(context, R.string.account_enrollment_failed, Toast.LENGTH_LONG).show()
                    }
                }
            },
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
            onOpenAccessibilitySettings = systemSettings::openAccessibilitySettings,
            onStartEnforcementSession = viewModel::startEnforcementSession,
            onEmergencyStopEnforcement = viewModel::emergencyStopEnforcement,
            onDismissInterceptionBanner = viewModel::dismissInterceptionBanner,
            onEnforcementOpened = viewModel::onEnforcementOpened,
        )
        CurfewApp(
            state = state,
            actions = actions,
            placeCheckInContent = { advanced, done, useForAlarm ->
                PlaceCheckInHost(
                    selectedId = state.callbackId,
                    onAdvanced = advanced,
                    onDone = done,
                    onUseForAlarm = useForAlarm,
                )
            },
        )
    }
}
