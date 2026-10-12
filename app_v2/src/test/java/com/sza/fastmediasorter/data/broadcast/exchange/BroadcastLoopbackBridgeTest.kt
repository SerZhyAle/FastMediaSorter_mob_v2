package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class BroadcastLoopbackBridgeTest {

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
                val input = peer.getInputStream()
                val output = peer.getOutputStream()
                val buffer = ByteArray(BUFFER)
                val read = input.read(buffer)
                output.write(String(buffer, 0, read).uppercase().toByteArray())
                output.flush()
            }
        }
        val bridge = BroadcastLoopbackBridge("test") {
            ExchangeConnection(Socket(InetAddress.getLoopbackAddress(), upstreamServer.localPort), "")
        }.also(closeables::add)

        val answer = Socket(BroadcastExchangeEndpoints.LOOPBACK_HOST, bridge.port).use { client ->
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
        val bridge = BroadcastLoopbackBridge("test") { throw IOException("refused") }.also(closeables::add)

        val read = Socket(BroadcastExchangeEndpoints.LOOPBACK_HOST, bridge.port).use { client ->
            client.soTimeout = TIMEOUT_MS
            client.getInputStream().read()
        }

        assertEquals("the player must see a closed stream, never a hang", -1, read)
    }

    private companion object {
        const val BUFFER = 64
        const val TIMEOUT_MS = 5_000
    }
}
