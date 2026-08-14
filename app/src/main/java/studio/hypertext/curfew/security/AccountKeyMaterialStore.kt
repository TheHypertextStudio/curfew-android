package studio.hypertext.curfew.security

import android.content.Context

enum class RecoveryEnrollmentStep {
    AUTHENTICATE_AAL2,
    ENROLL_DEVICE_KEY,
    SAVE_RECOVERY_KEY,
    READY,
}

class AccountKeyMaterialStore(context: Context) {
    private val secrets = AndroidKeystoreSecretStore(context)

    fun storeDevicePrivateKey(key: ByteArray) = secrets.put("account:device-private-key", key)
    fun storeAccountRootKey(key: ByteArray) = secrets.put("account:root-key", key)
    fun storeRecoveryKey(key: ByteArray) = secrets.put("account:recovery-key", key)

    fun hasDevicePrivateKey(): Boolean = secrets.get("account:device-private-key") != null
    fun hasAccountRootKey(): Boolean = secrets.get("account:root-key") != null
    fun hasRecoveryKey(): Boolean = secrets.get("account:recovery-key") != null
    fun accountRootKey(): ByteArray? = secrets.get("account:root-key")

    fun enrollmentStep(aal2Complete: Boolean): RecoveryEnrollmentStep = when {
        !aal2Complete -> RecoveryEnrollmentStep.AUTHENTICATE_AAL2
        !hasDevicePrivateKey() -> RecoveryEnrollmentStep.ENROLL_DEVICE_KEY
        !hasRecoveryKey() -> RecoveryEnrollmentStep.SAVE_RECOVERY_KEY
        !hasAccountRootKey() -> RecoveryEnrollmentStep.ENROLL_DEVICE_KEY
        else -> RecoveryEnrollmentStep.READY
    }
}
