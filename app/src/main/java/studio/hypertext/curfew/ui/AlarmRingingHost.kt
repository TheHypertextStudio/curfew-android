package studio.hypertext.curfew.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRuntimeClock
import studio.hypertext.curfew.alarm.AlarmRuntimeSnapshot
import studio.hypertext.curfew.alarm.OwnerRecoveryResult
import studio.hypertext.curfew.alarm.campaignStartedAt
import studio.hypertext.curfew.checkin.CheckInDescription
import studio.hypertext.curfew.checkin.PlaceApprovalResult
import studio.hypertext.curfew.platform.AndroidOwnerRecovery
import java.time.Instant
import java.util.Date

/**
 * Host component managing the ringing alarm presentation, scanner, and owner recovery flow.
 * Extracts active ringing/recovery presentation out of MainActivity into an isolated Composable.
 */
@Composable
internal fun AlarmRingingHost(
    snapshot: AlarmRuntimeSnapshot,
    ringingCondition: CheckInDescription?,
    onAlarmDismissed: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val scope = rememberCoroutineScope()
    var recoveryVisible by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }

    val ownerRecovery = remember(activity) {
        if (activity != null) {
            AndroidOwnerRecovery(
                activity = activity,
                onRecovered = { result: OwnerRecoveryResult ->
                    recoveryVisible = false
                    val message = when (result) {
                        OwnerRecoveryResult.Recovered -> "Alarm stopped through recovery."
                        OwnerRecoveryResult.RecoveredNextAlarmUnavailable -> "Alarm stopped. Check tomorrow’s alarm."
                        OwnerRecoveryResult.NotActive -> "This alarm is not ringing."
                        OwnerRecoveryResult.NotSaved -> "Couldn’t stop the alarm. Try again."
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    if (result == OwnerRecoveryResult.Recovered || result == OwnerRecoveryResult.RecoveredNextAlarmUnavailable) {
                        onAlarmDismissed()
                    }
                },
            )
        } else null
    }

    BackHandler(enabled = recoveryVisible) {
        ownerRecovery?.close()
        recoveryVisible = false
    }

    if (recoveryVisible && ownerRecovery != null) {
        AlarmRecoveryScreen(
            conditionLabel = ringingCondition?.displayLabel ?: "your check-in",
            credentialAvailable = ownerRecovery.credentialAvailable,
            busy = ownerRecovery.busy,
            error = ownerRecovery.error,
            onBack = {
                ownerRecovery.close()
                recoveryVisible = false
            },
            onBeginHold = { reason ->
                val campaignId = snapshot.state.campaignId
                ownerRecovery.beginHold(campaignId, reason)
            },
            onCancelHold = ownerRecovery::cancelHold,
            onConfirm = ownerRecovery::requestConfirmation,
            onSecuritySettings = {
                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            },
        )
    } else if (scanning) {
        PlaceScanScreen(
            label = ringingCondition?.displayLabel ?: "saved",
            onScan = { payload ->
                scope.launch {
                    try {
                        val repository = studio.hypertext.curfew.checkin.PlaceCheckInRepository(context)
                        val result = repository.approve(snapshot.state.campaignId, payload)
                        val message = when (result) {
                            PlaceApprovalResult.CHECKED_IN -> "Checked in."
                            PlaceApprovalResult.NOT_ACTIVE -> "This alarm is no longer active."
                            PlaceApprovalResult.NEXT_ALARM_UNAVAILABLE -> "Checked in. Check tomorrow’s alarm."
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        if (result == PlaceApprovalResult.CHECKED_IN || result == PlaceApprovalResult.NEXT_ALARM_UNAVAILABLE) {
                            onAlarmDismissed()
                        }
                    } finally {
                        scanning = false
                    }
                }
            },
            onCancel = { scanning = false },
        )
    } else {
        val clock = DateFormat.getTimeFormat(context)
        val campaign = snapshot.state
        val startedInstant = (campaign as? AlarmCampaignState)?.campaignStartedAt()
        val timeString = startedInstant?.let { clock.format(Date.from(it)) } ?: ""

        val nextRingTime = snapshot.takeIf { it.state is AlarmCampaignState.Quiet }?.let { quietSnap ->
            AlarmRuntimeClock.nextTransitionAt(
                quietSnap,
                Instant.now(),
                SystemClock.elapsedRealtime(),
                snapshot.bootId,
            )?.let { next -> clock.format(Date.from(next)) }
        }

        AlarmRingingScreen(
            time = timeString,
            nextRingTime = nextRingTime,
            onRecover = { recoveryVisible = true },
            conditionLabel = ringingCondition?.displayLabel ?: "your check-in",
            actionLabel = if (ringingCondition?.placeCode == true) {
                "Scan code"
            } else {
                ringingCondition?.actionUrl?.let { "Open check-in" }
            },
            onOpenAction = {
                if (ringingCondition?.placeCode == true) {
                    scanning = true
                }
                ringingCondition?.actionUrl?.let { url ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }.onFailure {
                        Toast.makeText(context, "Couldn’t open the check-in link.", Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
    }
}
