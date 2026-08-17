package studio.hypertext.curfew.security

import android.content.Context
import studio.hypertext.curfew.account.AccountRootKeySink

enum class RecoveryEnrollmentStep {
    AUTHENTICATE_AAL2,
    ENROLL_DEVICE_KEY,
    SAVE_RECOVERY_KEY,
    READY,
}

class AccountKeyMaterialStore(context: Context) : AccountRootKeySink {
    private val secrets = AndroidKeystoreSecretStore(context)

    fun storeDevicePrivateKey(key: ByteArray) = secrets.put("account:device-private-key", key)
    fun storeAccountRootKey(key: ByteArray) = secrets.put("account:root-key", key)

    override fun storeRootKey(value: ByteArray) = storeAccountRootKey(value)

    override fun storePendingRecoveryKey(value: ByteArray) =
        secrets.put("account:pending-recovery-key", value)

    override fun clearPendingRecoveryKey() = secrets.delete("account:pending-recovery-key")

    fun hasDevicePrivateKey(): Boolean = secrets.get("account:device-private-key") != null
    fun hasAccountRootKey(): Boolean = secrets.get("account:root-key") != null
    fun pendingRecoveryKey(): ByteArray? = secrets.get("account:pending-recovery-key")
    fun accountRootKey(): ByteArray? = secrets.get("account:root-key")

    fun enrollmentStep(aal2Complete: Boolean): RecoveryEnrollmentStep = when {
        !aal2Complete -> RecoveryEnrollmentStep.AUTHENTICATE_AAL2
        !hasDevicePrivateKey() -> RecoveryEnrollmentStep.ENROLL_DEVICE_KEY
        !hasAccountRootKey() -> RecoveryEnrollmentStep.ENROLL_DEVICE_KEY
        pendingRecoveryKey() != null -> RecoveryEnrollmentStep.SAVE_RECOVERY_KEY
        else -> RecoveryEnrollmentStep.READY
    }
}
