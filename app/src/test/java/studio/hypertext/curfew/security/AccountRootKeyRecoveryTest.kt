package studio.hypertext.curfew.security

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPrivateKeySpec
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import studio.hypertext.curfew.protocols.CipherSuite
import studio.hypertext.curfew.protocols.Kdf
import studio.hypertext.curfew.protocols.Kem
import studio.hypertext.curfew.protocols.RecoveryEnvelopeInfo
import studio.hypertext.curfew.protocols.RecoveryKeyEnvelope
import studio.hypertext.curfew.protocols.RootKeyEnvelope
import studio.hypertext.curfew.protocols.RootKeyEnvelopeInfo

class AccountRootKeyRecoveryTest {
    private val expectedRootKey = decode("ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8")

    @Test
    fun opensProtocolHpkeRootEnvelope() {
        val parameters = AlgorithmParameters.getInstance("EC").run {
            init(ECGenParameterSpec("secp256r1"))
            getParameterSpec(ECParameterSpec::class.java)
        }
        val privateKey = KeyFactory.getInstance("EC").generatePrivate(
            ECPrivateKeySpec(BigInteger(1, decode("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAI")), parameters),
        )
        val envelope = RootKeyEnvelope(
            aead = CipherSuite.AES256Gcm,
            ciphertext = "zR1Y5Z8iG09FOfMi_3cmndteDPYsKv7_03STWS4C8yvY5ZGpGoYkTqHTjy-lXrpD",
            createdAt = "2026-08-10T14:00:00Z",
            encapsulatedKey = "BF7L5NGmMwpEyPfvlR1L8WXmxrch762phftBZhvG5_1shzRkDEmY_343SwbOGmSi7NgqsDY4T7g9mnmxJ6J9UDI",
            info = RootKeyEnvelopeInfo.CurfewRootKeyEnvelopeV2,
            kdf = Kdf.HkdfSha256,
            kem = Kem.DhkemP256HkdfSha256,
            keyEpoch = 1,
            recipientDeviceId = "018f4f45-a055-7502-8b0c-7276bfe16c8f",
        )

        val opened = AccountRootKeyRecovery.openRootEnvelope(
            envelope = envelope,
            recipientPrivateKey = privateKey,
            recipientPublicKey = decode(
                "BHzyexiNA09-ilI4AwS1GsPAiWnid_IbNaYLSPxHZpl4B3dVENuO0EApPZrGn3Qw27p9reY86YIpngS3nSJ4c9E",
            ),
        )

        assertArrayEquals(expectedRootKey, opened)
    }

    @Test
    fun opensProtocolRecoveryEnvelopeOnlyWithSeparateRecoveryKey() {
        val envelope = RecoveryKeyEnvelope(
            aead = CipherSuite.AES256Gcm,
            ciphertext = "N6oVk0-PT81sLWLarss4ZZHylio7YefIplqMkP6SOaCY2lhGAJNpCgA4VM6PfX12",
            createdAt = "2026-08-10T14:00:00Z",
            info = RecoveryEnvelopeInfo.CurfewRecoveryWrapV2,
            kdf = Kdf.HkdfSha256,
            keyEpoch = 1,
            nonce = "wMHCw8TFxsfIycrL",
            salt = "cHFyc3R1dnd4eXp7fH1-fw",
        )

        val opened = AccountRootKeyRecovery.openRecoveryEnvelope(
            envelope,
            decode("QEFCQ0RFRkdISUpLTE1OT1BRUlNUVVZXWFlaW1xdXl8"),
        )

        assertArrayEquals(expectedRootKey, opened)
    }

    @Test
    fun createsProtocolRecoveryEnvelopeForFirstDeviceBootstrap() {
        val envelope = AccountRootKeyRecovery.createRecoveryEnvelope(
            rootKey = expectedRootKey,
            recoveryKey = decode("QEFCQ0RFRkdISUpLTE1OT1BRUlNUVVZXWFlaW1xdXl8"),
            keyEpoch = 1,
            createdAt = "2026-08-10T14:00:00Z",
            salt = decode("cHFyc3R1dnd4eXp7fH1-fw"),
            nonce = decode("wMHCw8TFxsfIycrL"),
        )

        assertArrayEquals(
            decode("N6oVk0-PT81sLWLarss4ZZHylio7YefIplqMkP6SOaCY2lhGAJNpCgA4VM6PfX12"),
            decode(envelope.ciphertext),
        )
        assertArrayEquals(expectedRootKey, AccountRootKeyRecovery.openRecoveryEnvelope(
            envelope,
            decode("QEFCQ0RFRkdISUpLTE1OT1BRUlNUVVZXWFlaW1xdXl8"),
        ))
    }

    private fun decode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)
}
