package studio.hypertext.curfew.account

import java.time.Instant
import studio.hypertext.curfew.protocols.AccountDeviceEnrollment
import studio.hypertext.curfew.protocols.RootKeyEnvelope
import studio.hypertext.curfew.security.AccountRootKeyRecovery

interface PeerRootEnvelopeTransport {
    fun currentDeviceId(): String

    fun listDevices(): List<AccountDeviceEnrollment>

    fun putRootEnvelope(envelope: RootKeyEnvelope)
}

class PeerRootKeyDistributor(
    private val transport: PeerRootEnvelopeTransport,
    private val rootKeyProvider: () -> ByteArray?,
    private val envelopeSealer: (ByteArray, AccountDeviceEnrollment, String) -> RootKeyEnvelope =
        { rootKey, peer, createdAt ->
            AccountRootKeyRecovery.createRootEnvelope(
                rootKey = rootKey,
                recipientDeviceId = peer.deviceId,
                recipientPublicKey = peer.encryptionPublicKeyJwk,
                keyEpoch = peer.keyEpoch,
                createdAt = createdAt,
            )
        },
    private val now: () -> String = { Instant.now().toString() },
) {
    fun distribute(): Int {
        val rootKey = rootKeyProvider() ?: return 0
        require(rootKey.size == 32) { "account root key must contain 256 bits" }
        return try {
            val currentDeviceId = transport.currentDeviceId()
            transport.listDevices()
                .asSequence()
                .filter { it.deviceId != currentDeviceId }
                .onEach { peer ->
                    transport.putRootEnvelope(envelopeSealer(rootKey, peer, now()))
                }
                .count()
        } finally {
            rootKey.fill(0)
        }
    }
}
