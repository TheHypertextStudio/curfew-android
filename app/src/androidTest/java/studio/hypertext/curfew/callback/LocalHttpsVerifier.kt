package studio.hypertext.curfew.callback

import androidx.test.platform.app.InstrumentationRegistry
import java.io.Closeable
import java.security.KeyStore
import java.time.Instant
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import org.json.JSONObject
import studio.hypertext.curfew.protocols.CallbackDefinition

/** A loopback TLS peer. Its certificate is trusted only inside this instrumentation process. */
internal class LocalHttpsVerifier : Closeable {
    private val previousFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
    private val executor = Executors.newSingleThreadExecutor()
    private val socket: SSLServerSocket
    @Volatile var definition: CallbackDefinition? = null
    @Volatile var mode = "pending"
    val requests = AtomicInteger()
    private var replayApproval: JSONObject? = null

    init {
        val keys = KeyStore.getInstance("PKCS12").apply {
            InstrumentationRegistry.getInstrumentation().context.assets
                .open("verifier-fixture.p12").use { load(it, "fixture-only".toCharArray()) }
        }
        val keyManager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keys, "fixture-only".toCharArray())
        }
        val trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(keys)
        }
        val tls = SSLContext.getInstance("TLS").apply {
            init(keyManager.keyManagers, trustManager.trustManagers, null)
        }
        HttpsURLConnection.setDefaultSSLSocketFactory(tls.socketFactory)
        socket = tls.serverSocketFactory.createServerSocket(0, 10, java.net.InetAddress.getByName("127.0.0.1")) as SSLServerSocket
        executor.submit {
            while (!socket.isClosed) {
                runCatching { (socket.accept() as SSLSocket).use(::respond) }
            }
        }
    }

    val endpoint get() = "https://localhost:${socket.localPort}/wake"

    private fun respond(client: SSLSocket) {
        client.soTimeout = 5_000
        val reader = client.inputStream.bufferedReader()
        check(reader.readLine().startsWith("POST /wake "))
        var length = 0
        while (true) {
            val header = reader.readLine() ?: error("No request body")
            if (header.isEmpty()) break
            if (header.startsWith("Content-Length:", ignoreCase = true)) length = header.substringAfter(':').trim().toInt()
        }
        require(length in 1..8192)
        val chars = CharArray(length)
        var offset = 0
        while (offset < length) {
            val count = reader.read(chars, offset, length - offset)
            check(count > 0)
            offset += count
        }
        val challenge = JSONObject(String(chars))
        val configured = requireNotNull(definition)
        check(challenge.getString("callbackId") == configured.callbackId)
        val unsignedChallenge = JSONObject(challenge.toString()).apply { remove("mac") }
        check(sign(configured, "curfew-callback-request-v1", unsignedChallenge) == challenge.getString("mac"))
        requests.incrementAndGet()
        val now = Instant.now()
        val responseMode = mode
        val response = JSONObject().apply {
            put("campaignId", if (responseMode == "wrong-occurrence") "another-alarm" else challenge.getString("campaignId"))
            put("nonce", challenge.getString("nonce"))
            put("observedAt", when (responseMode) {
                "expired" -> Instant.parse(challenge.getString("challengedAt"))
                "future" -> now.plusSeconds(60)
                else -> now
            }.toString())
            put("expiresAt", when (responseMode) {
                "expired" -> Instant.parse(challenge.getString("challengedAt")).plusNanos(1)
                "future" -> now.plusSeconds(90)
                else -> now.plusSeconds(30)
            }.toString())
            put("status", if (responseMode in setOf("pending", "replay-seed")) "pending" else "satisfied")
        }
        if (responseMode == "replay-seed") {
            replayApproval = JSONObject(response.toString()).apply {
                put("status", "satisfied")
                put("mac", sign(configured, "curfew-callback-response-v1", this))
            }
        }
        response.put("mac", if (responseMode == "forged") "invalid" else sign(configured, "curfew-callback-response-v1", response))
        val body = when (responseMode) {
            "malformed" -> "{}"
            "replayed-approval" -> requireNotNull(replayApproval).toString()
            else -> response.toString()
        }
        val bytes = body.toByteArray()
        val status = if (responseMode == "unavailable") "503 Service Unavailable" else "200 OK"
        client.outputStream.write("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
        client.outputStream.write(bytes)
        client.outputStream.flush()
    }

    private fun sign(definition: CallbackDefinition, info: String, body: JSONObject): String {
        fun hmac(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256")); doFinal(data)
        }
        val extracted = hmac(definition.callbackId.toByteArray(), Base64.getUrlDecoder().decode(definition.secret))
        val key = hmac(extracted, info.toByteArray() + byteArrayOf(1))
        val canonical = body.keys().asSequence().toList().sorted().joinToString(",", "{", "}") {
            JSONObject.quote(it) + ":" + JSONObject.quote(body.getString(it))
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(key, canonical.toByteArray()))
    }

    override fun close() {
        socket.close()
        executor.shutdownNow()
        HttpsURLConnection.setDefaultSSLSocketFactory(previousFactory)
    }
}
