package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAccountClient
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeEnvelope
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeFrameCodec
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeTokenStore
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource
import com.sza.fastmediasorter.domain.model.SftpTunnelState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * The producer of contract DEVICE-EXCHANGE 7.3-7.5 against a fake exchange server on loopback, speaking
 * plain frames (TLS is the connector's concern and is replaced here).
 */
class SftpServerTunnelManagerTest {

    private val exchange = ServerSocket(0)
    private val localServer = ServerSocket(0)
    private val connections = AtomicInteger()

    private val entry = DeviceExchangeAccountEntry(
        host = "127.0.0.1",
        port = exchange.localPort,
        login = "owner",
        deviceId = DEVICE_ID,
        pinnedCertificate = null,
    )
    private val producer = SftpExchangeProducer(entry, SHARE_ID)
    private val record = SftpRendezvousResource(
        resourceId = RESOURCE_ID,
        deviceId = DEVICE_ID,
        kind = SftpRendezvousResource.KIND_SFTP_SHARE,
        name = "Pixel",
        descriptor = "FMSSFTP1:test",
        updatedAtMs = 0,
        writtenAtMs = 0,
        ttlSeconds = 0,
        shareId = SHARE_ID,
        publicPort = 0,
    )

    private val connector = object : ExchangeConnector {
        override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection {
            connections.incrementAndGet()
            return ExchangeConnection(Socket(host, port), "SHA256:fake")
        }
    }

    private val tokens = object : DeviceExchangeTokenStore {
        override suspend fun token(entry: DeviceExchangeAccountEntry): String = TOKEN

        override suspend fun save(entry: DeviceExchangeAccountEntry, token: String): Boolean = true

        override suspend fun forget(entry: DeviceExchangeAccountEntry) = Unit
    }

    private val source = object : SftpExchangeProducerSource {
        override suspend fun producer(): SftpExchangeProducer = producer

        override suspend fun shareRecord(
            producer: SftpExchangeProducer,
            port: Int,
            hosts: List<String>,
        ): SftpRendezvousResource = record.copy(descriptor = "FMSSFTP1:${hosts.joinToString()}:$port")
    }

    // The manager runs in the test's own runBlocking scope, so every coroutine it starts is joined
    // before the test returns.
    private fun withManager(block: suspend (SftpServerTunnelManager) -> Unit) = runBlocking(Dispatchers.IO) {
        val manager =
            SftpServerTunnelManager(DeviceExchangeAccountClient(connector, tokens), source, this, Dispatchers.IO)
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
    fun `publishes the share, then splices an opened tunnel to the local server`() = withManager { manager ->
        val control = ArrayBlockingQueue<DeviceExchangeEnvelope>(2)
        val data = ArrayBlockingQueue<DeviceExchangeEnvelope>(2)
        val echoed = ArrayBlockingQueue<String>(1)
        thread(isDaemon = true) { echoOnce(localServer) }
        thread(isDaemon = true) {
            val stream = exchange.accept()
            control.put(read(stream))
            write(
                stream,
                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME) { addProperty("keepaliveSeconds", 30) }
            )
            control.put(read(stream))
            write(stream, published(40007))
            write(
                stream,
                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_OPEN) {
                    addProperty("tunnelId", TUNNEL_ID)
                    addProperty("resourceId", RESOURCE_ID)
                }
            )
            val tunnel = exchange.accept()
            data.put(read(tunnel))
            write(tunnel, DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME))
            data.put(read(tunnel))
            tunnel.getOutputStream().apply {
                write(PAYLOAD.toByteArray())
                flush()
            }
            val reply = ByteArray(PAYLOAD.length)
            readFully(tunnel.getInputStream(), reply)
            echoed.put(String(reply))
        }

        manager.start(localServer.localPort, listOf("192.168.1.5"))

        val hello = poll(control)
        assertEquals(DeviceExchangeEnvelope.TYPE_HELLO, hello.type)
        assertEquals(TOKEN, hello.string("deviceToken"))
        val publish = poll(control)
        assertEquals(DeviceExchangeEnvelope.TYPE_PUBLISH, publish.type)
        val resource = checkNotNull(publish.obj("resource"))
        assertEquals(RESOURCE_ID, resource.get("resourceId").asString)
        assertEquals(SHARE_ID, resource.get("shareId").asString)
        assertEquals(0, resource.get("publicPort").asInt)
        assertEquals(
            "FMSSFTP1:192.168.1.5:${localServer.localPort}",
            resource.getAsJsonObject("access").get("descriptor").asString
        )
        assertEquals(DeviceExchangeEnvelope.TYPE_HELLO, poll(data).type)
        val attach = poll(data)
        assertEquals(DeviceExchangeEnvelope.TYPE_ATTACH, attach.type)
        assertEquals(TUNNEL_ID, attach.string("tunnelId"))
        assertEquals(PAYLOAD, echoed.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(SftpTunnelState.Registered(40007), manager.state.value)
    }

    @Test
    fun `publishes again after the control stream comes back`() = withManager { manager ->
        val publishes = ArrayBlockingQueue<DeviceExchangeEnvelope>(2)
        thread(isDaemon = true) {
            repeat(2) { round ->
                exchange.accept().use { stream ->
                    read(stream)
                    write(stream, DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME))
                    publishes.put(read(stream))
                    write(stream, published(0))
                    // The first stream drops as a restarted server would; the second is held open.
                    if (round == 1) stream.getInputStream().read()
                }
            }
        }

        manager.start(localServer.localPort, emptyList())

        assertEquals(DeviceExchangeEnvelope.TYPE_PUBLISH, poll(publishes).type)
        val again = checkNotNull(publishes.poll(WAIT_SECONDS + RECONNECT_SECONDS, TimeUnit.SECONDS))
        assertEquals(RESOURCE_ID, again.obj("resource")?.get("resourceId")?.asString)
        assertEquals(2, connections.get())
    }

    @Test
    fun `unpublishes and says bye when the server stops`() = withManager { manager ->
        val farewell = ArrayBlockingQueue<DeviceExchangeEnvelope>(2)
        val registered = ArrayBlockingQueue<Boolean>(1)
        thread(isDaemon = true) {
            exchange.accept().use { stream ->
                read(stream)
                write(stream, DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME))
                read(stream)
                write(stream, published(0))
                registered.put(true)
                farewell.put(read(stream))
                farewell.put(read(stream))
            }
        }

        manager.start(localServer.localPort, emptyList())
        checkNotNull(registered.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        withTimeout(WAIT_SECONDS * MILLIS) { manager.state.first { it is SftpTunnelState.Registered } }
        manager.stop()

        val unpublish = poll(farewell)
        assertEquals(DeviceExchangeEnvelope.TYPE_UNPUBLISH, unpublish.type)
        assertEquals(RESOURCE_ID, unpublish.string("resourceId"))
        assertEquals(DeviceExchangeEnvelope.TYPE_BYE, poll(farewell).type)
        assertEquals(SftpTunnelState.Off, manager.state.value)
    }

    @Test
    fun `refused credentials end the retries`() = withManager { manager ->
        thread(isDaemon = true) {
            exchange.accept().use { stream ->
                read(stream)
                write(stream, DeviceExchangeEnvelope.refused(DeviceExchangeEnvelope.REASON_BAD_CREDENTIALS))
            }
        }

        manager.start(localServer.localPort, emptyList())

        val refused = withTimeout(WAIT_SECONDS * MILLIS) { manager.state.first { it is SftpTunnelState.Refused } }
        assertEquals(SftpTunnelState.Refused(DeviceExchangeEnvelope.REASON_BAD_CREDENTIALS), refused)
        Thread.sleep(RETRY_WINDOW_MS)
        assertEquals(1, connections.get())
        assertTrue(manager.state.value is SftpTunnelState.Refused)
    }

    private fun published(publicPort: Int) = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_PUBLISHED) {
        addProperty("resourceId", RESOURCE_ID)
        addProperty("publicPort", publicPort)
    }

    private fun read(socket: Socket): DeviceExchangeEnvelope =
        checkNotNull(
            DeviceExchangeFrameCodec.read(
                socket.getInputStream(),
                DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED
            )
        )

    private fun write(socket: Socket, envelope: DeviceExchangeEnvelope) =
        DeviceExchangeFrameCodec.write(socket.getOutputStream(), envelope)

    private fun poll(queue: ArrayBlockingQueue<DeviceExchangeEnvelope>): DeviceExchangeEnvelope =
        checkNotNull(queue.poll(WAIT_SECONDS, TimeUnit.SECONDS))

    private fun echoOnce(server: ServerSocket) {
        server.accept().use { socket ->
            val buffer = ByteArray(PAYLOAD.length)
            readFully(socket.getInputStream(), buffer)
            socket.getOutputStream().apply {
                write(buffer)
                flush()
            }
            socket.getInputStream().read()
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) offset += input.read(buffer, offset, buffer.size - offset)
    }

    private companion object {
        const val DEVICE_ID = "AAAAAAAAAAAAAAAAAAAAAA"
        const val SHARE_ID = "BBBBBBBBBBBBBBBBBBBBBB"
        const val RESOURCE_ID = "CCCCCCCCCCCCCCCCCCCCCC"
        const val TUNNEL_ID = "DDDDDDDDDDDDDDDDDDDDDD"
        const val TOKEN = "device-token"
        const val PAYLOAD = "SSH-2.0-test\r\n"
        const val WAIT_SECONDS = 10L
        const val RECONNECT_SECONDS = 5L
        const val MILLIS = 1_000L
        const val RETRY_WINDOW_MS = 3_000L
    }
}
