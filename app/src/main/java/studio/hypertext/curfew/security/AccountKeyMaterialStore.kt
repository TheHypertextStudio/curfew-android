package studio.hypertext.curfew.security

import android.content.Context
import studio.hypertext.curfew.account.AccountRootKeySink
import studio.hypertext.curfew.config.ProductionServices

enum class RecoveryEnrollmentStep {
    AUTHENTICATE_AAL2,
    ENROLL_DEVICE_KEY,
    SAVE_RECOVERY_KEY,
    READY,
}

class AccountKeyMaterialStore(
    context: Context,
) : AccountRootKeySink {
    private val secrets = AndroidKeystoreSecretStore(context)

    fun storeDevicePrivateKey(key: ByteArray) = secrets.put("account:device-private-key", key)

    fun storeAccountRootKey(key: ByteArray) = secrets.put(ProductionServices.ROOT_KEY, key)

    override fun storeRootKey(value: ByteArray) = storeAccountRootKey(value)

    override fun storePendingRecoveryKey(value: ByteArray) = secrets.put(ProductionServices.PENDING_RECOVERY_KEY, value)

    override fun clearPendingRecoveryKey() = secrets.delete(ProductionServices.PENDING_RECOVERY_KEY)

    fun hasDevicePrivateKey(): Boolean = secrets.get("account:device-private-key") != null

    fun hasAccountRootKey(): Boolean = secrets.get(ProductionServices.ROOT_KEY) != null

    fun pendingRecoveryKey(): ByteArray? = secrets.get(ProductionServices.PENDING_RECOVERY_KEY)

    fun accountRootKey(): ByteArray? = secrets.get(ProductionServices.ROOT_KEY)

    fun enrollmentStep(aal2Complete: Boolean): RecoveryEnrollmentStep =
        when {
            !aal2Complete -> RecoveryEnrollmentStep.AUTHENTICATE_AAL2
            !hasDevicePrivateKey() -> RecoveryEnrollmentStep.ENROLL_DEVICE_KEY
            !hasAccountRootKey() -> RecoveryEnrollmentStep.ENROLL_DEVICE_KEY
            pendingRecoveryKey() != null -> RecoveryEnrollmentStep.SAVE_RECOVERY_KEY
            else -> RecoveryEnrollmentStep.READY
        }
}
