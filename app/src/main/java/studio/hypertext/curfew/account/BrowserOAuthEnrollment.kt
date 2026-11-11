package studio.hypertext.curfew.account

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio.hypertext.curfew.config.ProductionServices
import studio.hypertext.curfew.security.AndroidKeystoreSecretStore
import java.net.URI

class BrowserOAuthEnrollment(
    context: Context,
) {
    private val hostContext = context
    private val secrets = AndroidKeystoreSecretStore(context)

    suspend fun begin(): OAuthEnrollmentRequest {
        check(canUseAccountSignIn(hostContext.packageName)) { "Account sign-in is unavailable in this test build." }
        val clientId = OAuthDynamicClientRegistrar(hostContext).registeredClientId()
        val request =
            OAuthEnrollmentRequest.create(
                issuer = URI(ProductionServices.ACCOUNT_ORIGIN),
                clientId = clientId,
                redirectUri = URI("studio.hypertext.curfew://oauth/callback"),
            )
        secrets.put("oauth:${request.state}:verifier", request.codeVerifier.toByteArray())
        withContext(Dispatchers.Main) {
            CustomTabsIntent
                .Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(hostContext, Uri.parse(request.authorizationUri.toASCIIString()))
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
