package studio.hypertext.curfew.ui

import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import studio.hypertext.curfew.R
import studio.hypertext.curfew.alarm.OwnerRecoveryResult
import studio.hypertext.curfew.alarm.campaignStartedAt
import studio.hypertext.curfew.checkin.PlaceApprovalResult
import studio.hypertext.curfew.platform.rememberOwnerRecovery

@Composable
internal fun AlarmRingingHost(
    alarm: RingingAlarmPresentation,
    onApprovePlaceCode: suspend (campaignId: String, code: String) -> PlaceApprovalResult,
    onOpenCheckIn: (String) -> Unit,
    onSecuritySettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val campaign = alarm.snapshot.state
    val condition = alarm.condition
    val conditionLabel = condition?.displayLabel ?: stringResource(R.string.alarm_check_in_fallback)
    var recoveryVisible by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }

    val ownerRecovery = rememberOwnerRecovery { result ->
        recoveryVisible = false
        val message = when (result) {
            OwnerRecoveryResult.Recovered -> R.string.alarm_recovered
            OwnerRecoveryResult.RecoveredNextAlarmUnavailable -> R.string.alarm_recovered_next_unavailable
            OwnerRecoveryResult.NotActive -> R.string.alarm_not_active
            OwnerRecoveryResult.NotSaved -> R.string.alarm_recovery_failed
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    BackHandler(enabled = recoveryVisible) {
        ownerRecovery?.close()
        recoveryVisible = false
    }

    when {
        recoveryVisible && ownerRecovery != null -> AlarmRecoveryScreen(
            conditionLabel = conditionLabel,
            credentialAvailable = ownerRecovery.credentialAvailable,
            busy = ownerRecovery.busy,
            error = ownerRecovery.error,
            onBack = {
                ownerRecovery.close()
                recoveryVisible = false
            },
            onBeginHold = { ownerRecovery.beginHold(campaign.campaignId, it) },
            onCancelHold = ownerRecovery::cancelHold,
            onConfirm = ownerRecovery::requestConfirmation,
            onSecuritySettings = onSecuritySettings,
        )
        scanning -> PlaceScanScreen(
            label = conditionLabel,
            onScan = { payload ->
                scope.launch {
                    val message = try {
                        when (onApprovePlaceCode(campaign.campaignId, payload)) {
                            PlaceApprovalResult.CHECKED_IN -> R.string.alarm_checked_in
                            PlaceApprovalResult.NOT_ACTIVE -> R.string.alarm_not_active
                            PlaceApprovalResult.NEXT_ALARM_UNAVAILABLE -> R.string.alarm_checked_in_next_unavailable
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        R.string.alarm_check_in_scan_failed
                    } finally {
                        scanning = false
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            },
            onCancel = { scanning = false },
        )
        else -> {
            val clock = DateFormat.getTimeFormat(context)
            AlarmRingingScreen(
                time = clock.format(Date.from(campaign.campaignStartedAt())),
                nextRingTime = alarm.nextRingAt?.let { clock.format(Date.from(it)) },
                onRecover = { recoveryVisible = true },
                conditionLabel = conditionLabel,
                actionLabel = when {
                    condition?.placeCode == true -> stringResource(R.string.alarm_scan_code)
                    condition?.actionUrl != null -> stringResource(R.string.alarm_open_check_in)
                    else -> null
                },
                onOpenAction = {
                    if (condition?.placeCode == true) {
                        scanning = true
                    } else {
                        condition?.actionUrl?.let(onOpenCheckIn)
                    }
                },
            )
        }
    }
}
