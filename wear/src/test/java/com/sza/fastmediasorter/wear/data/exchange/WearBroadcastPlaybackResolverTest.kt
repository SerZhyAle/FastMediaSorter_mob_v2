package com.sza.fastmediasorter.wear.data.exchange

import android.net.Network
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastPlayback
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastPlayback.Reason
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** What a tap on a listed broadcast resolves to (contract DEVICE-EXCHANGE 7.7, items CH and CI; LIVE-BROADCAST M). */
class WearBroadcastPlaybackResolverTest {

    private val bridges = WearExchangeRelayBridges(
        object : WearExchangeConnector {
            override fun connect(
                host: String,
                port: Int,
                pinned: String?,
                pinnedKey: String?,
                readTimeoutMs: Int,
                network: Network?,
            ): WearExchangeConnection =
                throw IOException("no server in a unit test")
        },
    )
    private var lanAnswers = true

    private val resolver = WearBroadcastPlaybackResolver(
        bridges,
        object : WearLanEndpointProbe {
            override fun answers(endpoint: WearBroadcastEndpoint.Lan): Boolean = lanAnswers
        },
    )

    @After
    fun tearDown() {
        bridges.closeAll()
    }

    @Test
    fun `a reachable LAN endpoint wins over the relay and keeps its address`() {
        val playback = resolve(record(descriptor = LAN_AND_RELAY, mode = "AUDIO_ONLY"))

        val playable = playback as WearBroadcastPlayback.Playable
        assertEquals("http://10.0.0.2:8080/", playable.url)
        assertFalse(playable.isVideo)
    }

    @Test
    fun `a LAN endpoint that does not answer falls to the pinned relay on loopback`() {
        lanAnswers = false

        val playable = resolve(record(descriptor = LAN_AND_RELAY, mode = "VIDEO_AUDIO")) as
            WearBroadcastPlayback.Playable

        assertTrue(playable.url.startsWith("http://127.0.0.1:"))
        assertTrue(playable.url.endsWith("/v2/b/b1/stream"))
        assertTrue(playable.isVideo)
    }

    @Test
    fun `a mode the watch does not decode is unavailable and never played`() {
        val playback = resolve(record(descriptor = LAN_AND_RELAY, mode = "HOLOGRAM"))

        assertEquals(WearBroadcastPlayback.Unavailable(Reason.MODE), playback)
    }

    @Test
    fun `a record with no endpoint the watch implements is unavailable`() {
        val tunnelOnly = """{"endpoints":[{"url":"fmsx://ex.example/b/b1/rtsp","transport":"TUNNEL"}]}"""

        assertEquals(
            WearBroadcastPlayback.Unavailable(Reason.NO_ENDPOINT),
            resolve(record(descriptor = tunnelOnly, mode = "AUDIO_ONLY")),
        )
    }

    @Test
    fun `no endpoint answering is unreachable`() {
        lanAnswers = false
        val lanOnly = """{"endpoints":[{"url":"http://10.0.0.2:8080/","transport":"HTTP"}]}"""

        assertEquals(
            WearBroadcastPlayback.Unavailable(Reason.UNREACHABLE),
            resolve(record(descriptor = lanOnly, mode = "AUDIO_ONLY")),
        )
    }

    @Test
    fun `a protected broadcast is never played from the LAN even when it answers`() {
        val protectedLanOnly = """{"auth":{"scheme":"password-v1","user":"u"},""" +
            """"endpoints":[{"url":"http://10.0.0.2:8080/","transport":"HTTP"}]}"""

        val credentials = WearBroadcastCredentials("u", "p")

        val playback = resolve(record(protectedLanOnly, mode = "AUDIO_ONLY", credentials = credentials))

        assertEquals(WearBroadcastPlayback.Unavailable(Reason.PROTECTED), playback)
    }

    @Test
    fun `a protected broadcast without credentials is unavailable and nothing is asked on the watch`() {
        val protectedRelay = """{"auth":{"scheme":"password-v1","user":"u"},""" +
            """"endpoints":[{"url":"https://ex.example:7443/v2/b/b1/stream","transport":"RELAY"}]}"""

        val playback = resolve(record(descriptor = protectedRelay, mode = "AUDIO_ONLY"))

        assertEquals(WearBroadcastPlayback.Unavailable(Reason.PROTECTED), playback)
    }

