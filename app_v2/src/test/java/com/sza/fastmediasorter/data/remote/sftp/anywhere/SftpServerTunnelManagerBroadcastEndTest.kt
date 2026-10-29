package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialRegistry
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastTunnelTls
import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeBroadcastGrants
import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeRelayKeys
import com.sza.fastmediasorter.data.broadcast.exchange.TunnelTestIdentity
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAccountClient
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeEnvelope
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeFrameCodec
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeTokenStore
import com.sza.fastmediasorter.data.remote.exchange.ExchangeBroadcastPublication
import com.sza.fastmediasorter.data.remote.exchange.InMemoryE2eKeyStore
import com.sza.fastmediasorter.data.remote.exchange.RecordingPinRecorder
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.SftpRendezvousBroadcast
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import kotlin.concurrent.thread

/**
 * Contract DEVICE-EXCHANGE item DX against a fake exchange server on loopback: a deliberate end writes
 * `broadcast-end` on the control stream before any tunnel of the broadcast closes, and never waits for a control
 * stream that is not there. The share-side behaviour lives in [SftpServerTunnelManagerTest].
 */
class SftpServerTunnelManagerBroadcastEndTest {

    private val exchange = ServerSocket(0)
    private val localServer = ServerSocket(0)

    private val entry = DeviceExchangeAccountEntry(
        host = "127.0.0.1",
        port = exchange.localPort,
        login = "owner",
        deviceId = DEVICE_ID,
        pinnedCertificate = null,
    )

    private val connector = object : ExchangeConnector {
        override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection =
            ExchangeConnection(Socket(host, port), "SHA256:fake")
    }

    private val tokens = object : DeviceExchangeTokenStore {
        override suspend fun token(entry: DeviceExchangeAccountEntry): String = TOKEN

        override suspend fun save(entry: DeviceExchangeAccountEntry, token: String): Boolean = true

        override suspend fun forget(entry: DeviceExchangeAccountEntry) = Unit
    }

    private val source = object : SftpExchangeProducerSource {
        override suspend fun producer(): SftpExchangeProducer = SftpExchangeProducer(entry, SHARE_ID)

        override suspend fun shareRecord(
            producer: SftpExchangeProducer,
            port: Int,
            hosts: List<String>,
        ): SftpRendezvousResource = error("no share in these tests")

        override suspend fun grantEnvelopes(
            port: Int,
            hosts: List<String>,
            sealing: ExchangeGrantSealing,
        ): List<DeviceExchangeEnvelope> = emptyList()

        override suspend fun broadcastGrantEnvelopes(sealing: ExchangeGrantSealing): List<DeviceExchangeEnvelope> =
            emptyList()

        override suspend fun sealedRecord(
            entry: DeviceExchangeAccountEntry,
            record: SftpRendezvousResource,
            serverSeals: Boolean,
            devices: List<SftpRendezvousDevice>?,
        ): SealedShareRecord = SealedShareRecord(record, "")

        override suspend fun sealedBroadcast(
            entry: DeviceExchangeAccountEntry,
            record: SftpRendezvousBroadcast,
            devices: List<SftpRendezvousDevice>,
        ): SealedBroadcastRecord = SealedBroadcastRecord(record, recipientsKey = "recipients")

        override fun tunnelTls(broadcastId: String): SSLContext = TunnelTestIdentity.context()
    }

    private val credentials = BroadcastCredentialRegistry(Dispatchers.Unconfined)
    private val broadcastGrants = ExchangeBroadcastGrants(credentials, ExchangeRelayKeys())

    @After
    fun tearDown() {
        exchange.close()
        localServer.close()
    }

