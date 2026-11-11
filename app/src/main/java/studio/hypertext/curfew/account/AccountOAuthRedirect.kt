package studio.hypertext.curfew.account

import java.net.URI

internal object AccountOAuthRedirect {
    private const val SCHEME = "studio.hypertext.curfew"
    private const val HOST = "oauth"
    private const val PATH = "/callback"
    const val URL = "$SCHEME://$HOST$PATH"

    fun matches(uri: URI): Boolean =
        uri.scheme == SCHEME && uri.rawAuthority == HOST && uri.rawPath == PATH
}
