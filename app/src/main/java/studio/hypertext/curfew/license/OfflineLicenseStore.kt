package studio.hypertext.curfew.license

import android.content.Context
import studio.hypertext.curfew.security.AndroidKeystoreSecretStore

fun interface OfflineLicenseSignatureVerifier {
    fun verify(signedEnvelope: ByteArray): Boolean
}

class OfflineLicenseStore(context: Context) {
    private val secrets = AndroidKeystoreSecretStore(context)

    fun install(
        licenseId: String,
        signedEnvelope: ByteArray,
        verifier: OfflineLicenseSignatureVerifier,
    ) {
        require(licenseId.isNotBlank())
        require(signedEnvelope.isNotEmpty() && signedEnvelope.size <= 64 * 1024)
        require(verifier.verify(signedEnvelope)) { "offline license signature is invalid" }
        secrets.put("license:$licenseId", signedEnvelope)
    }

    fun load(licenseId: String): ByteArray? = secrets.get("license:$licenseId")

    fun remove(licenseId: String) = secrets.delete("license:$licenseId")
}
