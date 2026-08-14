package studio.hypertext.curfew.security

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import studio.hypertext.curfew.protocols.CipherSuite
import studio.hypertext.curfew.protocols.EncryptedRecord
import studio.hypertext.curfew.protocols.EncryptedRecordNamespace
import studio.hypertext.curfew.protocols.SignatureAlgorithm

fun interface RecordSigner {
    /** Returns a 64-byte IEEE P1363 r || s ES256 signature. */
    fun sign(canonicalInput: ByteArray): ByteArray
}

class EncryptedRecordSealer(private val signer: RecordSigner) {
    fun seal(
        plaintextCanonical: ByteArray,
        rootKey: ByteArray,
        namespace: EncryptedRecordNamespace,
        keyEpoch: Long,
        recordId: String,
        updatedAt: Instant,
        version: Long,
        writerCounter: Long,
        writerDeviceId: String,
        nonce: ByteArray = ByteArray(12).also(SecureRandom()::nextBytes),
    ): EncryptedRecord {
        require(rootKey.size == 32) { "account root key must contain 256 bits" }
        require(nonce.size == 12) { "AES-GCM nonce must contain 96 bits" }
        require(keyEpoch > 0)
        require(version > 0)
        require(writerCounter > 0)
        require(recordId.isNotBlank())
        require(writerDeviceId.isNotBlank())

        val aad = canonicalJson(
            "cipherSuite" to CipherSuite.AES256Gcm.value,
            "keyEpoch" to keyEpoch,
            "namespace" to namespace.value,
            "recordId" to recordId,
            "updatedAt" to updatedAt.toString(),
            "version" to version,
            "writerCounter" to writerCounter,
            "writerDeviceId" to writerDeviceId,
        ).toByteArray(StandardCharsets.UTF_8)
        val aadDigest = base64Url(MessageDigest.getInstance("SHA-256").digest(aad))
        val namespaceKey = hkdfSha256(
            inputKeyMaterial = rootKey,
            salt = "curfew-encrypted-record-v2".toByteArray(StandardCharsets.UTF_8),
            info = "namespace=${namespace.value};keyEpoch=$keyEpoch"
                .toByteArray(StandardCharsets.UTF_8),
            length = 32,
        )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(namespaceKey, "AES"),
            GCMParameterSpec(128, nonce),
        )
        cipher.updateAAD(aad)
        val ciphertext = base64Url(cipher.doFinal(plaintextCanonical))
        val signatureInput = canonicalJson(
            "aadDigest" to aadDigest,
            "cipherSuite" to CipherSuite.AES256Gcm.value,
            "ciphertext" to ciphertext,
            "keyEpoch" to keyEpoch,
            "namespace" to namespace.value,
            "nonce" to base64Url(nonce),
            "recordId" to recordId,
            "signatureAlgorithm" to SignatureAlgorithm.Es256P1363Sha256.value,
            "updatedAt" to updatedAt.toString(),
            "version" to version,
            "writerCounter" to writerCounter,
            "writerDeviceId" to writerDeviceId,
        ).toByteArray(StandardCharsets.UTF_8)
        val signature = signer.sign(signatureInput)
        require(signature.size == 64) { "ES256 signature must use 64-byte P1363 encoding" }
        return EncryptedRecord(
            aadDigest = aadDigest,
            cipherSuite = CipherSuite.AES256Gcm,
            ciphertext = ciphertext,
            keyEpoch = keyEpoch,
            namespace = namespace,
            nonce = base64Url(nonce),
            recordId = recordId,
            signature = base64Url(signature),
            signatureAlgorithm = SignatureAlgorithm.Es256P1363Sha256,
            updatedAt = updatedAt.toString(),
            version = version,
            writerCounter = writerCounter,
            writerDeviceId = writerDeviceId,
        )
    }
}

internal fun hkdfSha256(
    inputKeyMaterial: ByteArray,
    salt: ByteArray,
    info: ByteArray,
    length: Int,
): ByteArray {
    require(length in 1..(255 * 32))
    val extract = Mac.getInstance("HmacSHA256")
    extract.init(SecretKeySpec(salt, "HmacSHA256"))
    val pseudoRandomKey = extract.doFinal(inputKeyMaterial)
    val result = ByteArray(length)
    var previous = ByteArray(0)
    var offset = 0
    var counter = 1
    while (offset < length) {
        val expand = Mac.getInstance("HmacSHA256")
        expand.init(SecretKeySpec(pseudoRandomKey, "HmacSHA256"))
        expand.update(previous)
        expand.update(info)
        expand.update(counter.toByte())
        previous = expand.doFinal()
        val copied = minOf(previous.size, length - offset)
        previous.copyInto(result, offset, 0, copied)
        offset += copied
        counter += 1
    }
    return result
}

internal fun canonicalJson(vararg fields: Pair<String, Any>): String =
    fields.sortedBy { it.first }.joinToString(",", "{", "}") { (key, value) ->
        val encodedValue = when (value) {
            is String -> quoteJson(value)
            is Long -> value.toString()
            is Int -> value.toString()
            is Boolean -> value.toString()
            else -> error("unsupported canonical JSON value")
        }
        "${quoteJson(key)}:$encodedValue"
    }

internal fun quoteJson(value: String): String = buildString {
    append('"')
    value.forEach { character ->
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000c' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character.code < 0x20) {
                append("\\u%04x".format(character.code))
            } else {
                append(character)
            }
        }
    }
    append('"')
}

internal fun base64Url(value: ByteArray): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(value)
