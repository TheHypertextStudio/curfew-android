package studio.hypertext.curfew.callback

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import studio.hypertext.curfew.protocols.CallbackChallenge
import studio.hypertext.curfew.protocols.CallbackDefinition
import studio.hypertext.curfew.protocols.CallbackPollPolicy
import studio.hypertext.curfew.protocols.CallbackReceipt
import studio.hypertext.curfew.protocols.CallbackReceiptAcceptance
import studio.hypertext.curfew.protocols.CampaignDisposition
import studio.hypertext.curfew.protocols.MACDispositionEnum
import studio.hypertext.curfew.protocols.NonceDispositionEnum

private const val REQUEST_KEY_INFO = "curfew-callback-request-v1"
private const val RESPONSE_KEY_INFO = "curfew-callback-response-v1"

class CallbackVerificationException(message: String, cause: Throwable? = null) :
    SecurityException(message, cause)

interface NonceLedger {
    fun register(nonce: String)
    fun consume(nonce: String): Boolean
}

class InMemoryNonceLedger : NonceLedger {
    private val active = mutableSetOf<String>()
    private val consumed = mutableSetOf<String>()

    @Synchronized
    override fun register(nonce: String) {
        require(nonce.isNotBlank())
        check(nonce !in active && nonce !in consumed) { "nonce was already registered" }
        active += nonce
    }

    @Synchronized
    override fun consume(nonce: String): Boolean {
        if (!active.remove(nonce)) return false
        consumed += nonce
        return true
    }

    @Synchronized
    fun wasConsumed(nonce: String): Boolean = nonce in consumed
}

