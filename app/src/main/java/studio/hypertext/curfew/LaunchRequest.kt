package studio.hypertext.curfew

import java.net.URI
import java.net.URISyntaxException
import studio.hypertext.curfew.account.AccountOAuthRedirect

/**
 * Intent action used by the protection service and notification to open the protection
 * destination.
 */
internal const val ACTION_SHOW_ENFORCEMENT = "studio.hypertext.curfew.action.SHOW_ENFORCEMENT"

/**
 * Diagnostic label for an OAuth callback. It never contains the callback URL or authorization
 * code.
 */
private const val REDACTED_CALLBACK_LABEL = "OAuthCallback"

/**
 * The small set of launch commands Curfew can receive from Android. These values carry only
 * what the destination or feature needs; they do not contain Intent or Context objects.
 */
internal sealed interface LaunchRequest {
    /** Carries the sensitive browser return. Only account exchange should read [url]. */
    class OAuthCallback(val url: String) : LaunchRequest {
        override fun toString() = REDACTED_CALLBACK_LABEL
    }

    /** Presents a single persisted occurrence, independent of which schedule the UI is editing. */
    data class ShowAlarm(val campaignId: String) : LaunchRequest

    /** Opens protection; a notification entry has no intercepted package to display. */
    data class Enforcement(val interceptedPackage: String?) : LaunchRequest

    /** Ordinary launches or inputs that do not identify a supported command. */
    data object None : LaunchRequest
}

/**
 * Validates launch inputs without Android dependencies. An exact OAuth redirect takes
 * priority, followed by an explicitly requested alarm, then a protection entry. Unknown or
 * incomplete inputs produce [LaunchRequest.None].
 */
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
