package studio.hypertext.curfew.account

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceEnrollmentTest {
    @Test
    fun `PKCE challenge matches RFC 7636 and uses the system browser contract`() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

        val request = OAuthEnrollmentRequest.create(
            issuer = URI("https://curfew-account.hypertext.studio"),
            clientId = "studio.hypertext.curfew.android",
            redirectUri = URI("studio.hypertext.curfew://oauth/callback"),
            state = "state-123",
            codeVerifier = verifier,
        )

        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            request.codeChallenge,
        )
        assertEquals("https", request.authorizationUri.scheme)
        assertEquals("curfew-account.hypertext.studio", request.authorizationUri.host)
        assertEquals(
            "/api/auth/oauth2/authorize",
            request.authorizationUri.path,
        )
        assertEquals("/api/auth/oauth2/token", request.tokenUri.path)
        assertTrue(request.authorizationUri.rawQuery.contains("code_challenge_method=S256"))
        assertTrue(request.authorizationUri.rawQuery.contains("state=state-123"))
        assertTrue(
            request.authorizationUri.query.contains(
                "resource=https://curfew-sync.hypertext.studio",
            ),
        )
        assertTrue(
            request.authorizationUri.query.contains(
                "scope=openid offline_access curfew:account:read curfew:devices:read " +
                    "curfew:devices:write curfew:entitlements:read curfew:sync:read " +
                    "curfew:sync:write curfew:wake:read curfew:wake:write",
            ),
        )
        assertFalse(request.authorizationUri.query.contains("/mcp"))
        assertFalse(request.requiresGooglePlayServices)
    }

    @Test
    fun `non HTTPS issuer is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            OAuthEnrollmentRequest.create(
                issuer = URI("http://curfew-account.hypertext.studio"),
                clientId = "studio.hypertext.curfew.android",
                redirectUri = URI("studio.hypertext.curfew://oauth/callback"),
                state = "state-123",
                codeVerifier = "verifier-with-at-least-forty-three-characters-1234",
            )
        }
    }

    @Test
    fun `unexpected account host is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            OAuthEnrollmentRequest.create(
                issuer = URI("https://accounts.example.com"),
                clientId = "studio.hypertext.curfew.android",
                redirectUri = URI("studio.hypertext.curfew://oauth/callback"),
                state = "state-123",
                codeVerifier = "verifier-with-at-least-forty-three-characters-1234",
            )
        }
    }

    @Test
    fun `redirect must be the registered public native callback`() {
        assertThrows(IllegalArgumentException::class.java) {
            OAuthEnrollmentRequest.create(
                issuer = URI("https://curfew-account.hypertext.studio"),
                clientId = "studio.hypertext.curfew.android",
                redirectUri = URI("studio.hypertext.curfew:/unexpected"),
                state = "state-123",
                codeVerifier = "verifier-with-at-least-forty-three-characters-1234",
            )
        }
    }
}
