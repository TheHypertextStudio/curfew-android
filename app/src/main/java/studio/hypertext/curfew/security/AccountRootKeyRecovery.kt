package studio.hypertext.curfew.security

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import studio.hypertext.curfew.protocols.RecoveryKeyEnvelope
import studio.hypertext.curfew.protocols.RootKeyEnvelope
import studio.hypertext.curfew.protocols.CipherSuite
import studio.hypertext.curfew.protocols.Kdf
import studio.hypertext.curfew.protocols.RecoveryEnvelopeInfo

object AccountRootKeyRecovery {
    fun createRecoveryEnvelope(
        rootKey: ByteArray,
        recoveryKey: ByteArray,
        keyEpoch: Long,
        createdAt: String,
        salt: ByteArray = randomBytes(16),
        nonce: ByteArray = randomBytes(GCM_NONCE_BYTES),
    ): RecoveryKeyEnvelope {
        require(rootKey.size == ROOT_KEY_BYTES) { "account root key must contain 256 bits" }
        require(recoveryKey.size == ROOT_KEY_BYTES) { "Curfew Recovery Key must contain 256 bits" }
        require(keyEpoch >= 1)
        require(salt.size == 16)
        require(nonce.size == GCM_NONCE_BYTES)
        val wrappingKey = hkdfExpand(
            hkdfExtract(salt, recoveryKey),
            "curfew-recovery-wrap-v2".bytes(),
            ROOT_KEY_BYTES,
        )
        val aad = canonicalJsonElement(
            buildJsonObject {
                put("createdAt", createdAt)
                put("keyEpoch", keyEpoch)
            },
        ).bytes()
        return RecoveryKeyEnvelope(
            aead = CipherSuite.AES256Gcm,
            ciphertext = encode(aesGcmSeal(wrappingKey, nonce, aad, rootKey)),
            createdAt = createdAt,
            info = RecoveryEnvelopeInfo.CurfewRecoveryWrapV2,
            kdf = Kdf.HkdfSha256,
            keyEpoch = keyEpoch,
            nonce = encode(nonce),
            salt = encode(salt),
        )
    }

    fun openRootEnvelope(
        envelope: RootKeyEnvelope,
        recipientPrivateKey: PrivateKey,
        recipientPublicKey: ByteArray,
    ): ByteArray {
        require(recipientPublicKey.size == P256_PUBLIC_KEY_BYTES)
        val encapsulatedKey = decode(envelope.encapsulatedKey)
        require(encapsulatedKey.size == P256_PUBLIC_KEY_BYTES)
        val dh = KeyAgreement.getInstance("ECDH").run {
            init(recipientPrivateKey)
            doPhase(ecPublicKey(encapsulatedKey), true)
            generateSecret()
        }
        val kemSuite = "KEM".bytes() + i2osp(P256_KEM_ID, 2)
        val hpkeSuite = "HPKE".bytes() +
            i2osp(P256_KEM_ID, 2) +
            i2osp(HKDF_SHA256_ID, 2) +
            i2osp(AES_256_GCM_ID, 2)
        val eaePrk = labeledExtract(kemSuite, byteArrayOf(), "eae_prk", dh)
        val sharedSecret = labeledExpand(
            kemSuite,
            eaePrk,
            "shared_secret",
            encapsulatedKey + recipientPublicKey,
            ROOT_KEY_BYTES,
        )
        val pskIdHash = labeledExtract(hpkeSuite, byteArrayOf(), "psk_id_hash", byteArrayOf())
        val infoHash = labeledExtract(hpkeSuite, byteArrayOf(), "info_hash", envelope.info.value.bytes())
        val context = byteArrayOf(HPKE_BASE_MODE) + pskIdHash + infoHash
        val secret = labeledExtract(hpkeSuite, sharedSecret, "secret", byteArrayOf())
        val key = labeledExpand(hpkeSuite, secret, "key", context, ROOT_KEY_BYTES)
        val nonce = labeledExpand(hpkeSuite, secret, "base_nonce", context, GCM_NONCE_BYTES)
        val aad = canonicalJsonElement(
            buildJsonObject {
                put("createdAt", envelope.createdAt)
                put("keyEpoch", envelope.keyEpoch)
                put("recipientDeviceId", envelope.recipientDeviceId)
            },
        ).bytes()
        return aesGcmOpen(key, nonce, aad, decode(envelope.ciphertext)).requireRootKey()
    }

