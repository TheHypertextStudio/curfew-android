package studio.hypertext.curfew.callback

import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import studio.hypertext.curfew.protocols.CallbackDefinition
import studio.hypertext.curfew.protocols.CallbackReceiptAcceptance

class CallbackTransportException(message: String, cause: Throwable? = null) :
    java.io.IOException(message, cause)

class HttpsCallbackClient(
    private val nonceLedger: NonceLedger,
    private val json: Json = Json,
) {
    fun poll(
        definition: CallbackDefinition,
        campaignId: String,
        campaignStartedAt: Instant,
        now: Instant = Instant.now(),
    ): CallbackReceiptAcceptance {
        val nonce = ByteArray(16).also(SecureRandom()::nextBytes).let {
            Base64.getUrlEncoder().withoutPadding().encodeToString(it)
        }
        val authenticator = CallbackAuthenticator(nonceLedger, json)
        val challenge = authenticator.createChallenge(
            definition,
            campaignId,
            campaignStartedAt,
            now,
            nonce,
        )
        val endpoint = URI(definition.endpoint)
        require(endpoint.scheme == "https")
        val connection = endpoint.toURL().openConnection() as HttpURLConnection
        return try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.connectTimeout = (definition.pollPolicy.requestTimeoutSeconds * 1_000)
                .toInt()
            connection.readTimeout = connection.connectTimeout
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use {
                it.write(json.encodeToString(challenge).toByteArray(StandardCharsets.UTF_8))
            }
            val status = connection.responseCode
            if (status in 300..399) {
                throw CallbackTransportException("callback redirects are rejected")
            }
            if (status !in 200..299) {
                throw CallbackTransportException("callback returned HTTP $status")
            }
            val body = connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use {
                it.readTextLimited(32 * 1024)
            }
            authenticator.verifyJson(definition, challenge, body, Instant.now())
        } catch (exception: CallbackVerificationException) {
            throw exception
        } catch (exception: CallbackTransportException) {
            throw exception
        } catch (exception: java.io.IOException) {
            throw CallbackTransportException("callback transport failed", exception)
        } finally {
            connection.disconnect()
        }
    }
}

private fun java.io.Reader.readTextLimited(maxCharacters: Int): String {
    val result = StringBuilder()
    val buffer = CharArray(2_048)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        if (result.length + read > maxCharacters) {
            throw CallbackTransportException("callback response is too large")
        }
        result.append(buffer, 0, read)
    }
    return result.toString()
}
