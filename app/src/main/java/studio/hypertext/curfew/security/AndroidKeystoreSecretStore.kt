package studio.hypertext.curfew.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreSecretStore(
    context: Context,
    private val keyAlias: String = "curfew-device-secrets-v1",
) {
    private val directory = File(context.noBackupFilesDir, "secure-secrets").also {
        check(it.mkdirs() || it.isDirectory) { "secure secret storage is unavailable" }
    }

    @Synchronized
    fun put(identifier: String, secret: ByteArray) {
        require(identifier.isNotBlank())
        require(secret.isNotEmpty())
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(secret)
        val payload = ByteArray(1 + cipher.iv.size + ciphertext.size)
        payload[0] = cipher.iv.size.toByte()
        cipher.iv.copyInto(payload, 1)
        ciphertext.copyInto(payload, 1 + cipher.iv.size)
        val destination = fileFor(identifier)
        val temporary = File(directory, "${destination.name}.new")
        temporary.outputStream().use { stream ->
            stream.write(payload)
            stream.fd.sync()
        }
        check(temporary.renameTo(destination)) { "could not persist encrypted secret" }
    }

    @Synchronized
    fun get(identifier: String): ByteArray? {
        val source = fileFor(identifier)
        if (!source.exists()) return null
        val payload = source.readBytes()
        require(payload.isNotEmpty()) { "encrypted secret is malformed" }
        val ivSize = payload[0].toInt() and 0xff
        require(ivSize in 12..16 && payload.size > 1 + ivSize) {
            "encrypted secret is malformed"
        }
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            requireNotNull(keyStore().getKey(keyAlias, null) as? SecretKey),
            GCMParameterSpec(128, payload.copyOfRange(1, 1 + ivSize)),
        )
        return cipher.doFinal(payload.copyOfRange(1 + ivSize, payload.size))
    }

    @Synchronized
    fun delete(identifier: String) {
        val file = fileFor(identifier)
        if (file.exists()) check(file.delete()) { "could not delete encrypted secret" }
    }

    private fun fileFor(identifier: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identifier.toByteArray(Charsets.UTF_8))
        val name = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        return File(directory, "$name.secret")
    }

    private fun getOrCreateKey(): SecretKey {
        val existing = keyStore().getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private companion object {
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
