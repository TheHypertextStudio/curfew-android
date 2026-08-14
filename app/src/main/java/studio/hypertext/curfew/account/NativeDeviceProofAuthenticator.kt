package studio.hypertext.curfew.account

import android.content.Context
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import studio.hypertext.curfew.security.AndroidDeviceSigner
import studio.hypertext.curfew.security.AndroidKeystoreSecretStore
import studio.hypertext.curfew.security.NativeDeviceProofFactory

class NativeDeviceProofAuthenticator(context: Context) {
    private val secrets = AndroidKeystoreSecretStore(context)
    private val signer = AndroidDeviceSigner()

    fun deviceId(): String {
        secrets.get(DEVICE_ID_KEY)?.toString(Charsets.UTF_8)?.let { return it }
        return UUID.randomUUID().toString().also { secrets.put(DEVICE_ID_KEY, it.toByteArray()) }
    }

    fun proof(
        accessToken: String,
        method: String,
        canonicalUrl: String,
        jsonBody: String? = null,
    ): String = NativeDeviceProofFactory(signer).create(
        accessToken = accessToken,
        nonce = challenge(accessToken),
        method = method,
        canonicalUrl = canonicalUrl,
        jsonBody = jsonBody,
    )

    fun challenge(accessToken: String): String {
        val endpoint = URI("https://curfew-sync.hypertext.studio/sync/device-proof/challenge")
        val connection = endpoint.toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use {
                it.write(buildJsonObject { put("deviceId", deviceId()) }.toString().toByteArray())
            }
            val status = connection.responseCode
            check(status !in 300..399) { "device proof challenge redirects are rejected" }
            check(status in 200..299) { "device proof challenge returned HTTP $status" }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            require(response.length <= 8 * 1024)
            return Json.parseToJsonElement(response).jsonObject.getValue("nonce")
                .jsonPrimitive.content
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val DEVICE_ID_KEY = "account:device-id"
    }
}
