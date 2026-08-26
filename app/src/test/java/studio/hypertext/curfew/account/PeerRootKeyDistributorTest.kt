package studio.hypertext.curfew.account

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import studio.hypertext.curfew.protocols.AccountDeviceEnrollment
import studio.hypertext.curfew.protocols.CipherSuite
import studio.hypertext.curfew.protocols.Crv
import studio.hypertext.curfew.protocols.AccountPublicKeyJWK
import studio.hypertext.curfew.protocols.Kdf
import studio.hypertext.curfew.protocols.Kem
import studio.hypertext.curfew.protocols.Kty
import studio.hypertext.curfew.protocols.RootKeyEnvelope
import studio.hypertext.curfew.protocols.RootKeyEnvelopeInfo

class PeerRootKeyDistributorTest {
    @Test
    fun wrapsTheRootForEveryOtherEnrolledDevice() {
        val rootKey = ByteArray(32) { it.toByte() }
        val transport = FakeTransport(listOf(enrollment(OWN_DEVICE), enrollment(PEER_DEVICE)))
        var sealedRoot: ByteArray? = null
        val distributor = PeerRootKeyDistributor(
            transport = transport,
            rootKeyProvider = { rootKey.copyOf() },
            envelopeSealer = { root, peer, createdAt ->
                sealedRoot = root.copyOf()
                envelope(peer.deviceId, peer.keyEpoch, createdAt)
            },
            now = { CREATED_AT },
        )

        assertEquals(1, distributor.distribute())
        assertArrayEquals(rootKey, sealedRoot)
        assertEquals(listOf(PEER_DEVICE), transport.uploaded.map { it.recipientDeviceId })
    }

    @Test
    fun doesNothingBeforeTheAccountRootExists() {
        val transport = FakeTransport(listOf(enrollment(PEER_DEVICE)))
        val distributor = PeerRootKeyDistributor(
            transport = transport,
            rootKeyProvider = { null },
        )

        assertEquals(0, distributor.distribute())
        assertEquals(emptyList<RootKeyEnvelope>(), transport.uploaded)
    }

    private class FakeTransport(
        private val devices: List<AccountDeviceEnrollment>,
    ) : PeerRootEnvelopeTransport {
        val uploaded = mutableListOf<RootKeyEnvelope>()

        override fun currentDeviceId(): String = OWN_DEVICE

        override fun listDevices(): List<AccountDeviceEnrollment> = devices

        override fun putRootEnvelope(envelope: RootKeyEnvelope) {
            uploaded += envelope
        }
    }

    private fun enrollment(deviceId: String) = AccountDeviceEnrollment(
        deviceId = deviceId,
        encryptionPublicKeyJwk = publicKey(),
        signingPublicKeyJwk = publicKey(),
        keyEpoch = 1,
        enrolledAt = CREATED_AT,
        protocolVersion = "0.3",
    )

    private fun publicKey() = AccountPublicKeyJWK(
        crv = Crv.P256,
        kty = Kty.Ec,
        x = "A".repeat(43),
        y = "B".repeat(43),
    )

    private fun envelope(deviceId: String, keyEpoch: Long, createdAt: String) = RootKeyEnvelope(
        aead = CipherSuite.AES256Gcm,
        ciphertext = "C".repeat(64),
        createdAt = createdAt,
        encapsulatedKey = "E".repeat(87),
        info = RootKeyEnvelopeInfo.CurfewRootKeyEnvelopeV2,
        kdf = Kdf.HkdfSha256,
        kem = Kem.DhkemP256HkdfSha256,
        keyEpoch = keyEpoch,
        recipientDeviceId = deviceId,
    )

    private companion object {
        const val OWN_DEVICE = "10000000-0000-4000-8000-000000000001"
        const val PEER_DEVICE = "10000000-0000-4000-8000-000000000002"
        const val CREATED_AT = "2026-08-10T14:00:00Z"
    }
}
