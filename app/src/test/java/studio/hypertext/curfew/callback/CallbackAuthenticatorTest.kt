package studio.hypertext.curfew.callback

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import studio.hypertext.curfew.protocols.CallbackDefinition
import studio.hypertext.curfew.protocols.CallbackPollPolicy
import studio.hypertext.curfew.protocols.CallbackReceipt
import studio.hypertext.curfew.protocols.ReceiptStatus

class CallbackAuthenticatorTest {
    private val secret = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"
    private val callbackId = "018f4f45-cafe-7f00-9a82-e47805fb4d34"
    private val campaignId = "018f4f45-7a98-7f53-89af-a4805f705d20"
    private val nonce = "Fb17b59pB_k3RG7VSz0hEw"
    private val challengedAt = Instant.parse("2026-08-10T14:00:05Z")
    private val definition = CallbackDefinition(
        callbackId = callbackId,
        displayLabel = "Start the day",
        endpoint = "https://callback.example.test/condition",
        pollPolicy = CallbackPollPolicy(
            intervalSeconds = 15,
            maximumBackoffSeconds = 60,
            requestTimeoutSeconds = 5,
        ),
        secret = secret,
    )

    @Test
    fun `challenge matches the shared protocol golden vector`() {
        val challenge = CallbackAuthenticator(InMemoryNonceLedger()).createChallenge(
            definition = definition,
            campaignId = campaignId,
            campaignStartedAt = Instant.parse("2026-08-10T14:00:00Z"),
            challengedAt = challengedAt,
            nonce = nonce,
        )

        assertEquals("2026-08-10T14:00:10Z", challenge.expiresAt)
        assertEquals(
            "ltfHvXBz1jt8Vt8d5lRHzKc6jv-52_-UiXDpda0_VUo",
            challenge.mac,
        )
    }

    @Test
    fun `fresh satisfied receipt matches the golden vector and consumes its nonce`() {
        val ledger = InMemoryNonceLedger()
        val authenticator = CallbackAuthenticator(ledger)
        val challenge = goldenChallenge(authenticator)
        val receipt = goldenReceipt()

        val accepted = authenticator.verify(
            definition = definition,
            challenge = challenge,
            receipt = receipt,
            now = Instant.parse("2026-08-10T14:00:07Z"),
        )

        assertEquals(ReceiptStatus.Satisfied, accepted.receipt.status)
        assertEquals(true, ledger.wasConsumed(nonce))
    }

    @Test
    fun `forged receipt MAC fails closed`() {
        val authenticator = CallbackAuthenticator(InMemoryNonceLedger())
        val forged = goldenReceipt().copy(mac = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")

        assertThrows(CallbackVerificationException::class.java) {
            authenticator.verify(
                definition,
                goldenChallenge(authenticator),
                forged,
                Instant.parse("2026-08-10T14:00:07Z"),
            )
        }
    }

    @Test
    fun `forged challenge MAC fails closed before receipt acceptance`() {
        val authenticator = CallbackAuthenticator(InMemoryNonceLedger())
        val forgedChallenge = goldenChallenge(authenticator).copy(
            mac = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        )

        assertThrows(CallbackVerificationException::class.java) {
            authenticator.verify(
                definition,
                forgedChallenge,
                goldenReceipt(),
                Instant.parse("2026-08-10T14:00:07Z"),
            )
        }
    }

    @Test
    fun `stale receipt fails closed even when its MAC is valid`() {
        val authenticator = CallbackAuthenticator(InMemoryNonceLedger())

        assertThrows(CallbackVerificationException::class.java) {
            authenticator.verify(
                definition,
                goldenChallenge(authenticator),
                goldenReceipt(),
                Instant.parse("2026-08-10T14:01:30Z"),
            )
        }
    }

    @Test
    fun `future receipt fails closed`() {
        val authenticator = CallbackAuthenticator(InMemoryNonceLedger())

        assertThrows(CallbackVerificationException::class.java) {
            authenticator.verify(
                definition,
                goldenChallenge(authenticator),
                goldenReceipt(),
                Instant.parse("2026-08-10T13:59:55Z"),
            )
        }
    }

    @Test
    fun `replayed nonce fails after one successful verification`() {
        val authenticator = CallbackAuthenticator(InMemoryNonceLedger())
        val challenge = goldenChallenge(authenticator)
        val receipt = goldenReceipt()
        val now = Instant.parse("2026-08-10T14:00:07Z")

        authenticator.verify(definition, challenge, receipt, now)

        assertThrows(CallbackVerificationException::class.java) {
            authenticator.verify(definition, challenge, receipt, now)
        }
    }

    @Test
    fun `wrong campaign and nonce fail before acceptance`() {
        val authenticator = CallbackAuthenticator(InMemoryNonceLedger())
        val challenge = goldenChallenge(authenticator)

        assertThrows(CallbackVerificationException::class.java) {
            authenticator.verify(
                definition,
                challenge,
                goldenReceipt().copy(campaignId = "different-campaign"),
                Instant.parse("2026-08-10T14:00:07Z"),
            )
        }
        assertThrows(CallbackVerificationException::class.java) {
            authenticator.verify(
                definition,
                challenge,
                goldenReceipt().copy(nonce = "different-nonce"),
                Instant.parse("2026-08-10T14:00:07Z"),
            )
        }
    }

    @Test
    fun `malformed receipt JSON fails closed`() {
        val authenticator = CallbackAuthenticator(InMemoryNonceLedger())

        assertThrows(CallbackVerificationException::class.java) {
            authenticator.verifyJson(
                definition,
                goldenChallenge(authenticator),
                "{\"status\":\"satisfied\"}",
                Instant.parse("2026-08-10T14:00:07Z"),
            )
        }
    }

    @Test
    fun `transport backoff is bounded by the callback policy`() {
        val backoff = CallbackBackoff(definition.pollPolicy)

        assertEquals(Duration.ofSeconds(15), backoff.afterFailure(0))
        assertEquals(Duration.ofSeconds(30), backoff.afterFailure(1))
        assertEquals(Duration.ofSeconds(60), backoff.afterFailure(2))
        assertEquals(Duration.ofSeconds(60), backoff.afterFailure(20))
        assertEquals(Duration.ofSeconds(15), backoff.afterSuccess())
    }

    private fun goldenChallenge(authenticator: CallbackAuthenticator) =
        authenticator.createChallenge(
            definition = definition,
            campaignId = campaignId,
            campaignStartedAt = Instant.parse("2026-08-10T14:00:00Z"),
            challengedAt = challengedAt,
            nonce = nonce,
        )

    private fun goldenReceipt() = CallbackReceipt(
        campaignId = campaignId,
        expiresAt = "2026-08-10T14:00:20Z",
        mac = "QX7X_95wB6aQjro_mBnT3SybkuyKftBj1CSgvYpWszw",
        nonce = nonce,
        observedAt = "2026-08-10T14:00:06Z",
        status = ReceiptStatus.Satisfied,
    )
}
