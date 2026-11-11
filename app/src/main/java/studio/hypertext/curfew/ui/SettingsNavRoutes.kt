package studio.hypertext.curfew.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import studio.hypertext.curfew.R
import studio.hypertext.curfew.account.AccountUiState
import studio.hypertext.curfew.account.AccountViewModel
import studio.hypertext.curfew.alarm.AlarmSetupUiState
import studio.hypertext.curfew.platform.SystemSettingsLauncher

/**
 * Resolves the settings model for the current NavEntry and refreshes capability summaries when
 * that screen resumes.
 */
@Composable
internal fun SettingsEntry(journey: AlarmSetupJourney, alarm: AlarmSetupUiState) {
    val model: SettingsViewModel = hiltViewModel()
    val settings by model.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refreshCapabilities() }
    SettingsRoute(journey, alarm, settings)
}

/**
 * Combines alarm setup status with the settings entry's account and protection summaries. It
 * delegates navigation to the shared shell.
 */
@Composable
internal fun SettingsRoute(
    journey: AlarmSetupJourney,
    alarm: AlarmSetupUiState,
    settings: SettingsUiState,
) {
    CurfewSettingsScreen(
        alarm.conditionLabel,
        alarm.conditionVerified,
        alarm.readiness.canArm,
        settings.accountSignedIn,
        enforcementSummary = stringResource(
            when (settings.protection) {
                ProtectionStatus.ACTIVE -> R.string.protection_status_active
                ProtectionStatus.READY -> R.string.protection_status_ready
                ProtectionStatus.NEEDS_ATTENTION -> R.string.protection_status_needs_attention
            },
        ),
        onCheckIn = { journey.navigate(CurfewDestination.CheckIn) },
        onReadiness = { journey.navigate(CurfewDestination.Readiness) },
        onAccount = { journey.navigate(CurfewDestination.Account) },
        onBedtimeProtection = { journey.navigate(CurfewDestination.Enforcement) },
    )
}

/**
 * Binds the Activity-owned account model to the account screen and browser launcher. The model
 * outlives the route because Android may deliver the browser result elsewhere.
 */
@Composable
internal fun AccountEntry(model: AccountViewModel, systemSettings: SystemSettingsLauncher) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    AccountRoute(
        state = state,
        onOpenAccount = systemSettings::openAccountPortal,
        onEnroll = {
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
        onRestoreRecoveryKey = model::restoreRecoveryKey,
        onAcknowledgeRecoveryKey = model::acknowledgeRecoveryKey,
    )
}

/**
 * Renders account state with supplied actions. It can be used with fixture state without
 * starting account services.
 */
@Composable
internal fun AccountRoute(
    state: AccountUiState,
    onOpenAccount: () -> Unit,
    onEnroll: () -> Unit,
    onRestoreRecoveryKey: (String) -> Unit,
    onAcknowledgeRecoveryKey: () -> Unit,
) {
    CurfewAccountScreen(
        state.accountEnrolled,
        state.accountSignedIn,
        state.recoveryState,
        onOpenAccount,
        onEnroll,
        onRestoreRecoveryKey,
        onAcknowledgeRecoveryKey,
    )
}