class CallbackAuthenticator(
    private val nonceLedger: NonceLedger,
    private val json: Json = Json { ignoreUnknownKeys = false },
) {
    fun createChallenge(
        definition: CallbackDefinition,
        campaignId: String,
        campaignStartedAt: Instant,
        challengedAt: Instant,
        nonce: String,
    ): CallbackChallenge {
        validateDefinition(definition)
        require(campaignId.isNotBlank())
        require(nonce.isNotBlank())
        require(!challengedAt.isBefore(campaignStartedAt))
        val expiresAt = challengedAt.plusSeconds(definition.pollPolicy.requestTimeoutSeconds)
        val unsigned = canonicalObject(
            "callbackId" to definition.callbackId,
            "campaignId" to campaignId,
            "campaignStartedAt" to campaignStartedAt.toString(),
            "challengedAt" to challengedAt.toString(),
            "expiresAt" to expiresAt.toString(),
            "nonce" to nonce,
        )
        nonceLedger.register(nonce)
        return CallbackChallenge(
            callbackId = definition.callbackId,
            campaignId = campaignId,
            campaignStartedAt = campaignStartedAt.toString(),
            challengedAt = challengedAt.toString(),
            expiresAt = expiresAt.toString(),
            mac = sign(definition, REQUEST_KEY_INFO, unsigned),
            nonce = nonce,
        )
    }

    fun verify(
        definition: CallbackDefinition,
        challenge: CallbackChallenge,
        receipt: CallbackReceipt,
        now: Instant,
    ): CallbackReceiptAcceptance {
        validateDefinition(definition)
        rejectUnless(challenge.callbackId == definition.callbackId, "callback does not match")
        rejectUnless(receipt.campaignId == challenge.campaignId, "campaign does not match")
        rejectUnless(receipt.nonce == challenge.nonce, "nonce does not match")
        val expectedChallengeMac = sign(
            definition,
            REQUEST_KEY_INFO,
            canonicalObject(
                "callbackId" to challenge.callbackId,
                "campaignId" to challenge.campaignId,
                "campaignStartedAt" to challenge.campaignStartedAt,
                "challengedAt" to challenge.challengedAt,
                "expiresAt" to challenge.expiresAt,
                "nonce" to challenge.nonce,
            ),
        )
        rejectUnless(
            MessageDigest.isEqual(
                expectedChallengeMac.toByteArray(StandardCharsets.US_ASCII),
                challenge.mac.toByteArray(StandardCharsets.US_ASCII),
            ),
            "challenge MAC is invalid",
        )

        val challengedAt = parseInstant(challenge.challengedAt, "challenge time")
        val challengeExpiry = parseInstant(challenge.expiresAt, "challenge expiry")
        val observedAt = parseInstant(receipt.observedAt, "observation time")
        val receiptExpiry = parseInstant(receipt.expiresAt, "receipt expiry")
        rejectUnless(!now.isAfter(challengeExpiry), "challenge expired")
        rejectUnless(!observedAt.isBefore(challengedAt), "observation predates challenge")
        rejectUnless(!observedAt.isAfter(now.plusSeconds(5)), "observation is in the future")
        rejectUnless(!now.isAfter(receiptExpiry), "receipt expired")
        rejectUnless(receiptExpiry.isAfter(observedAt), "receipt expiry is invalid")
        rejectUnless(
            Duration.between(observedAt, receiptExpiry) <= Duration.ofMinutes(1),
            "receipt expiry is too distant",
        )

        val canonical = canonicalObject(
            "campaignId" to receipt.campaignId,
            "expiresAt" to receipt.expiresAt,
            "nonce" to receipt.nonce,
            "observedAt" to receipt.observedAt,
            "status" to receipt.status.value,
        )
        val expectedMac = sign(definition, RESPONSE_KEY_INFO, canonical)
        rejectUnless(
            MessageDigest.isEqual(
                expectedMac.toByteArray(StandardCharsets.US_ASCII),
                receipt.mac.toByteArray(StandardCharsets.US_ASCII),
            ),
            "receipt MAC is invalid",
        )
        rejectUnless(nonceLedger.consume(receipt.nonce), "nonce is missing or replayed")

        return CallbackReceiptAcceptance(
            campaignDisposition = CampaignDisposition.Matched,
            macDisposition = MACDispositionEnum.Valid,
            nonceDisposition = NonceDispositionEnum.Fresh,
            receipt = receipt,
            timestampDisposition = NonceDispositionEnum.Fresh,
        )
    }

    fun verifyJson(
        definition: CallbackDefinition,
        challenge: CallbackChallenge,
        body: String,
        now: Instant,
    ): CallbackReceiptAcceptance = try {
        verify(definition, challenge, json.decodeFromString<CallbackReceipt>(body), now)
    } catch (exception: CallbackVerificationException) {
        throw exception
    } catch (exception: SerializationException) {
        throw CallbackVerificationException("malformed callback receipt", exception)
    } catch (exception: IllegalArgumentException) {
        throw CallbackVerificationException("malformed callback receipt", exception)
    }

    private fun validateDefinition(definition: CallbackDefinition) {
        val endpoint = runCatching { java.net.URI(definition.endpoint) }.getOrNull()
        require(endpoint?.scheme == "https" && endpoint.host != null) {
            "callback endpoint must use HTTPS"
        }
        require(definition.callbackId.isNotBlank())
        require(definition.displayLabel.isNotBlank())
        require(definition.pollPolicy.intervalSeconds in 1..60)
        require(definition.pollPolicy.requestTimeoutSeconds in 1..15)
        require(
            definition.pollPolicy.maximumBackoffSeconds >=
                definition.pollPolicy.intervalSeconds,
        )
        val decoded = runCatching { decodeUrlSafe(definition.secret) }.getOrNull()
        require(decoded?.size == 32) { "callback secret must contain 256 bits" }
    }

    private fun sign(definition: CallbackDefinition, info: String, canonical: String): String {
        val key = hkdfSha256(
            inputKeyMaterial = decodeUrlSafe(definition.secret),
            salt = definition.callbackId.toByteArray(StandardCharsets.UTF_8),
            info = info.toByteArray(StandardCharsets.UTF_8),
            length = 32,
        )
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            mac.doFinal(canonical.toByteArray(StandardCharsets.UTF_8)),
        )
    }

    private fun parseInstant(value: String, label: String): Instant = try {
        Instant.parse(value)
    } catch (exception: RuntimeException) {
        throw CallbackVerificationException("invalid $label", exception)
    }

    private fun rejectUnless(condition: Boolean, message: String) {
        if (!condition) throw CallbackVerificationException(message)
    }
}

class CallbackBackoff(private val policy: CallbackPollPolicy) {
    fun afterFailure(consecutiveFailures: Int): Duration {
        require(consecutiveFailures >= 0)
        val exponent = consecutiveFailures.coerceAtMost(30)
        val multiplier = 1L shl exponent
        val seconds = (policy.intervalSeconds * multiplier)
            .coerceAtMost(policy.maximumBackoffSeconds)
        return Duration.ofSeconds(seconds)
    }

    fun afterSuccess(): Duration = Duration.ofSeconds(policy.intervalSeconds)
}

private fun hkdfSha256(
    inputKeyMaterial: ByteArray,
    salt: ByteArray,
    info: ByteArray,
    length: Int,
): ByteArray {
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

private fun decodeUrlSafe(value: String): ByteArray =
    Base64.getUrlDecoder().decode(value)

private fun canonicalObject(vararg fields: Pair<String, String>): String =
    fields.sortedBy(Pair<String, String>::first).joinToString(",", "{", "}") { (key, value) ->
        "${quoteJson(key)}:${quoteJson(value)}"
    }

private fun quoteJson(value: String): String = buildString {
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
