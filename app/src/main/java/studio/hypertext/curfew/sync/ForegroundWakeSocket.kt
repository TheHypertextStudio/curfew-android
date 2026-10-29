package studio.hypertext.curfew.sync

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import studio.hypertext.curfew.config.ProductionServices
import java.net.URI

class ForegroundWakeSocket(
    private val client: OkHttpClient = OkHttpClient(),
) {
    private var webSocket: WebSocket? = null

    fun connect(
        endpoint: URI,
        bearerToken: String,
        deviceId: String,
        deviceProof: String,
        onCiphertextRecordAvailable: () -> Unit,
        onDisconnected: () -> Unit,
    ) {
        require(ProductionServices.isSyncSocket(endpoint))
        require(bearerToken.isNotBlank())
        require(deviceId.isNotBlank())
        require(deviceProof.isNotBlank())
        close()
        val request =
            Request
                .Builder()
                .url(endpoint.toASCIIString())
                .header("Authorization", "Bearer $bearerToken")
                .header("X-Curfew-Device-ID", deviceId)
                .header("DPoP", deviceProof)
                .build()
        webSocket =
            client.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        // The socket is only an acceleration signal; ciphertext is fetched and
                        // authenticated through the versioned sync channel.
                        if (text.length <= 32 * 1024) onCiphertextRecordAvailable()
                    }

                    override fun onClosing(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        webSocket.close(code, null)
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        onDisconnected()
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        onDisconnected()
                    }
                },
            )
    }

    fun close() {
        webSocket?.close(1000, null)
        webSocket = null
    }
}
