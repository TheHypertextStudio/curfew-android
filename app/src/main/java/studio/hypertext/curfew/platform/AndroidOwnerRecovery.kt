package studio.hypertext.curfew.platform

import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import studio.hypertext.curfew.alarm.OwnerRecoveryAuthorization
import studio.hypertext.curfew.alarm.OwnerRecoveryCompletion
import studio.hypertext.curfew.alarm.OwnerRecoveryResult

class AndroidOwnerRecovery(
    private val activity: ComponentActivity,
    private val onRecovered: (OwnerRecoveryResult) -> Unit,
    elapsedMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    val credentialAvailable: Boolean
        get() = activity.getSystemService(KeyguardManager::class.java).isDeviceSecure

    private val authorization = OwnerRecoveryAuthorization(elapsedMillis)
    private var pendingId: String? = null
    private var signal: CancellationSignal? = null
    private var saving: Job? = null
    private val completion = OwnerRecoveryCompletion(
        save = { AlarmOwnerRecoveryRepository(activity).recover(it) },
        finishOccurrence = { SunriseScheduler(activity).completeOccurrence(it) },
    )

    fun beginHold(campaignId: String, reason: String) {
        if (busy) return
        error = null
        authorization.beginHold(campaignId, reason)
    }

    fun cancelHold() { authorization.cancelHold() }

    fun requestConfirmation() {
        if (busy) return
        if (!credentialAvailable) {
            authorization.cancel()
            error = "Set a screen lock to use recovery."
            return
        }
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            authorization.cancelHold()
            return
        }
        val request = authorization.requestConfirmation() ?: return
        busy = true
        pendingId = request.requestId
        val cancellation = CancellationSignal().also { signal = it }
        try {
            BiometricPrompt.Builder(activity)
                .setTitle("Confirm alarm recovery")
                .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
                .authenticate(cancellation, activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (pendingId != request.requestId) return
                        authorization.finishConfirmation(request.requestId, false)
                        pendingId = null
                        signal = null
                        busy = false
                        error = "Confirmation canceled. Sunrise is still active."
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (pendingId != request.requestId) return
                        signal = null
                        val approved = authorization.finishConfirmation(
                            request.requestId,
                            result.authenticationType == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL,
                        )
                        if (approved == null) {
                            pendingId = null
                            busy = false
                            error = "Confirmation expired. Hold again to retry."
                            return
                        }
                        saving = activity.lifecycleScope.launch {
                            val outcome = completion.complete(approved)
                            if (pendingId != request.requestId) return@launch
                            pendingId = null
                            busy = false
                            when (outcome) {
                                OwnerRecoveryResult.NotSaved -> error = "Couldn’t save recovery. Sunrise is still active. Try again."
                                else -> onRecovered(outcome)
                            }
                        }
                    }
                })
        } catch (_: Exception) {
            authorization.finishConfirmation(request.requestId, false)
            pendingId = null
            signal = null
            busy = false
            error = "Couldn’t open screen lock confirmation. Try again."
        }
    }

    fun alarmEnded() {
        if (saving?.isActive != true) close()
    }

    fun close() {
        pendingId = null
        authorization.cancel()
        signal?.cancel()
        signal = null
        saving?.cancel()
        saving = null
        busy = false
        error = null
    }
}
