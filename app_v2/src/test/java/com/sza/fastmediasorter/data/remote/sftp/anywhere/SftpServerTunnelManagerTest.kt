package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.sza.fastmediasorter.domain.model.SftpExchangeConfig
import com.sza.fastmediasorter.domain.model.SftpTunnelState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * The producer against a fake exchange server on loopback, speaking the contract wire in plain frames
 * (TLS is the connector's concern and is replaced here).
 */
class SftpServerTunnelManagerTest {

    private val exchange = ServerSocket(0)
    private val localServer = ServerSocket(0)
    private val connections = AtomicInteger()
    private val pins = mutableListOf<String>()

    private val config = SftpExchangeConfig(
        enabled = true,
        host = "127.0.0.1",
        port = exchange.localPort,
        password = "Passw0rdTest",
        shareId = "AAAAAAAAAAAAAAAAAAAAAA",
        pinnedCertificate = null,
    )

    private val connector = object : ExchangeConnector {
        override fun connect(host: String, port: Int, pinnedCertificate: String?): ExchangeConnection {
            connections.incrementAndGet()
            return ExchangeConnection(Socket(host, port), FAKE_PIN)
        }
    }

    private val source = object : SftpExchangeConfigSource {
        override suspend fun snapshot(): SftpExchangeConfig = config

        override suspend fun pinCertificate(fingerprint: String) {
            pins += fingerprint
        }
    }

    // The manager runs in the tests own runBlocking scope, so every coroutine it starts is joined
    // before the test returns.
    private fun withManager(block: suspend (SftpServerTunnelManager) -> Unit) = runBlocking(Dispatchers.IO) {
        val manager = SftpServerTunnelManager(connector, source, this, Dispatchers.IO)
        try {
            block(manager)
        } finally {
            manager.stop()
        }
    }

    @After
    fun tearDown() {
        exchange.close()
        localServer.close()
    }

    @Test
    fun `registers, then splices an opened tunnel to the local server`() = withManager { manager ->
        val register = ArrayBlockingQueue<ExchangeEnvelope>(1)
        val attach = ArrayBlockingQueue<ExchangeEnvelope>(1)
        val echoed = ArrayBlockingQueue<String>(1)
        thread(isDaemon = true) { echoOnce(localServer) }
        thread(isDaemon = true) {
            val control = exchange.accept()
            register.put(checkNotNull(ExchangeFrameCodec.read(control.getInputStream())))
            ExchangeFrameCodec.write(
                control.getOutputStream(),
                ExchangeEnvelope(
                    ExchangeEnvelope.TYPE_REGISTERED,
                    keepaliveSeconds = 30,
                    port = 40007,
                    resumeToken = TOKEN,
                ),
            )
            val open = ExchangeEnvelope(ExchangeEnvelope.TYPE_OPEN, tunnelId = TOKEN)
            ExchangeFrameCodec.write(control.getOutputStream(), open)
            val data = exchange.accept()
            attach.put(checkNotNull(ExchangeFrameCodec.read(data.getInputStream())))
            data.getOutputStream().apply {
                write(PAYLOAD.toByteArray())
                flush()
            }
            val reply = ByteArray(PAYLOAD.length)
            var offset = 0
            while (offset < reply.size) offset += data.getInputStream().read(reply, offset, reply.size - offset)
            echoed.put(String(reply))
        }

        manager.start(localServer.localPort, listOf("192.168.1.5:2222"))

        val sent = checkNotNull(register.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals("Passw0rdTest", sent.password)
        assertEquals(config.shareId, sent.shareId)
        assertEquals(0, sent.port)
        assertEquals("192.168.1.5:2222", sent.claim?.getAsJsonArray("endpoints")?.get(0)?.asString)
        val attached = checkNotNull(attach.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(config.shareId, attached.shareId)
        assertEquals(TOKEN, attached.tunnelId)
        assertEquals(PAYLOAD, echoed.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(SftpTunnelState.Registered(40007), manager.state.value)
        assertEquals(listOf(FAKE_PIN), pins)
    }

    @Test
    fun `a wrong password ends the retries`() = withManager { manager ->
        thread(isDaemon = true) {
            val control = exchange.accept()
            ExchangeFrameCodec.read(control.getInputStream())
            ExchangeFrameCodec.write(
                control.getOutputStream(),
                ExchangeEnvelope(ExchangeEnvelope.TYPE_REFUSED, reason = ExchangeEnvelope.REASON_BAD_PASSWORD),
            )
        }

        manager.start(localServer.localPort, emptyList())

        val refused = withTimeout(WAIT_SECONDS * 1_000) { manager.state.first { it is SftpTunnelState.Refused } }
        assertEquals(SftpTunnelState.Refused(ExchangeEnvelope.REASON_BAD_PASSWORD), refused)
        Thread.sleep(RETRY_WINDOW_MS)
        assertEquals(1, connections.get())
        assertTrue(manager.state.value is SftpTunnelState.Refused)
    }

    private fun echoOnce(server: ServerSocket) {
        server.accept().use { socket ->
            val buffer = ByteArray(PAYLOAD.length)
            var offset = 0
            while (offset < buffer.size) offset += socket.getInputStream().read(buffer, offset, buffer.size - offset)
            socket.getOutputStream().apply {
                write(buffer)
                flush()
            }
            socket.getInputStream().read()
        }
    }

    private companion object {
        const val FAKE_PIN = "SHA256:fake"
        const val TOKEN = "BBBBBBBBBBBBBBBBBBBBBB"
        const val PAYLOAD = "SSH-2.0-test\r\n"
        const val WAIT_SECONDS = 10L
        const val RETRY_WINDOW_MS = 3_000L
    }
}
