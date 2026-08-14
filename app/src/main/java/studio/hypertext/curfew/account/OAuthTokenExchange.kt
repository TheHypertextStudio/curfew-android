package studio.hypertext.curfew.account

import android.app.Activity
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import studio.hypertext.curfew.persistence.CurfewPreferences
import studio.hypertext.curfew.security.AndroidKeystoreSecretStore

class OAuthTokenExchange(private val activity: Activity) {
    suspend fun exchange(callback: Uri) = withContext(Dispatchers.IO) {
        require(callback.scheme == "studio.hypertext.curfew")
        require(callback.host == "oauth" && callback.path == "/callback")
        val state = callback.getQueryParameter("state")?.takeIf(String::isNotBlank)
            ?: error("OAuth callback omitted state")
        val code = callback.getQueryParameter("code")?.takeIf(String::isNotBlank)
            ?: error("OAuth callback omitted authorization code")
        val browserEnrollment = BrowserOAuthEnrollment(activity)
        val verifier = browserEnrollment.consumeVerifier(state)
            ?: error("OAuth callback state is missing or replayed")
        val clientId = OAuthDynamicClientRegistrar(activity).registeredClientId()
        val endpoint = URI("https://curfew-account.hypertext.studio/api/auth/oauth2/token")
        val form = linkedMapOf(
            "grant_type" to "authorization_code",
            "client_id" to clientId,
            "code" to code,
            "code_verifier" to verifier,
            "redirect_uri" to "studio.hypertext.curfew://oauth/callback",
        ).entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val connection = endpoint.toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.doOutput = true
            connection.setRequestProperty(
                "Content-Type",
                "application/x-www-form-urlencoded; charset=UTF-8",
            )
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { it.write(form.toByteArray(StandardCharsets.UTF_8)) }
            val status = connection.responseCode
            check(status !in 300..399) { "OAuth token redirects are rejected" }
            check(status in 200..299) { "OAuth token exchange returned HTTP $status" }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            require(response.length <= 32 * 1024)
            val objectValue = Json.parseToJsonElement(response).jsonObject
            val tokenType = objectValue["token_type"]?.jsonPrimitive?.content
            require(tokenType.equals("Bearer", ignoreCase = true))
            val accessToken = objectValue["access_token"]?.jsonPrimitive?.content
                ?.takeIf(String::isNotBlank)
                ?: error("OAuth token response omitted access_token")
            val refreshToken = objectValue["refresh_token"]?.jsonPrimitive?.content
                ?.takeIf(String::isNotBlank)
                ?: error("OAuth token response omitted refresh_token")
            AndroidKeystoreSecretStore(activity).apply {
                put("account:access-token", accessToken.toByteArray())
                put("account:refresh-token", refreshToken.toByteArray())
            }
            val pkceChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256")
                    .digest(verifier.toByteArray(StandardCharsets.US_ASCII)),
            )
            NativeDeviceEnrollmentClient(activity).enroll(accessToken, pkceChallenge, state)
            CurfewPreferences(activity).setAccountSignedIn(true)
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(
        value,
        StandardCharsets.UTF_8,
    ).replace("+", "%20")
}
