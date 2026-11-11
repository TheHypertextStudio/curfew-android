package studio.hypertext.curfew.account

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import studio.hypertext.curfew.config.ProductionServices
import studio.hypertext.curfew.persistence.CurfewPreferences
import studio.hypertext.curfew.security.AndroidKeystoreSecretStore
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

/**
 * Validates and exchanges a native OAuth return, enrolls the device, and resumes key recovery.
 * It consumes the PKCE verifier once; a missing or replayed state cannot complete sign-in.
 */
class OAuthTokenExchange(
    private val context: Context,
) {
    internal suspend fun exchange(callbackUrl: String): AccountRecoveryState =
        withContext(Dispatchers.IO) {
            require(AccountOAuthRedirect.matches(URI(callbackUrl)))
            val callback = Uri.parse(callbackUrl)
            val state =
                callback.getQueryParameter("state")?.takeIf(String::isNotBlank)
                    ?: error("OAuth callback omitted state")
            val code =
                callback.getQueryParameter("code")?.takeIf(String::isNotBlank)
                    ?: error("OAuth callback omitted authorization code")
            val browserEnrollment = BrowserOAuthEnrollment(context)
            val verifier =
                browserEnrollment.consumeVerifier(state)
                    ?: error("OAuth callback state is missing or replayed")
            val clientId = OAuthDynamicClientRegistrar(context).registeredClientId()
            val endpoint = URI("${ProductionServices.ACCOUNT_ORIGIN}/api/auth/oauth2/token")
            val form =
                linkedMapOf(
                    "grant_type" to "authorization_code",
                    "client_id" to clientId,
                    "code" to code,
                    "code_verifier" to verifier,
                    "redirect_uri" to AccountOAuthRedirect.URL,
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
                val accessToken =
                    objectValue["access_token"]
                        ?.jsonPrimitive
                        ?.content
                        ?.takeIf(String::isNotBlank)
                        ?: error("OAuth token response omitted access_token")
                val refreshToken =
                    objectValue["refresh_token"]
                        ?.jsonPrimitive
                        ?.content
                        ?.takeIf(String::isNotBlank)
                        ?: error("OAuth token response omitted refresh_token")
                CurfewPreferences(context).setAccountSignedIn(false)
                AndroidKeystoreSecretStore(context).apply {
                    put(ProductionServices.ACCESS_TOKEN_KEY, accessToken.toByteArray())
                    put(ProductionServices.REFRESH_TOKEN_KEY, refreshToken.toByteArray())
                }
                val pkceChallenge =
                    Base64.getUrlEncoder().withoutPadding().encodeToString(
                        MessageDigest
                            .getInstance("SHA-256")
                            .digest(verifier.toByteArray(StandardCharsets.US_ASCII)),
                    )
                NativeDeviceEnrollmentClient(context).enroll(accessToken, pkceChallenge, state)
                CurfewPreferences(context).completeAccountSignIn {
                    AndroidAccountRecovery(context).resumeAfterEnrollment()
                }
            } finally {
                connection.disconnect()
            }
        }

    private fun encode(value: String): String =
        URLEncoder
            .encode(
                value,
                StandardCharsets.UTF_8,
            ).replace("+", "%20")
}
