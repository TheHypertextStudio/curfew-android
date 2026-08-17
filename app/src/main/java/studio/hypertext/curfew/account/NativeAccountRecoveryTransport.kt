package studio.hypertext.curfew.account

import android.content.Context
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import studio.hypertext.curfew.protocols.RecoveryKeyEnvelope
import studio.hypertext.curfew.protocols.RootKeyEnvelope
import studio.hypertext.curfew.security.AndroidKeystoreSecretStore

class NativeAccountRecoveryTransport(context: Context) : AccountRecoveryTransport {
    private val secrets = AndroidKeystoreSecretStore(context)
    private val authenticator = NativeDeviceProofAuthenticator(context)
    private val json = Json { ignoreUnknownKeys = false }

    override fun getRootEnvelope(): RootKeyEnvelope? {
        val deviceId = authenticator.deviceId()
        return get(
            URI("$SYNC_ORIGIN/sync/devices/$deviceId/root-key-envelope"),
            deviceId,
        )
    }

    override fun getRecoveryEnvelope(): RecoveryKeyEnvelope? = get(
        URI("$SYNC_ORIGIN/sync/e2ee/recovery-envelope"),
        authenticator.deviceId(),
    )

    override fun putRecoveryEnvelope(envelope: RecoveryKeyEnvelope): Boolean {
        val endpoint = URI("$SYNC_ORIGIN/sync/e2ee/recovery-envelope")
        val body = json.encodeToString(envelope)
        val connection = openAuthenticated(endpoint, "PUT", body)
        return try {
            when (val status = connection.responseCode) {
                in 200..299 -> true
                409 -> false
                in 300..399 -> error("recovery envelope redirects are rejected")
                else -> error("recovery envelope upload returned HTTP $status")
            }
        } finally {
            connection.disconnect()
        }
    }

    private inline fun <reified T> get(endpoint: URI, deviceId: String): T? {
        val connection = openAuthenticated(endpoint, "GET", null, deviceId)
        return try {
            when (val status = connection.responseCode) {
                404 -> null
                in 200..299 -> {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    require(response.length <= MAX_RESPONSE_BYTES)
                    json.decodeFromString(response)
                }
                in 300..399 -> error("encrypted-key recovery redirects are rejected")
                else -> error("encrypted-key recovery returned HTTP $status")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openAuthenticated(
        endpoint: URI,
        method: String,
        body: String?,
        deviceId: String = authenticator.deviceId(),
    ): HttpURLConnection {
        val accessToken = requireNotNull(secrets.get(ACCESS_TOKEN_KEY)) {
            "account access token is missing"
        }.toString(Charsets.UTF_8)
        val proof = authenticator.proof(
            accessToken = accessToken,
            method = method,
            canonicalUrl = endpoint.toASCIIString(),
            jsonBody = body,
        )
        return (endpoint.toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = method
            connectTimeout = 5_000
            readTimeout = 5_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("DPoP", proof)
            setRequestProperty("x-curfew-device-id", deviceId)
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(body.toByteArray()) }
            }
        }
    }

    private companion object {
        const val SYNC_ORIGIN = "https://curfew-sync.hypertext.studio"
        const val ACCESS_TOKEN_KEY = "account:access-token"
        const val MAX_RESPONSE_BYTES = 32 * 1024
    }
}
