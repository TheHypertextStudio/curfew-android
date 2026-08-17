package studio.hypertext.curfew.account

import android.content.Context
import java.util.Base64
import studio.hypertext.curfew.security.AccountKeyMaterialStore
import studio.hypertext.curfew.security.AndroidDeviceEncryptionKey

class AndroidAccountRecovery(context: Context) {
    private val keyStore = AccountKeyMaterialStore(context)
    private val coordinator = AccountRecoveryCoordinator(
        transport = NativeAccountRecoveryTransport(context),
        keySink = keyStore,
        rootEnvelopeOpener = AndroidDeviceEncryptionKey()::open,
    )

    fun resumeAfterEnrollment(): AccountRecoveryState = coordinator.resumeAfterEnrollment()

    fun restore(encodedRecoveryKey: String): AccountRecoveryState =
        coordinator.restore(encodedRecoveryKey.trim())

    fun acknowledgeSavedRecoveryKey() = coordinator.acknowledgeSavedRecoveryKey()

    fun pendingState(): AccountRecoveryState? = keyStore.pendingRecoveryKey()?.let { key ->
        AccountRecoveryState.SaveRecoveryKey(
            Base64.getUrlEncoder().withoutPadding().encodeToString(key),
        )
    }
}
