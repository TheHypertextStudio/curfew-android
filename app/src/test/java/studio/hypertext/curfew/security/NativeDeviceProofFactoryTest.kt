package studio.hypertext.curfew.security

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

class NativeDeviceProofFactoryTest {
    @Test
    fun `proof binds token method URL nonce and canonical JSON body`() {
        val signedInputs = mutableListOf<ByteArray>()
        val factory =
            NativeDeviceProofFactory(
                signer =
                    RecordSigner { input ->
                        signedInputs += input
                        ByteArray(64) { it.toByte() }
                    },
                now = { Instant.parse("2026-08-13T07:00:00Z") },
                uuid = { "30000000-0000-4000-8000-000000000001" },
            )
        val body = """{"z":2,"a":"first"}"""
        val proof =
            factory.create(
                accessToken = "access-token",
                nonce = "N".repeat(43),
                method = "POST",
                canonicalUrl = "https://sync.thecurfew.app/sync/wake/status",
                jsonBody = body,
            )

        val segments = proof.split('.')
        assertEquals(3, segments.size)
        assertEquals(
            signedInputs.single().toString(StandardCharsets.US_ASCII),
            "${segments[0]}.${segments[1]}",
        )
        val payload =
            Json
                .parseToJsonElement(
                    String(Base64.getUrlDecoder().decode(segments[1]), StandardCharsets.UTF_8),
                ).jsonObject
        assertEquals("POST", payload.getValue("httpMethod").jsonPrimitive.content)
        assertEquals("N".repeat(43), payload.getValue("nonce").jsonPrimitive.content)
        assertEquals(
            base64UrlForTest(
                java.security.MessageDigest
                    .getInstance("SHA-256")
                    .digest("access-token".toByteArray()),
            ),
            payload.getValue("accessTokenHash").jsonPrimitive.content,
        )
        assertEquals(
            base64UrlForTest(
                java.security.MessageDigest
                    .getInstance("SHA-256")
                    .digest("{\"a\":\"first\",\"z\":2}".toByteArray()),
            ),
            payload.getValue("bodyDigest").jsonPrimitive.content,
        )
        assertNotEquals(segments[1], proof)
    }

    @Test
    fun `proof refuses retired or lookalike sync authorities`() {
        val factory = NativeDeviceProofFactory(signer = RecordSigner { ByteArray(64) })
        listOf(
            "https://curfew-sync.hypertext.studio/sync/wake/status",
            "https://sync.thecurfew.app.attacker.example/sync/wake/status",
            "https://sync.thecurfew.app:444/sync/wake/status",
            "https://user@sync.thecurfew.app/sync/wake/status",
            "http://sync.thecurfew.app/sync/wake/status",
        ).forEach { endpoint ->
            assertThrows(endpoint, IllegalArgumentException::class.java) {
                factory.create("access-token", "N".repeat(43), "POST", endpoint)
            }
        }
    }

    private fun base64UrlForTest(value: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value)
}