    @Test
    fun `a protected broadcast with credentials plays on the relay and the url carries no password`() {
        val protectedRelay = """{"auth":{"scheme":"password-v1","user":"alice"},""" +
            """"endpoints":[{"url":"https://ex.example:7443/v2/b/b1/stream","transport":"RELAY"}]}"""

        val credentials = WearBroadcastCredentials("alice", "s3cret")

        val playable = resolve(record(protectedRelay, mode = "AUDIO_ONLY", credentials = credentials)) as
            WearBroadcastPlayback.Playable

        assertFalse(playable.url.contains("s3cret"))
        assertFalse(playable.url.contains("alice"))
        assertFalse(playable.url.contains("@"))
    }

    @Test
    fun `a sealed relay whose key never arrived is never played`() {
        val keyless = """{"endpoints":[{"url":"https://ex.example:7443/v2/b/b1/stream","transport":"RELAY",""" +
            """"seal":{"v":1}}]}"""

        assertEquals(
            WearBroadcastPlayback.Unavailable(Reason.NO_ENDPOINT),
            resolve(record(descriptor = keyless, mode = "AUDIO_ONLY")),
        )
    }

    @Test
    fun `a sealed relay endpoint resolves to the decrypting proxy, which plays the clear media`() {
        val vectors = relayVectors()
        val broadcastId = vectors.get("broadcastId").asString
        val chunks = vectors.getAsJsonArray("chunks").map { it.asJsonObject }
        val upstream = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        serveOnce(upstream, chunks.map { E2eBytes.fromB64url(it.get("payload").asString) })
        val sealedBridges = WearExchangeRelayBridges(
            object : WearExchangeConnector {
                override fun connect(
                    host: String,
                    port: Int,
                    pinned: String?,
                    pinnedKey: String?,
                    readTimeoutMs: Int,
                    network: Network?,
                ): WearExchangeConnection =
                    WearExchangeConnection(Socket(InetAddress.getLoopbackAddress(), upstream.localPort))
            },
        )
        val groupKey = E2eBytes.b64url(E2eBytes.fromHex(vectors.getAsJsonObject("groupKeys").get("link-1").asString))
        val descriptor = """{"endpoints":[{"url":"https://ex.example:7443/v2/b/$broadcastId/stream",""" +
            """"transport":"RELAY","seal":{"v":1,"key":"$groupKey"}}]}"""
        val sealedResolver = WearBroadcastPlaybackResolver(
            sealedBridges,
            object : WearLanEndpointProbe {
                override fun answers(endpoint: WearBroadcastEndpoint.Lan): Boolean = false
            },
        )

        try {
            val playable = runBlocking {
                sealedResolver.resolve(record(descriptor, "AUDIO_ONLY").copy(broadcastId = broadcastId))
            } as WearBroadcastPlayback.Playable
            val port = URI(playable.url).port
            val answer = Socket(WearExchangeRelayEndpoint.LOOPBACK_HOST, port).use { client ->
                client.soTimeout = TIMEOUT_MS
                client.getOutputStream().apply {
                    write("GET / HTTP/1.1\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    flush()
                }
                client.getInputStream().readBytes()
            }

            val text = String(answer, Charsets.ISO_8859_1)
            val body = answer.copyOfRange(text.indexOf("\r\n\r\n") + HEAD_END_LENGTH, answer.size)
            assertTrue(text.startsWith("HTTP/1.1 200"))
            val clear = chunks.flatMap { chunk -> chunk.getAsJsonArray("clearMedia").map { it.asString } }
            assertEquals(clear.joinToString(""), E2eBytes.hex(body))
        } finally {
            sealedBridges.closeAll()
            upstream.close()
        }
    }

    @Test
    fun `a LAN server answering 200 serves, and the probe asks it with a GET for its path`() {
        withLanServer("HTTP/1.1 200 OK\r\nContent-Type: audio/aac\r\n\r\n") { port, request ->
            assertTrue(socketProbe().answers(lanEndpoint(port)))
            assertTrue(request().startsWith("GET /live-audio.aac HTTP/1.1\r\n"))
        }
    }

    @Test
    fun `a LAN server answering 404 does not serve`() {
        withLanServer("HTTP/1.1 404 Not Found\r\n\r\n") { port, _ ->
            assertFalse(socketProbe().answers(lanEndpoint(port)))
        }
    }

    @Test
    fun `a LAN server that accepts the connection and stays silent does not serve`() {
        withLanServer(reply = null) { port, _ ->
            assertFalse(socketProbe().answers(lanEndpoint(port)))
        }
    }

    @Test
    fun `a LAN peer streaming bytes with no line end does not serve`() {
        withLanServer("x".repeat(PROBE_NOISE_LENGTH)) { port, _ ->
            assertFalse(socketProbe().answers(lanEndpoint(port)))
        }
    }

    @Test
    fun `a LAN port nothing listens on does not serve`() {
        val closed = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }

        val endpoint = WearBroadcastEndpoint.Lan("http://127.0.0.1:$closed/", "127.0.0.1", closed, "AUDIO_ONLY")

        assertFalse(socketProbe().answers(endpoint))
    }

