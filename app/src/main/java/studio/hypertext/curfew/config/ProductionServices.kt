package studio.hypertext.curfew.config

import java.net.URI

/** First-party authorities from curfew/deployment/environments.json. */
object ProductionServices {
    const val ACCOUNT_ORIGIN = "https://account.thecurfew.app"
    const val SYNC_ORIGIN = "https://sync.thecurfew.app"
    const val ACCOUNT_PORTAL = "$ACCOUNT_ORIGIN/account"
    const val SYNC_SOCKET_ORIGIN = "wss://sync.thecurfew.app"
    const val OAUTH_CLIENT_CACHE_KEY = "oauth:dynamic-client-id:$ACCOUNT_ORIGIN"

    const val ACCESS_TOKEN_KEY = "account:access-token:$ACCOUNT_ORIGIN"
    const val REFRESH_TOKEN_KEY = "account:refresh-token:$ACCOUNT_ORIGIN"
    const val ROOT_KEY = "account:root-key:$ACCOUNT_ORIGIN"
    const val PENDING_RECOVERY_KEY = "account:pending-recovery-key:$ACCOUNT_ORIGIN"

    fun isAccountIssuer(endpoint: URI): Boolean = endpoint.toASCIIString() == ACCOUNT_ORIGIN

    fun isSyncRequest(endpoint: URI): Boolean = hasSyncAuthority(endpoint, "https")

    fun isSyncSocket(endpoint: URI): Boolean = hasSyncAuthority(endpoint, "wss")

    private fun hasSyncAuthority(
        endpoint: URI,
        scheme: String,
    ): Boolean =
        endpoint.scheme == scheme &&
            endpoint.rawAuthority == "sync.thecurfew.app" &&
            endpoint.rawFragment == null &&
            endpoint.rawPath.orEmpty().startsWith("/")
}
