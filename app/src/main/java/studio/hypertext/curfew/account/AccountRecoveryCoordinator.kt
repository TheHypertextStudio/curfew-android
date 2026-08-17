package studio.hypertext.curfew.account

import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import studio.hypertext.curfew.protocols.RecoveryKeyEnvelope
import studio.hypertext.curfew.protocols.RootKeyEnvelope
import studio.hypertext.curfew.security.AccountRootKeyRecovery

interface AccountRecoveryTransport {
    fun getRootEnvelope(): RootKeyEnvelope?

    fun getRecoveryEnvelope(): RecoveryKeyEnvelope?

    /** Returns false when another device already created the envelope for this epoch. */
    fun putRecoveryEnvelope(envelope: RecoveryKeyEnvelope): Boolean
}

interface AccountRootKeySink {
    fun storeRootKey(value: ByteArray)

    fun storePendingRecoveryKey(value: ByteArray)

    fun clearPendingRecoveryKey()
}

sealed interface AccountRecoveryState {
    data object Ready : AccountRecoveryState

    data object EnterRecoveryKey : AccountRecoveryState

    data class SaveRecoveryKey(val encodedRecoveryKey: String) : AccountRecoveryState
}

class AccountRecoveryCoordinator(
    private val transport: AccountRecoveryTransport,
    private val keySink: AccountRootKeySink,
    private val rootEnvelopeOpener: (RootKeyEnvelope) -> ByteArray,
    private val randomBytes: (Int) -> ByteArray = ::secureRandomBytes,
    private val now: () -> String = { Instant.now().toString() },
) {
    fun resumeAfterEnrollment(): AccountRecoveryState {
        transport.getRootEnvelope()?.let { envelope ->
            keySink.storeRootKey(rootEnvelopeOpener(envelope).requireRootKey())
            return AccountRecoveryState.Ready
        }

        if (transport.getRecoveryEnvelope() != null) {
            return AccountRecoveryState.EnterRecoveryKey
        }

        val rootKey = randomBytes(ROOT_KEY_BYTES).requireSize(ROOT_KEY_BYTES).copyOf()
        val recoveryKey = randomBytes(ROOT_KEY_BYTES).requireSize(ROOT_KEY_BYTES).copyOf()
        val envelope = AccountRootKeyRecovery.createRecoveryEnvelope(
            rootKey = rootKey,
            recoveryKey = recoveryKey,
            keyEpoch = INITIAL_KEY_EPOCH,
            createdAt = now(),
            salt = randomBytes(RECOVERY_SALT_BYTES).requireSize(RECOVERY_SALT_BYTES),
            nonce = randomBytes(GCM_NONCE_BYTES).requireSize(GCM_NONCE_BYTES),
        )

        if (!transport.putRecoveryEnvelope(envelope)) {
            rootKey.fill(0)
            recoveryKey.fill(0)
            return AccountRecoveryState.EnterRecoveryKey
        }

        keySink.storeRootKey(rootKey.copyOf())
        keySink.storePendingRecoveryKey(recoveryKey.copyOf())
        val encodedRecoveryKey = Base64.getUrlEncoder().withoutPadding().encodeToString(recoveryKey)
        rootKey.fill(0)
        recoveryKey.fill(0)
        return AccountRecoveryState.SaveRecoveryKey(encodedRecoveryKey)
    }

    fun restore(encodedRecoveryKey: String): AccountRecoveryState {
        val recoveryKey = try {
            Base64.getUrlDecoder().decode(encodedRecoveryKey)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Curfew Recovery Key is not valid base64url", error)
        }.requireSize(ROOT_KEY_BYTES)
        val envelope = requireNotNull(transport.getRecoveryEnvelope()) {
            "account does not have a recovery envelope"
        }
        return try {
            val rootKey = AccountRootKeyRecovery.openRecoveryEnvelope(envelope, recoveryKey)
            keySink.storeRootKey(rootKey.copyOf())
            rootKey.fill(0)
            AccountRecoveryState.Ready
        } finally {
            recoveryKey.fill(0)
        }
    }

    fun acknowledgeSavedRecoveryKey() {
        keySink.clearPendingRecoveryKey()
    }

    private fun ByteArray.requireRootKey(): ByteArray = requireSize(ROOT_KEY_BYTES)

    private fun ByteArray.requireSize(expected: Int): ByteArray {
        require(size == expected)
        return this
    }

    private companion object {
        const val INITIAL_KEY_EPOCH = 1L
        const val ROOT_KEY_BYTES = 32
        const val RECOVERY_SALT_BYTES = 16
        const val GCM_NONCE_BYTES = 12

        fun secureRandomBytes(size: Int): ByteArray =
            ByteArray(size).also(SecureRandom()::nextBytes)
    }
}