    @Test
    fun `an RTSP LAN endpoint is asked with DESCRIBE and serves on its 200`() {
        withLanServer("RTSP/1.0 200 OK\r\nCSeq: 1\r\n\r\n") { port, request ->
            val endpoint = lanEndpoint(port, scheme = "rtsp", path = "/")

            assertTrue(socketProbe().answers(endpoint))
            assertTrue(request().startsWith("DESCRIBE ${endpoint.url} RTSP/1.0\r\n"))
        }
    }

    @Test
    fun `a LAN endpoint that accepts and stays silent sends the resolver on to the relay`() {
        withLanServer(reply = null) { port, _ ->
            val descriptor = """{"endpoints":[{"url":"${lanEndpoint(port).url}","transport":"HTTP"},""" +
                """{"url":"https://ex.example:7443/v2/b/b1/stream","transport":"RELAY"}]}"""

            val playable = realProbeResolver().resolveBlocking(record(descriptor, "AUDIO_ONLY")) as
                WearBroadcastPlayback.Playable

            assertTrue(playable.url.startsWith("http://127.0.0.1:"))
            assertTrue(playable.url.endsWith("/v2/b/b1/stream"))
        }
    }

    @Test
    fun `a LAN endpoint that serves keeps its address when the real probe judges it`() {
        withLanServer("HTTP/1.1 200 OK\r\n\r\n") { port, _ ->
            val lan = lanEndpoint(port)
            val descriptor = """{"endpoints":[{"url":"${lan.url}","transport":"HTTP"},""" +
                """{"url":"https://ex.example:7443/v2/b/b1/stream","transport":"RELAY"}]}"""

            val playable = realProbeResolver().resolveBlocking(record(descriptor, "AUDIO_ONLY")) as
                WearBroadcastPlayback.Playable

            assertEquals(lan.url, playable.url)
        }
    }

    private fun socketProbe() = SocketWearLanEndpointProbe(PROBE_CONNECT_MS, PROBE_STATUS_MS)

    private fun realProbeResolver() = WearBroadcastPlaybackResolver(bridges, socketProbe())

    private fun WearBroadcastPlaybackResolver.resolveBlocking(record: WearDirectoryBroadcast) =
        runBlocking { resolve(record) }

    private fun lanEndpoint(port: Int, scheme: String = "http", path: String = "/live-audio.aac") =
        WearBroadcastEndpoint.Lan(
            url = "$scheme://127.0.0.1:$port$path",
            host = "127.0.0.1",
            port = port,
            mode = "AUDIO_ONLY",
        )

    /**
     * A loopback server for one connection: it reads the request head, then sends [reply], or nothing when null and
     * keeps the connection open. [body] gets the port and a reader of the head the server received.
     */
    private fun <T> withLanServer(reply: String?, body: (port: Int, request: () -> String) -> T): T {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val peers = CopyOnWriteArrayList<Socket>()
        val request = AtomicReference("")
        thread(isDaemon = true) { serveOneLanPeer(server, peers, request, reply) }
        return try {
            body(server.localPort) { request.get() }
        } finally {
            peers.forEach { runCatching(it::close) }
            server.close()
        }
    }

