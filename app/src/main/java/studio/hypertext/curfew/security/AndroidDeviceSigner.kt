package studio.hypertext.curfew.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.interfaces.ECPublicKey
import studio.hypertext.curfew.protocols.Crv
import studio.hypertext.curfew.protocols.DevicePublicKeyJWK
import studio.hypertext.curfew.protocols.Kty

class AndroidDeviceSigner(
    private val keyAlias: String = "curfew-device-signing-v2",
) : RecordSigner {
    override fun sign(canonicalInput: ByteArray): ByteArray {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey())
        signature.update(canonicalInput)
        return derToLowSP1363(signature.sign())
    }

    fun publicKeyEncoded(): ByteArray = keyStore().getCertificate(keyAlias)?.publicKey?.encoded
        ?: run {
            privateKey()
            requireNotNull(keyStore().getCertificate(keyAlias)).publicKey.encoded
        }

    fun publicJwk(): DevicePublicKeyJWK {
        privateKey()
        val publicKey = requireNotNull(keyStore().getCertificate(keyAlias)).publicKey as ECPublicKey
        return DevicePublicKeyJWK(
            crv = Crv.P256,
            kty = Kty.Ec,
            x = base64Url(unsignedFixed(publicKey.w.affineX, 32)),
            y = base64Url(unsignedFixed(publicKey.w.affineY, 32)),
        )
    }

    private fun privateKey(): java.security.PrivateKey {
        val store = keyStore()
        (store.getKey(keyAlias, null) as? java.security.PrivateKey)?.let { return it }
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").run {
            initialize(
                KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build(),
            )
            generateKeyPair()
        }
        return requireNotNull(keyStore().getKey(keyAlias, null) as? java.security.PrivateKey)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }
}

class AndroidDeviceEncryptionKey(
    private val keyAlias: String = "curfew-device-encryption-v2",
) {
    fun publicJwk(): DevicePublicKeyJWK {
        privateKey()
        val publicKey = requireNotNull(keyStore().getCertificate(keyAlias)).publicKey as ECPublicKey
        return DevicePublicKeyJWK(
            crv = Crv.P256,
            kty = Kty.Ec,
            x = base64Url(unsignedFixed(publicKey.w.affineX, 32)),
            y = base64Url(unsignedFixed(publicKey.w.affineY, 32)),
        )
    }

    private fun privateKey(): java.security.PrivateKey {
        val store = keyStore()
        (store.getKey(keyAlias, null) as? java.security.PrivateKey)?.let { return it }
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").run {
            initialize(
                KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_AGREE_KEY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .build(),
            )
            generateKeyPair()
        }
        return requireNotNull(keyStore().getKey(keyAlias, null) as? java.security.PrivateKey)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}

internal fun derToLowSP1363(der: ByteArray): ByteArray {
    var index = 0
    require(der[index++].toInt() == 0x30)
    index += derLengthByteCount(der, index).also { count ->
        if (count > 1) index += count - 1
    }
    require(der[index++].toInt() == 0x02)
    val rLength = der[index++].toInt() and 0xff
    val r = BigInteger(1, der.copyOfRange(index, index + rLength))
    index += rLength
    require(der[index++].toInt() == 0x02)
    val sLength = der[index++].toInt() and 0xff
    var s = BigInteger(1, der.copyOfRange(index, index + sLength))
    if (s > P256_ORDER.shiftRight(1)) s = P256_ORDER.subtract(s)
    return unsignedFixed(r, 32) + unsignedFixed(s, 32)
}

private fun derLengthByteCount(der: ByteArray, index: Int): Int {
    val first = der[index].toInt() and 0xff
    return if (first and 0x80 == 0) 1 else 1 + (first and 0x7f)
}

private fun unsignedFixed(value: BigInteger, size: Int): ByteArray {
    val bytes = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
    require(bytes.size <= size)
    return ByteArray(size - bytes.size) + bytes
}

private val P256_ORDER = BigInteger(
    "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551",
    16,
)
