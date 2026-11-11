package studio.hypertext.curfew.security

/**
 * Contract for secure key-value storage.
 * Decouples consumers from platform keystore implementations.
 */
interface SecretStore {
    fun put(identifier: String, secret: ByteArray)
    fun get(identifier: String): ByteArray?
    fun getOrPut(identifier: String, create: () -> ByteArray): ByteArray
    fun delete(identifier: String)
}
