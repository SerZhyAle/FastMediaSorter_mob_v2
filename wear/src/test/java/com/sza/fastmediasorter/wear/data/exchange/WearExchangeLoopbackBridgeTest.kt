package com.sza.fastmediasorter.wear.data.exchange

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/** The pinning proxy of contract 7.7 on the watch: a byte splice on loopback, a closed stream on refusal. */
class WearExchangeLoopbackBridgeTest {

    private val upstreamServer = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    private val closeables = mutableListOf<AutoCloseable>(upstreamServer)

    @After
    fun tearDown() {
        closeables.asReversed().forEach { runCatching { it.close() } }
    }

    @Test
    fun bytesSpliceBothWaysThroughTheLoopbackPort() {
        // The upstream answers every byte it reads, uppercased, so both directions are proven by one round trip.
        thread(isDaemon = true) {
            upstreamServer.accept().use { peer ->
                val buffer = ByteArray(BUFFER)
                val read = peer.getInputStream().read(buffer)
                peer.getOutputStream().write(String(buffer, 0, read).uppercase().toByteArray())
                peer.getOutputStream().flush()
            }
        }
        val bridge = WearExchangeLoopbackBridge("test") {
            WearExchangeConnection(Socket(InetAddress.getLoopbackAddress(), upstreamServer.localPort))
        }.also(closeables::add)

        val answer = Socket(WearExchangeRelayEndpoint.LOOPBACK_HOST, bridge.port).use { client ->
            client.soTimeout = TIMEOUT_MS
            client.getOutputStream().apply {
                write("get /v2/b/x/stream".toByteArray())
                flush()
            }
            val buffer = ByteArray(BUFFER)
            String(buffer, 0, client.getInputStream().read(buffer))
        }

        assertEquals("GET /V2/B/X/STREAM", answer)
    }

    @Test
    fun anUpstreamRefusalClosesTheAcceptedConnection() {
        val bridge = WearExchangeLoopbackBridge("test") { throw IOException("refused") }.also(closeables::add)

        val read = Socket(WearExchangeRelayEndpoint.LOOPBACK_HOST, bridge.port).use { client ->
            client.soTimeout = TIMEOUT_MS
            client.getInputStream().read()
        }

        assertEquals("the player must see a closed stream, never a hang", -1, read)
    }

    @Test
    fun theRequestHeaderIsAddedToTheHeadOfTheRequestExactlyOnce() {
        val received = java.util.concurrent.LinkedBlockingQueue<String>()
        thread(isDaemon = true) {
            upstreamServer.accept().use { peer ->
                // The bridge writes the head and the header apart, so one read may carry only the first.
                val buffer = ByteArray(HEAD_BUFFER)
                var length = 0
                while (!String(buffer, 0, length).endsWith("\r\n\r\n")) {
                    length += peer.getInputStream().read(buffer, length, buffer.size - length)
                }
                received.add(String(buffer, 0, length))
                peer.getOutputStream().write("HTTP/1.1 200 OK\r\n\r\n".toByteArray())
                peer.getOutputStream().flush()
            }
        }
        val bridge = WearExchangeLoopbackBridge("test", "Authorization: Basic abc") {
            WearExchangeConnection(Socket(InetAddress.getLoopbackAddress(), upstreamServer.localPort))
        }.also(closeables::add)

        Socket(WearExchangeRelayEndpoint.LOOPBACK_HOST, bridge.port).use { client ->
            client.soTimeout = TIMEOUT_MS
            client.getOutputStream().apply {
                write("GET /v2/b/x/stream HTTP/1.1\r\nHost: ex.example\r\n\r\n".toByteArray())
                flush()
            }
            client.getInputStream().read()
        }

        val head = received.poll(TIMEOUT_MS.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals(
            "GET /v2/b/x/stream HTTP/1.1\r\nHost: ex.example\r\nAuthorization: Basic abc\r\n\r\n",
            head,
        )
    }

    @Test
    fun aRequestHeadThatNeverEndsClosesTheConnectionWithoutForwarding() {
        val bridge = WearExchangeLoopbackBridge("test", "Authorization: Basic abc") {
            WearExchangeConnection(Socket(InetAddress.getLoopbackAddress(), upstreamServer.localPort))
        }.also(closeables::add)

        val read = Socket(WearExchangeRelayEndpoint.LOOPBACK_HOST, bridge.port).use { client ->
            client.soTimeout = TIMEOUT_MS
            client.getOutputStream().apply {
                write(ByteArray(OVERSIZED_HEAD) { 'a'.code.toByte() })
                flush()
            }
            client.getInputStream().read()
        }

        assertEquals("an oversized head must end in a closed stream", -1, read)
    }

    private companion object {
        const val BUFFER = 64
        const val HEAD_BUFFER = 512
        const val OVERSIZED_HEAD = 9 * 1024
        const val TIMEOUT_MS = 5_000
    }
}
