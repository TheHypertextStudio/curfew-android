package studio.hypertext.curfew.account

import android.content.Context
import java.util.Base64
import studio.hypertext.curfew.security.AccountKeyMaterialStore
import studio.hypertext.curfew.security.AndroidDeviceEncryptionKey

class AndroidAccountRecovery(context: Context) {
    private val keyStore = AccountKeyMaterialStore(context)
    private val transport = NativeAccountRecoveryTransport(context)
    private val coordinator = AccountRecoveryCoordinator(
        transport = transport,
        keySink = keyStore,
        rootEnvelopeOpener = AndroidDeviceEncryptionKey()::open,
    )
    private val distributor = PeerRootKeyDistributor(
        transport = transport,
        rootKeyProvider = keyStore::accountRootKey,
    )

    fun resumeAfterEnrollment(): AccountRecoveryState = coordinator.resumeAfterEnrollment().also {
        if (it == AccountRecoveryState.Ready) distributor.distribute()
    }

    fun restore(encodedRecoveryKey: String): AccountRecoveryState =
        coordinator.restore(encodedRecoveryKey.trim()).also {
            if (it == AccountRecoveryState.Ready) distributor.distribute()
        }

    fun distributeRootKeyToPeers(): Int = distributor.distribute()

    fun acknowledgeSavedRecoveryKey() = coordinator.acknowledgeSavedRecoveryKey()

    fun pendingState(): AccountRecoveryState? = keyStore.pendingRecoveryKey()?.let { key ->
        AccountRecoveryState.SaveRecoveryKey(
            Base64.getUrlEncoder().withoutPadding().encodeToString(key),
        )
    }
}
