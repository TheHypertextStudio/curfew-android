package studio.hypertext.curfew.security

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import studio.hypertext.curfew.protocols.DeviceProofClaims

class NativeDeviceProofFactory(
    private val signer: RecordSigner,
    private val now: () -> Instant = Instant::now,
    private val uuid: () -> String = { UUID.randomUUID().toString() },
) {
    fun create(
        accessToken: String,
        nonce: String,
        method: String,
        canonicalUrl: String,
        jsonBody: String? = null,
    ): String {
        require(accessToken.isNotBlank())
        require(nonce.matches(Regex("^[A-Za-z0-9_-]{22,86}$")))
        require(method.matches(Regex("^[A-Z]+$")))
        require(canonicalUrl.startsWith("https://curfew-sync.hypertext.studio/"))
        val claims = DeviceProofClaims(
            accessTokenHash = sha256Base64Url(accessToken.toByteArray(StandardCharsets.UTF_8)),
            bodyDigest = jsonBody?.let {
                sha256Base64Url(
                    canonicalJsonElement(Json.parseToJsonElement(it))
                        .toByteArray(StandardCharsets.UTF_8),
                )
            },
            canonicalUrl = canonicalUrl,
            httpMethod = method,
            issuedAt = now().toString(),
            jti = uuid(),
            nonce = nonce,
        )
        val header = base64Url(
            "{\"alg\":\"ES256\",\"typ\":\"curfew-device-proof+jws\"}"
                .toByteArray(StandardCharsets.UTF_8),
        )
        val payload = base64Url(Json.encodeToString(claims).toByteArray(StandardCharsets.UTF_8))
        val signingInput = "$header.$payload"
        val signature = signer.sign(signingInput.toByteArray(StandardCharsets.US_ASCII))
        require(signature.size == 64) { "device proof signature must use ES256 P1363" }
        return "$signingInput.${base64Url(signature)}"
    }
}
internal fun canonicalJsonElement(value: JsonElement): String = when (value) {
    JsonNull -> "null"
    is JsonPrimitive -> value.toString()
    is JsonArray -> value.joinToString(",", "[", "]", transform = ::canonicalJsonElement)
    is JsonObject -> value.entries.sortedBy { it.key }.joinToString(",", "{", "}") { (key, child) ->
        "${Json.encodeToString(key)}:${canonicalJsonElement(child)}"
    }
}

private fun sha256Base64Url(value: ByteArray): String =
    base64Url(MessageDigest.getInstance("SHA-256").digest(value))
