package studio.hypertext.curfew.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class ProductionServicesTest {
    @Test
    fun `HTTPS proof and websocket authorities match the same production service`() {
        assertTrue(ProductionServices.isSyncRequest(URI("${ProductionServices.SYNC_ORIGIN}/sync/socket?campaignId=one")))
        assertTrue(ProductionServices.isSyncSocket(URI("${ProductionServices.SYNC_SOCKET_ORIGIN}/sync/socket?campaignId=one")))
        assertTrue(ProductionServices.OAUTH_CLIENT_CACHE_KEY.endsWith(ProductionServices.ACCOUNT_ORIGIN))
    }

    @Test
    fun `websocket transport rejects credentials ports fragments and lookalikes`() {
        listOf(
            "wss://curfew-sync.hypertext.studio/sync/socket",
            "wss://user@sync.thecurfew.app/sync/socket",
            "wss://sync.thecurfew.app:444/sync/socket",
            "wss://sync.thecurfew.app.attacker.example/sync/socket",
            "wss://sync.thecurfew.app/sync/socket#unexpected",
            "ws://sync.thecurfew.app/sync/socket",
        ).forEach { endpoint -> assertFalse(endpoint, ProductionServices.isSyncSocket(URI(endpoint))) }
    }
}
