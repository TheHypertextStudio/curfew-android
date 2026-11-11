package studio.hypertext.curfew

import java.net.URI
import java.net.URISyntaxException
import studio.hypertext.curfew.account.AccountOAuthRedirect

internal const val ACTION_SHOW_ENFORCEMENT = "studio.hypertext.curfew.action.SHOW_ENFORCEMENT"

internal sealed interface LaunchRequest {
    class OAuthCallback(val url: String) : LaunchRequest {
        override fun toString() = "OAuthCallback"
    }
    data class ShowAlarm(val campaignId: String) : LaunchRequest
    data class Enforcement(val interceptedPackage: String?) : LaunchRequest
    data object None : LaunchRequest
}

internal fun parseLaunchRequest(
    data: String? = null,
    action: String? = null,
    showAlarm: Boolean = false,
    campaignId: String? = null,
    interceptedPackage: String? = null,
): LaunchRequest {
    val uri = try {
        data?.let(::URI)
    } catch (_: URISyntaxException) {
        null
    }
    return when {
        uri != null && AccountOAuthRedirect.matches(uri) ->
            LaunchRequest.OAuthCallback(requireNotNull(data))
        showAlarm && !campaignId.isNullOrBlank() -> LaunchRequest.ShowAlarm(campaignId)
        action == ACTION_SHOW_ENFORCEMENT -> LaunchRequest.Enforcement(interceptedPackage?.takeUnless(String::isBlank))
        else -> LaunchRequest.None
    }
}