    @Test
    fun `a deliberate end with no control stream returns at once`() = withManager { manager ->
        manager.announceBroadcast(publication())

        val started = System.nanoTime()
        assertFalse(manager.endBroadcast(LONG_TIMEOUT_MS))
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < LONG_TIMEOUT_MS)
    }

    @Test
    fun `a deliberate end writes broadcast-end before the broadcast's tunnel closes`() = withManager { manager ->
        assertEndPrecedesTunnelClose(manager) { assertTrue(manager.endBroadcast(LONG_TIMEOUT_MS)) }
    }

    @Test
    fun `withdraw writes broadcast-end before the broadcast's tunnel closes`() = withManager { manager ->
        assertEndPrecedesTunnelClose(manager) { assertTrue(manager.withdraw()) }
    }

    // When the broadcast's tunnel reaches its end of stream, `broadcast-end` must already sit in the control
    // stream: the phone wrote it before it closed the tunnel.
    private suspend fun assertEndPrecedesTunnelClose(manager: SftpServerTunnelManager, end: suspend () -> Unit) {
        val spliced = CountDownLatch(1)
        val endWaiting = ArrayBlockingQueue<Boolean>(1)
        val control = ArrayBlockingQueue<DeviceExchangeEnvelope>(1)
        thread(isDaemon = true) { echoOnce(localServer) }
        thread(isDaemon = true) {
            val stream = exchange.accept()
            read(stream)
            write(stream, DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME))
            read(stream)
            write(stream, httpOpen())
            val tunnel = exchange.accept()
            read(tunnel)
            write(tunnel, DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME))
            read(tunnel)
            val tls = BroadcastTunnelTls.connect(
                TunnelTestIdentity.pin,
                tunnel.getInputStream(),
                tunnel.getOutputStream(),
                tunnel,
            )
            tls.getOutputStream().apply {
                write(PAYLOAD.toByteArray())
                flush()
            }
            readFully(tls.getInputStream(), ByteArray(PAYLOAD.length))
            spliced.countDown()
            runCatching { while (tunnel.getInputStream().read() >= 0) Unit }
            endWaiting.put(stream.getInputStream().available() > 0)
            control.put(read(stream))
        }

        manager.announceBroadcast(publication())
        assertTrue(spliced.await(WAIT_SECONDS, TimeUnit.SECONDS))
        Thread.sleep(SETTLE_MS)
        end()

        assertEquals(true, endWaiting.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        val first = checkNotNull(control.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(DeviceExchangeEnvelope.TYPE_BROADCAST_END, first.type)
        assertEquals(BROADCAST_ID, first.string("broadcastId"))
    }

    private fun withManager(block: suspend (SftpServerTunnelManager) -> Unit) = runBlocking(Dispatchers.IO) {
        val manager = SftpServerTunnelManager(
            DeviceExchangeAccountClient(connector, tokens, InMemoryE2eKeyStore(), RecordingPinRecorder()),
            source,
            this,
            Dispatchers.IO,
            broadcastGrants,
        )
        try {
            block(manager)
        } finally {
            manager.stop()
            manager.endBroadcast()
        }
    }

    private fun httpOpen() = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_OPEN) {
        addProperty("tunnelId", TUNNEL_ID)
        addProperty("broadcastId", BROADCAST_ID)
        addProperty("scheme", "http")
    }

    private fun publication() = ExchangeBroadcastPublication(
        record = SftpRendezvousBroadcast(
            broadcastId = BROADCAST_ID,
            deviceId = DEVICE_ID,
            title = "Pixel",
            mode = "AUDIO_ONLY",
            startedAtMs = 0L,
            updatedAtMs = 0L,
            descriptorJson = "{\"url\":\"http://192.168.1.5:${localServer.localPort}/live-audio.aac\"}",
            writtenAtMs = 0L,
            ttlSeconds = 0L,
        ),
        maxListeners = ExchangeBroadcastPublication.NO_LISTENER_CAP,
        localPorts = mapOf(ExchangeBroadcastPublication.SCHEME_HTTP to localServer.localPort),
    )

    private fun read(socket: Socket): DeviceExchangeEnvelope =
        checkNotNull(
            DeviceExchangeFrameCodec.read(
                socket.getInputStream(),
                DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED,
            )
        )

    private fun write(socket: Socket, envelope: DeviceExchangeEnvelope) =
        DeviceExchangeFrameCodec.write(socket.getOutputStream(), envelope)

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
        const val TUNNEL_ID = "DDDDDDDDDDDDDDDDDDDDDD"
        const val BROADCAST_ID = "EEEEEEEEEEEEEEEEEEEEEA"
        const val TOKEN = "device-token"
        const val PAYLOAD = "SSH-2.0-test\r\n"
        const val WAIT_SECONDS = 10L
        const val SETTLE_MS = 300L
        const val LONG_TIMEOUT_MS = 5_000L
    }
}