    private fun serveOneLanPeer(
        server: ServerSocket,
        peers: MutableList<Socket>,
        request: AtomicReference<String>,
        reply: String?,
    ) {
        try {
            val peer = server.accept()
            peers += peer
            request.set(readHead(peer.getInputStream()))
            reply?.let { text ->
                peer.getOutputStream().apply {
                    write(text.toByteArray(Charsets.US_ASCII))
                    flush()
                }
            }
        } catch (expected: IOException) {
            // The test closed the server while the thread was still accepting or writing.
        }
    }

    private fun readHead(input: InputStream): String {
        val head = StringBuilder()
        while (!head.endsWith("\r\n\r\n")) {
            val next = input.read()
            if (next < 0) {
                break
            }
            head.append(next.toChar())
        }
        return head.toString()
    }

    private fun relayVectors(): JsonObject {
        val text = requireNotNull(javaClass.classLoader?.getResource(VECTORS)) { "missing test resource $VECTORS" }
            .readText()
        return JsonParser.parseString(text).asJsonObject.getAsJsonObject("relay")
    }

    // The relay's listen body: chunk payloads without their length and flag (7.6), in HTTP chunks.
    private fun serveOnce(upstream: ServerSocket, payloads: List<ByteArray>) {
        thread(isDaemon = true) {
            upstream.accept().use { peer ->
                val input = peer.getInputStream()
                var blankLines = 0
                var previous = 0
                while (blankLines == 0) {
                    val byte = input.read()
                    if (byte < 0 || (byte == '\n'.code && previous == '\n'.code)) blankLines++
                    if (byte != '\r'.code) previous = byte
                }
                val out = ByteArrayOutputStream()
                out.write("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n".toByteArray(Charsets.US_ASCII))
                payloads.forEach { payload ->
                    out.write("${Integer.toHexString(payload.size)}\r\n".toByteArray(Charsets.US_ASCII))
                    out.write(payload)
                    out.write("\r\n".toByteArray(Charsets.US_ASCII))
                }
                out.write("0\r\n\r\n".toByteArray(Charsets.US_ASCII))
                peer.getOutputStream().apply {
                    write(out.toByteArray())
                    flush()
                }
            }
        }
    }

    private fun resolve(record: WearDirectoryBroadcast): WearBroadcastPlayback =
        runBlocking { resolver.resolve(record) }

    @Test
    fun `a record with an unsupported higher descriptor schemaVersion resolves to FORMAT_UNSUPPORTED`() {
        val higherSchema = """{"schemaVersion":2,"endpoints":[{"url":"http://10.0.0.2:8080/","transport":"HTTP"}]}"""
        val playback = resolve(record(descriptor = higherSchema, mode = "AUDIO_ONLY"))

        val unavailable = playback as WearBroadcastPlayback.Unavailable
        assertEquals(Reason.FORMAT_UNSUPPORTED, unavailable.reason)
    }

    private fun record(
        descriptor: String,
        mode: String,
        credentials: WearBroadcastCredentials? = null,
    ) = WearDirectoryBroadcast(
        broadcastId = "b1",
        deviceId = "d1",
        title = "Mic",
        mode = mode,
        descriptor = JsonParser.parseString(descriptor).asJsonObject,
        credentials = credentials,
    )

    private companion object {
        const val VECTORS = "device-exchange/e2e-v1.json"
        const val TIMEOUT_MS = 10_000
        const val HEAD_END_LENGTH = 4
        const val PROBE_CONNECT_MS = 1_000
        const val PROBE_STATUS_MS = 400
        const val PROBE_NOISE_LENGTH = 512
        const val LAN_AND_RELAY = """{"endpoints":[""" +
            """{"url":"http://10.0.0.2:8080/","transport":"HTTP"},""" +
            """{"url":"https://ex.example:7443/v2/b/b1/stream","transport":"RELAY","certFingerprint":"SHA256:p"}]}"""
    }
}
