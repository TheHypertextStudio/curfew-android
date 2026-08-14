package studio.hypertext.curfew.account

import android.app.Activity
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio.hypertext.curfew.security.AndroidKeystoreSecretStore

class BrowserOAuthEnrollment(activity: Activity) {
    private val hostActivity = activity
    private val secrets = AndroidKeystoreSecretStore(activity)

    suspend fun begin(): OAuthEnrollmentRequest {
        val clientId = OAuthDynamicClientRegistrar(hostActivity).registeredClientId()
        val request = OAuthEnrollmentRequest.create(
            issuer = URI("https://curfew-account.hypertext.studio"),
            clientId = clientId,
            redirectUri = URI("studio.hypertext.curfew://oauth/callback"),
        )
        secrets.put("oauth:${request.state}:verifier", request.codeVerifier.toByteArray())
        withContext(Dispatchers.Main) {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(hostActivity, Uri.parse(request.authorizationUri.toASCIIString()))
        }
        return request
    }

    fun consumeVerifier(state: String): String? {
        val identifier = "oauth:$state:verifier"
        val verifier = secrets.get(identifier)?.toString(Charsets.UTF_8)
        secrets.delete(identifier)
        return verifier
    }
}
