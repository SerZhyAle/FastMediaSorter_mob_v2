package com.sza.fastmediasorter.data.broadcast.exchange

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnection
import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** The opening proxy of contract DEVICE-EXCHANGE 27 item DL, fed the relay chunks of `vectors/e2e-v1.json`. */
class SealedRelayProxyTest {

    private val relay: JsonObject by lazy {
        val text = requireNotNull(javaClass.classLoader?.getResource(VECTORS)) { "missing test resource $VECTORS" }
            .readText()
        JsonParser.parseString(text).asJsonObject.getAsJsonObject("relay")
    }
    private val upstream = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    private val closeables = mutableListOf<AutoCloseable>(upstream)

    @After
    fun tearDown() {
        closeables.asReversed().forEach { runCatching { it.close() } }
    }

    @Test
    fun `the vectors' relay chunks come out of the proxy as the clear media in order`() {
        val chunks = relay.getAsJsonArray("chunks").map { it.asJsonObject }
        // The listen body carries chunk payloads without their length and flag (7.6), here in HTTP chunks.
        serveOnce(chunks.map { E2eBytes.fromB64url(it.get("payload").asString) })
        val groupKey = E2eBytes.fromHex(relay.getAsJsonObject("groupKeys").get("link-1").asString)
        val proxy = SealedRelayProxy(
            label = "test",
            path = "/v2/b/x/stream",
            host = "exchange.example",
            broadcastId = relay.get("broadcastId").asString,
            groupKey = groupKey,
        ) {
            ExchangeConnection(Socket(InetAddress.getLoopbackAddress(), upstream.localPort), "")
        }.also(closeables::add)

        val answer = Socket(BroadcastExchangeEndpoints.LOOPBACK_HOST, proxy.port).use { client ->
            client.soTimeout = TIMEOUT_MS
            client.getOutputStream().apply {
                write("GET /v2/b/x/stream HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray(Charsets.US_ASCII))
                flush()
            }
            client.getInputStream().readBytes()
        }

        val split = indexOf(answer, HEAD_END)
        val head = String(answer, 0, split, Charsets.US_ASCII)
        val body = answer.copyOfRange(split + HEAD_END.size, answer.size)
        assertTrue(head, head.startsWith("HTTP/1.1 200"))
        assertTrue(head, head.contains("Content-Type: ${relay.get("mediaType").asString}"))
        val clear = chunks.flatMap { chunk -> chunk.getAsJsonArray("clearMedia").map { it.asString } }
        assertEquals(clear.joinToString(""), E2eBytes.hex(body))
    }

    @Test
    fun closeUnblocksSilentClientBeforeRequestHeadersComplete() {
        val groupKey = ByteArray(32)
        val proxy = SealedRelayProxy(
            label = "test",
            path = "/v2/b/x/stream",
            host = "exchange.example",
            broadcastId = "b-1",
            groupKey = groupKey,
        ) {
            ExchangeConnection(Socket(InetAddress.getLoopbackAddress(), upstream.localPort), "")
        }.also(closeables::add)

        Socket(BroadcastExchangeEndpoints.LOOPBACK_HOST, proxy.port).use { client ->
            client.soTimeout = TIMEOUT_MS
            client.getOutputStream().apply {
                write("GET /v2/b/x/stream HTTP/1.1\r\n".toByteArray(Charsets.US_ASCII))
                flush()
            }
            proxy.close()
            val read = runCatching { client.getInputStream().read() }.getOrDefault(-1)
            assertEquals(-1, read)
        }
    }

    @Test
    fun closeTearsDownActiveUpstreamConnection() {
        val peerAccepted = CountDownLatch(1)
        val peerEOF = CountDownLatch(1)
        thread(isDaemon = true) {
            upstream.accept().use { peer ->
                peer.soTimeout = TIMEOUT_MS
                peerAccepted.countDown()
                runCatching {
                    while (peer.getInputStream().read() >= 0) {
                        // Drain request bytes until proxy.close() closes the upstream socket.
                    }
                }
                peerEOF.countDown()
            }
        }
        val proxy = SealedRelayProxy(
            label = "test",
            path = "/v2/b/x/stream",
            host = "exchange.example",
            broadcastId = "b-1",
            groupKey = ByteArray(32),
        ) {
            ExchangeConnection(Socket(InetAddress.getLoopbackAddress(), upstream.localPort), "")
        }.also(closeables::add)

        Socket(BroadcastExchangeEndpoints.LOOPBACK_HOST, proxy.port).use { client ->
            client.soTimeout = TIMEOUT_MS
            client.getOutputStream().apply {
                write("GET /v2/b/x/stream HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray(Charsets.US_ASCII))
                flush()
            }
            assertTrue(peerAccepted.await(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS))
            proxy.close()
            val read = runCatching { client.getInputStream().read() }.getOrDefault(-1)
            assertEquals(-1, read)
            assertTrue(peerEOF.await(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS))
        }
    }

    private fun serveOnce(payloads: List<ByteArray>) {
        thread(isDaemon = true) {
            upstream.accept().use { peer ->
                val input = peer.getInputStream()
                var line = readLine(input)
                while (line.isNotEmpty()) line = readLine(input)
                val out = ByteArrayOutputStream()
                out.write(
                    "HTTP/1.1 200 OK\r\nContent-Type: application/x-fmsx-sealed\r\nTransfer-Encoding: chunked\r\n\r\n"
                        .toByteArray(Charsets.US_ASCII),
                )
                payloads.forEach { payload ->
                    out.write("${Integer.toHexString(payload.size)}\r\n".toByteArray(Charsets.US_ASCII))
                    out.write(payload)
                    out.write(CRLF)
                }
                out.write("0\r\n\r\n".toByteArray(Charsets.US_ASCII))
                peer.getOutputStream().apply {
                    write(out.toByteArray())
                    flush()
                }
            }
        }
    }

    private fun readLine(input: java.io.InputStream): String {
        val line = StringBuilder()
        var byte = input.read()
        while (byte >= 0 && byte != '\n'.code) {
            if (byte != '\r'.code) line.append(byte.toChar())
            byte = input.read()
        }
        return line.toString()
    }

    private fun indexOf(bytes: ByteArray, wanted: ByteArray): Int =
        (0..bytes.size - wanted.size).first { at -> wanted.indices.all { bytes[at + it] == wanted[it] } }

    private companion object {
        const val VECTORS = "device-exchange/e2e-v1.json"
        const val TIMEOUT_MS = 10_000
        val CRLF = "\r\n".toByteArray(Charsets.US_ASCII)
        val HEAD_END = "\r\n\r\n".toByteArray(Charsets.US_ASCII)
    }
}
