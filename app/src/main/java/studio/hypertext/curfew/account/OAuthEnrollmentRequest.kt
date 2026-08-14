package studio.hypertext.curfew.account

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import studio.hypertext.curfew.protocols.CurfewFirstPartyOAuthScope

private const val CURFEW_ACCOUNT_HOST = "curfew-account.hypertext.studio"
private const val CURFEW_SYNC_RESOURCE = "https://curfew-sync.hypertext.studio"
private const val CURFEW_ANDROID_REDIRECT = "studio.hypertext.curfew://oauth/callback"
internal val CURFEW_ANDROID_SCOPES: String =
    (listOf("openid", "offline_access") + CurfewFirstPartyOAuthScope.entries.map { it.value })
        .joinToString(" ")

data class OAuthEnrollmentRequest(
    val authorizationUri: URI,
    val tokenUri: URI,
    val codeVerifier: String,
    val codeChallenge: String,
    val state: String,
    val requiresGooglePlayServices: Boolean = false,
) {
    companion object {
        fun create(
            issuer: URI,
            clientId: String,
            redirectUri: URI,
            state: String = randomUrlSafe(32),
            codeVerifier: String = randomUrlSafe(64),
        ): OAuthEnrollmentRequest {
            require(issuer.scheme == "https") { "OAuth issuer must use HTTPS" }
            require(issuer.host == CURFEW_ACCOUNT_HOST) { "Unexpected Curfew account host" }
            require(clientId.isNotBlank())
            require(state.isNotBlank())
            require(codeVerifier.length in 43..128) { "PKCE verifier must be 43-128 characters" }
            require(redirectUri.toASCIIString() == CURFEW_ANDROID_REDIRECT) {
                "OAuth redirect must be Curfew's registered native callback"
            }

            val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.toByteArray(StandardCharsets.US_ASCII)),
            )
            val query = linkedMapOf(
                "client_id" to clientId,
                "redirect_uri" to redirectUri.toASCIIString(),
                "response_type" to "code",
                "scope" to CURFEW_ANDROID_SCOPES,
                "resource" to CURFEW_SYNC_RESOURCE,
                "state" to state,
                "code_challenge" to challenge,
                "code_challenge_method" to "S256",
            ).entries.joinToString("&") { (key, value) ->
                "${encode(key)}=${encode(value)}"
            }
            val base = issuer.toASCIIString().trimEnd('/')
            return OAuthEnrollmentRequest(
                authorizationUri = URI("$base/api/auth/oauth2/authorize?$query"),
                tokenUri = URI("$base/api/auth/oauth2/token"),
                codeVerifier = codeVerifier,
                codeChallenge = challenge,
                state = state,
            )
        }

        private fun randomUrlSafe(byteCount: Int): String =
            ByteArray(byteCount).also(SecureRandom()::nextBytes).let {
                Base64.getUrlEncoder().withoutPadding().encodeToString(it)
            }

        private fun encode(value: String): String = URLEncoder.encode(
            value,
            StandardCharsets.UTF_8,
        ).replace("+", "%20")
    }
}