    fun openRecoveryEnvelope(
        envelope: RecoveryKeyEnvelope,
        recoveryKey: ByteArray,
    ): ByteArray {
        require(recoveryKey.size == ROOT_KEY_BYTES) { "Curfew Recovery Key must contain 256 bits" }
        val salt = decode(envelope.salt)
        require(salt.size == 16)
        val nonce = decode(envelope.nonce)
        require(nonce.size == GCM_NONCE_BYTES)
        val wrappingKey = hkdfExpand(
            hkdfExtract(salt, recoveryKey),
            envelope.info.value.bytes(),
            ROOT_KEY_BYTES,
        )
        val aad = canonicalJsonElement(
            buildJsonObject {
                put("createdAt", envelope.createdAt)
                put("keyEpoch", envelope.keyEpoch)
            },
        ).bytes()
        return aesGcmOpen(wrappingKey, nonce, aad, decode(envelope.ciphertext)).requireRootKey()
    }

    private fun ByteArray.requireRootKey(): ByteArray {
        require(size == ROOT_KEY_BYTES) { "account root key must contain 256 bits" }
        return this
    }

    private fun aesGcmOpen(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        updateAAD(aad)
        doFinal(ciphertext)
    }

    private fun aesGcmSeal(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
    ): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        updateAAD(aad)
        doFinal(plaintext)
    }

    private fun labeledExtract(
        suite: ByteArray,
        salt: ByteArray,
        label: String,
        inputKeyMaterial: ByteArray,
    ): ByteArray = hkdfExtract(
        salt,
        "HPKE-v1".bytes() + suite + label.bytes() + inputKeyMaterial,
    )

    private fun labeledExpand(
        suite: ByteArray,
        pseudorandomKey: ByteArray,
        label: String,
        info: ByteArray,
        length: Int,
    ): ByteArray = hkdfExpand(
        pseudorandomKey,
        i2osp(length, 2) + "HPKE-v1".bytes() + suite + label.bytes() + info,
        length,
    )

    private fun hkdfExtract(salt: ByteArray, inputKeyMaterial: ByteArray): ByteArray =
        hmacSha256(if (salt.isEmpty()) ByteArray(32) else salt, inputKeyMaterial)

    private fun hkdfExpand(
        pseudorandomKey: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        var output = byteArrayOf()
        var previous = byteArrayOf()
        var counter = 1
        while (output.size < length) {
            previous = hmacSha256(
                pseudorandomKey,
                previous + info + byteArrayOf(counter.toByte()),
            )
            output += previous
            counter += 1
        }
        return output.copyOf(length)
    }

    private fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(message)
        }

    private fun ecPublicKey(raw: ByteArray): PublicKey {
        require(raw.size == P256_PUBLIC_KEY_BYTES && raw[0] == 4.toByte())
        val point = ECPoint(
            BigInteger(1, raw.copyOfRange(1, 33)),
            BigInteger(1, raw.copyOfRange(33, 65)),
        )
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, p256Parameters))
    }

    private fun i2osp(value: Int, length: Int): ByteArray =
        ByteArray(length) { index -> (value ushr (8 * (length - index - 1))).toByte() }

    private fun decode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)

    private fun encode(value: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value)

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(SecureRandom()::nextBytes)

    private fun String.bytes(): ByteArray = toByteArray(StandardCharsets.UTF_8)

    private val p256Parameters: ECParameterSpec by lazy {
        AlgorithmParameters.getInstance("EC").run {
            init(ECGenParameterSpec("secp256r1"))
            getParameterSpec(ECParameterSpec::class.java)
        }
    }

    private const val P256_KEM_ID = 0x10
    private const val HKDF_SHA256_ID = 1
    private const val AES_256_GCM_ID = 2
    private const val HPKE_BASE_MODE: Byte = 0
    private const val ROOT_KEY_BYTES = 32
    private const val GCM_NONCE_BYTES = 12
    private const val P256_PUBLIC_KEY_BYTES = 65
}
