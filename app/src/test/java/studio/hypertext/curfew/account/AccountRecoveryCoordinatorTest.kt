package studio.hypertext.curfew.account

import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import studio.hypertext.curfew.protocols.CipherSuite
import studio.hypertext.curfew.protocols.Kdf
import studio.hypertext.curfew.protocols.Kem
import studio.hypertext.curfew.protocols.RecoveryEnvelopeInfo
import studio.hypertext.curfew.protocols.RecoveryKeyEnvelope
import studio.hypertext.curfew.protocols.RootKeyEnvelope
import studio.hypertext.curfew.protocols.RootKeyEnvelopeInfo
import studio.hypertext.curfew.security.AccountRootKeyRecovery

class AccountRecoveryCoordinatorTest {
    @Test
    fun peerEnvelopeCompletesEnrollmentWithoutRequestingRecoveryKey() {
        val rootKey = ByteArray(32) { it.toByte() }
        val sink = FakeSink()
        val coordinator = AccountRecoveryCoordinator(
            transport = FakeTransport(rootEnvelope = rootEnvelope()),
            keySink = sink,
            rootEnvelopeOpener = { rootKey },
        )

        assertEquals(AccountRecoveryState.Ready, coordinator.resumeAfterEnrollment())
        assertArrayEquals(rootKey, sink.rootKey)
        assertEquals(null, sink.pendingRecoveryKey)
    }

    @Test
    fun existingRecoveryEnvelopeRequiresTheSeparateRecoveryKey() {
        val sink = FakeSink()
        val transport = FakeTransport(recoveryEnvelope = recoveryEnvelope())
        val coordinator = AccountRecoveryCoordinator(
            transport = transport,
            keySink = sink,
            rootEnvelopeOpener = { error("no peer envelope") },
        )

        assertEquals(AccountRecoveryState.EnterRecoveryKey, coordinator.resumeAfterEnrollment())
        assertEquals(null, sink.rootKey)
    }

    @Test
    fun firstDeviceCreatesAndDisplaysASeparateRecoveryKey() {
        val rootKey = ByteArray(32) { (0x20 + it).toByte() }
        val recoveryKey = ByteArray(32) { (0x40 + it).toByte() }
        val salt = ByteArray(16) { (0x60 + it).toByte() }
        val nonce = ByteArray(12) { (0x70 + it).toByte() }
        val values = ArrayDeque(listOf(rootKey, recoveryKey, salt, nonce))
        val sink = FakeSink()
        val transport = FakeTransport()
        val coordinator = AccountRecoveryCoordinator(
            transport = transport,
            keySink = sink,
            rootEnvelopeOpener = { error("no peer envelope") },
            randomBytes = { size -> values.removeFirst().also { require(it.size == size) } },
            now = { "2026-08-10T14:00:00Z" },
        )

        val state = coordinator.resumeAfterEnrollment()

        assertEquals(
            AccountRecoveryState.SaveRecoveryKey(encode(recoveryKey)),
            state,
        )
        assertArrayEquals(rootKey, sink.rootKey)
        assertArrayEquals(recoveryKey, sink.pendingRecoveryKey)
        val uploaded = requireNotNull(transport.uploadedRecoveryEnvelope)
        assertArrayEquals(
            rootKey,
            AccountRootKeyRecovery.openRecoveryEnvelope(uploaded, recoveryKey),
        )
    }

    @Test
    fun recoveryKeyRestoresTheRootKeyWithoutUploadingIt() {
        val rootKey = ByteArray(32) { (0x20 + it).toByte() }
        val recoveryKey = ByteArray(32) { (0x40 + it).toByte() }
        val envelope = AccountRootKeyRecovery.createRecoveryEnvelope(
            rootKey = rootKey,
            recoveryKey = recoveryKey,
            keyEpoch = 1,
            createdAt = "2026-08-10T14:00:00Z",
            salt = ByteArray(16) { (0x60 + it).toByte() },
            nonce = ByteArray(12) { (0x70 + it).toByte() },
        )
        val sink = FakeSink()
        val coordinator = AccountRecoveryCoordinator(
            transport = FakeTransport(recoveryEnvelope = envelope),
            keySink = sink,
            rootEnvelopeOpener = { error("no peer envelope") },
        )

        assertEquals(AccountRecoveryState.Ready, coordinator.restore(encode(recoveryKey)))
        assertArrayEquals(rootKey, sink.rootKey)
        assertEquals(null, sink.pendingRecoveryKey)
    }

    @Test(expected = IllegalArgumentException::class)
    fun recoveryRejectsKeysThatAreNotExactly256Bits() {
        val coordinator = AccountRecoveryCoordinator(
            transport = FakeTransport(recoveryEnvelope = recoveryEnvelope()),
            keySink = FakeSink(),
            rootEnvelopeOpener = { error("no peer envelope") },
        )

        coordinator.restore(encode(ByteArray(31)))
    }

    @Test
    fun acknowledgementDeletesTheTemporaryRecoveryKey() {
        val sink = FakeSink().apply { pendingRecoveryKey = ByteArray(32) }
        val coordinator = AccountRecoveryCoordinator(
            transport = FakeTransport(),
            keySink = sink,
            rootEnvelopeOpener = { error("no peer envelope") },
        )

        coordinator.acknowledgeSavedRecoveryKey()

        assertEquals(null, sink.pendingRecoveryKey)
    }

    private class FakeTransport(
        private val rootEnvelope: RootKeyEnvelope? = null,
        private val recoveryEnvelope: RecoveryKeyEnvelope? = null,
    ) : AccountRecoveryTransport {
        var uploadedRecoveryEnvelope: RecoveryKeyEnvelope? = null

        override fun getRootEnvelope(): RootKeyEnvelope? = rootEnvelope

        override fun getRecoveryEnvelope(): RecoveryKeyEnvelope? = recoveryEnvelope

        override fun putRecoveryEnvelope(envelope: RecoveryKeyEnvelope): Boolean {
            uploadedRecoveryEnvelope = envelope
            return true
        }
    }

    private class FakeSink : AccountRootKeySink {
        var rootKey: ByteArray? = null
        var pendingRecoveryKey: ByteArray? = null

        override fun storeRootKey(value: ByteArray) {
            rootKey = value
        }

        override fun storePendingRecoveryKey(value: ByteArray) {
            pendingRecoveryKey = value
        }

        override fun clearPendingRecoveryKey() {
            pendingRecoveryKey = null
        }
    }

    private fun rootEnvelope() = RootKeyEnvelope(
        aead = CipherSuite.AES256Gcm,
        ciphertext = "C".repeat(64),
        createdAt = "2026-08-10T14:00:00Z",
        encapsulatedKey = "E".repeat(87),
        info = RootKeyEnvelopeInfo.CurfewRootKeyEnvelopeV2,
        kdf = Kdf.HkdfSha256,
        kem = Kem.DhkemP256HkdfSha256,
        keyEpoch = 1,
        recipientDeviceId = "018f4f45-a055-7502-8b0c-7276bfe16c8f",
    )

    private fun recoveryEnvelope() = RecoveryKeyEnvelope(
        aead = CipherSuite.AES256Gcm,
        ciphertext = "C".repeat(64),
        createdAt = "2026-08-10T14:00:00Z",
        info = RecoveryEnvelopeInfo.CurfewRecoveryWrapV2,
        kdf = Kdf.HkdfSha256,
        keyEpoch = 1,
        nonce = "N".repeat(16),
        salt = "S".repeat(22),
    )

    private fun encode(value: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value)
}
