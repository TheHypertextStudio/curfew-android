package studio.hypertext.curfew.account

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import studio.hypertext.curfew.config.ProductionServices
import studio.hypertext.curfew.security.AndroidKeystoreSecretStore
import java.net.HttpURLConnection
import java.net.URI

class OAuthDynamicClientRegistrar(
    context: Context,
) {
    private val secrets = AndroidKeystoreSecretStore(context)

    suspend fun registeredClientId(): String =
        withContext(Dispatchers.IO) {
            secrets.get(CLIENT_ID_KEY)?.toString(Charsets.UTF_8)?.let { return@withContext it }
            val endpoint = URI("${ProductionServices.ACCOUNT_ORIGIN}/api/auth/oauth2/register")
            val connection = endpoint.toURL().openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.requestMethod = "POST"
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                val body =
                    buildJsonObject {
                        put("client_name", "Curfew for Android")
                        put("token_endpoint_auth_method", "none")
                        put("application_type", "native")
                        put("redirect_uris", buildJsonArray { add("studio.hypertext.curfew://oauth/callback") })
                        put(
                            "grant_types",
                            buildJsonArray {
                                add("authorization_code")
                                add("refresh_token")
                            },
                        )
                        put("response_types", buildJsonArray { add("code") })
                        put(
                            "scope",
                            CURFEW_ANDROID_SCOPES,
                        )
                    }.toString()
                connection.outputStream.use { it.write(body.toByteArray()) }
                val status = connection.responseCode
                check(status !in 300..399) { "OAuth client registration redirects are rejected" }
                check(status in 200..299) { "OAuth client registration returned HTTP $status" }
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                require(response.length <= 32 * 1024)
                val clientId =
                    Json
                        .parseToJsonElement(response)
                        .jsonObject["client_id"]
                        ?.jsonPrimitive
                        ?.content
                        ?.takeIf(String::isNotBlank)
                        ?: error("OAuth client registration omitted client_id")
                secrets.put(CLIENT_ID_KEY, clientId.toByteArray())
                clientId
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val CLIENT_ID_KEY = ProductionServices.OAUTH_CLIENT_CACHE_KEY
    }
}
