package studio.hypertext.curfew.account

import java.net.URI

/**
 * Single source for the native OAuth redirect used by registration, the browser, token
 * exchange, and launch parsing. Match the raw authority and path so a lookalike host, port, or
 * encoded path cannot be mistaken for this callback.
 */
internal object AccountOAuthRedirect {
    private const val SCHEME = "studio.hypertext.curfew"
    private const val HOST = "oauth"
    private const val PATH = "/callback"
    const val URL = "$SCHEME://$HOST$PATH"

    fun matches(uri: URI): Boolean =
        uri.scheme == SCHEME && uri.rawAuthority == HOST && uri.rawPath == PATH
}
