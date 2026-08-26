package studio.hypertext.curfew.account

import android.content.Context
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import studio.hypertext.curfew.protocols.DeviceEnrollmentRequest
import studio.hypertext.curfew.protocols.DeviceProof
import studio.hypertext.curfew.security.AndroidDeviceEncryptionKey
import studio.hypertext.curfew.security.AndroidDeviceSigner
import studio.hypertext.curfew.security.NativeDeviceProofFactory

class NativeDeviceEnrollmentClient(context: Context) {
    private val signingKey = AndroidDeviceSigner()
    private val encryptionKey = AndroidDeviceEncryptionKey()
    private val proofAuthenticator = NativeDeviceProofAuthenticator(context)

    fun enroll(accessToken: String, pkceChallenge: String, state: String) {
        val deviceId = proofAuthenticator.deviceId()
        val challenge = proofAuthenticator.challenge(accessToken)
        val enrolledAt = Instant.now().toString()
        val signingJwk = signingKey.publicJwk()
        val encryptionJwk = encryptionKey.publicJwk()
        val unsignedBody = buildJsonObject {
            put("coordinatorNonce", challenge)
            put("deviceId", deviceId)
            put("encryptionPublicKeyJwk", Json.parseToJsonElement(Json.encodeToString(encryptionJwk)))
            put("enrolledAt", enrolledAt)
            put("keyEpoch", 1)
            put("protocolVersion", "0.3")
            put("pkceChallenge", pkceChallenge)
            put("signingPublicKeyJwk", Json.parseToJsonElement(Json.encodeToString(signingJwk)))
            put("state", state)
        }.toString()
        val endpoint = URI("https://curfew-sync.hypertext.studio/sync/devices/enroll")
        val proof = NativeDeviceProofFactory(signingKey).create(
            accessToken = accessToken,
            nonce = challenge,
            method = "POST",
            canonicalUrl = endpoint.toASCIIString(),
            jsonBody = unsignedBody,
        )
        val request = DeviceEnrollmentRequest(
            coordinatorNonce = challenge,
            deviceId = deviceId,
            deviceProof = DeviceProof(proof),
            encryptionPublicKeyJwk = encryptionJwk,
            enrolledAt = enrolledAt,
            keyEpoch = 1,
            protocolVersion = "0.3",
            pkceChallenge = pkceChallenge,
            signingPublicKeyJwk = signingJwk,
            state = state,
        )
        val connection = endpoint.toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(Json.encodeToString(request).toByteArray()) }
            val status = connection.responseCode
            check(status !in 300..399) { "device enrollment redirects are rejected" }
            check(status in 200..299) { "device enrollment returned HTTP $status" }
        } finally {
            connection.disconnect()
        }
    }

}
