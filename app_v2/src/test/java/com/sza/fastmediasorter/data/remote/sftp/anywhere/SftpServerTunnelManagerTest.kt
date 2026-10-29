package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialRegistry
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastVerifier
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
import com.sza.fastmediasorter.domain.model.ExchangeBroadcastGrant
import com.sza.fastmediasorter.domain.model.ExchangeMessage
import com.sza.fastmediasorter.domain.model.SftpRendezvousBroadcast
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource
import com.sza.fastmediasorter.domain.model.SftpTunnelState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
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

        override suspend fun grantEnvelopes(
            port: Int,
            hosts: List<String>,
            sealing: ExchangeGrantSealing,
        ): List<DeviceExchangeEnvelope> = grants

        override suspend fun broadcastGrantEnvelopes(sealing: ExchangeGrantSealing): List<DeviceExchangeEnvelope> =
            broadcastGrantEnvelopes

        override suspend fun sealedRecord(
            entry: DeviceExchangeAccountEntry,
            record: SftpRendezvousResource,
            serverSeals: Boolean,
            devices: List<SftpRendezvousDevice>?,
        ): SealedShareRecord = sealer(record, serverSeals, devices)

        // S4440: a record whose title has a latch seals only once the latch opens, which holds one announce back.
        override suspend fun sealedBroadcast(
            entry: DeviceExchangeAccountEntry,
            record: SftpRendezvousBroadcast,
            devices: List<SftpRendezvousDevice>,
        ): SealedBroadcastRecord {
            record.title?.let(sealHolds::get)?.await()
            return SealedBroadcastRecord(record, recipientsKey = "recipients")
        }

        override fun tunnelTls(broadcastId: String): SSLContext = TunnelTestIdentity.context()
    }

    private val sealHolds = ConcurrentHashMap<String, CountDownLatch>()

    private var grants: List<DeviceExchangeEnvelope> = emptyList()

    private var broadcastGrantEnvelopes: List<DeviceExchangeEnvelope> = emptyList()

    private val credentials = BroadcastCredentialRegistry(Dispatchers.Unconfined)
    private val relayKeys = ExchangeRelayKeys()
    private val broadcastGrants = ExchangeBroadcastGrants(credentials, relayKeys)

    private var sealer: (SftpRendezvousResource, Boolean, List<SftpRendezvousDevice>?) -> SealedShareRecord =
        { record, _, _ -> SealedShareRecord(record, "") }

    @Test
    fun `a sealed server gets sealedAccess, published again when the devices change`() = withManager { manager ->
        sealer = { record, serverSeals, devices ->
            val ids = devices?.map { it.deviceId }.orEmpty()
            val box = """{"for":"${ids.joinToString()}"}""".takeIf { serverSeals && ids.isNotEmpty() }
            SealedShareRecord(record.copy(descriptor = null, sealedAccess = box), ids.joinToString())
        }
        val control = ArrayBlockingQueue<DeviceExchangeEnvelope>(SEALED_FRAMES)
        thread(isDaemon = true) {
            val stream = exchange.accept()
            control.put(read(stream))
            write(stream, sealedWelcome())
            repeat(SEALED_FIRST_READS) { control.put(read(stream)) }
            write(stream, directoryWith(OTHER_DEVICE_ID))
            control.put(read(stream))
            stream.getInputStream().read()
        }

        manager.start(localServer.localPort, listOf("192.168.1.5"))

        assertEquals(DeviceExchangeEnvelope.TYPE_HELLO, poll(control).type)
        val first = poll(control)
        assertEquals(DeviceExchangeEnvelope.TYPE_PUBLISH, first.type)
        assertFalse(first.members.getAsJsonObject("resource").has("access"))
        assertFalse(first.members.getAsJsonObject("resource").has("sealedAccess"))
        assertEquals(DeviceExchangeEnvelope.TYPE_LIST, poll(control).type)
        assertEquals(DeviceExchangeEnvelope.TYPE_SUBSCRIBE, poll(control).type)
        val second = poll(control).members.getAsJsonObject("resource")
        assertFalse(second.has("access"))
        assertEquals(OTHER_DEVICE_ID, second.getAsJsonObject("sealedAccess").get("for").asString)
    }

    private fun sealedWelcome() = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME) {
        add("features", JsonArray().apply { add("sealed") })
    }

    private fun directoryWith(deviceId: String) = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_DIRECTORY) {
        addProperty("revision", 1)
        val device = JsonObject().apply {
            addProperty("deviceId", deviceId)
            addProperty("deviceName", "Tablet")
            addProperty("product", "fms-android")
            addProperty("presence", "online")
        }
        add("devices", JsonArray().apply { add(device) })
    }

    @Test
    fun `grants follow the publish on a welcomed stream and granted is recorded`() = withManager { manager ->
        grants = listOf(
            DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_GRANT) {
                addProperty(DeviceExchangeEnvelope.KEY_GRANT_ID, GRANT_ID)
            }
        )
        val control = ArrayBlockingQueue<DeviceExchangeEnvelope>(3)
        thread(isDaemon = true) {
            val stream = exchange.accept()
            control.put(read(stream))
            write(stream, DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME))
            control.put(read(stream))
            control.put(read(stream))
            write(
                stream,
                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_GRANTED) {
                    addProperty(DeviceExchangeEnvelope.KEY_GRANT_ID, GRANT_ID)
                }
            )
            stream.getInputStream().read()
        }

        manager.start(localServer.localPort, listOf("192.168.1.5"))

        assertEquals(DeviceExchangeEnvelope.TYPE_HELLO, poll(control).type)
        assertEquals(DeviceExchangeEnvelope.TYPE_PUBLISH, poll(control).type)
        val grant = poll(control)
        assertEquals(DeviceExchangeEnvelope.TYPE_GRANT, grant.type)
        assertEquals(GRANT_ID, grant.string(DeviceExchangeEnvelope.KEY_GRANT_ID))
        val granted = withTimeout(TimeUnit.SECONDS.toMillis(WAIT_SECONDS)) {
            manager.grantedIds.first { GRANT_ID in it }
        }
        assertTrue(GRANT_ID in granted)
    }

    // The manager runs in the test's own runBlocking scope, so every coroutine it starts is joined
    // before the test returns.
    private fun withManager(block: suspend (SftpServerTunnelManager) -> Unit) = runBlocking(Dispatchers.IO) {
        val manager =
            SftpServerTunnelManager(
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
    fun `withdraw unpublishes on the live stream and reports that the server was told`() = withManager { manager ->
        val farewell = ArrayBlockingQueue<DeviceExchangeEnvelope>(2)
        thread(isDaemon = true) {
            exchange.accept().use { stream ->
                read(stream)
                write(stream, DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME))
                read(stream)
                write(stream, published(0))
                farewell.put(read(stream))
                farewell.put(read(stream))
            }
        }

        manager.start(localServer.localPort, emptyList())
        withTimeout(WAIT_SECONDS * MILLIS) { manager.state.first { it is SftpTunnelState.Registered } }

        assertTrue(manager.withdraw())
        assertEquals(DeviceExchangeEnvelope.TYPE_UNPUBLISH, poll(farewell).type)
        assertEquals(DeviceExchangeEnvelope.TYPE_BYE, poll(farewell).type)
        assertEquals(SftpTunnelState.Off, manager.state.value)
    }

    @Test
    fun `withdraw with nothing published ends locally at once`() = withManager { manager ->
        assertFalse(manager.withdraw())
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

    @Test
    fun `a broadcast alone announces itself and splices an http tunnel to its server`() = withManager { manager ->
        val control = ArrayBlockingQueue<DeviceExchangeEnvelope>(2)
        val data = ArrayBlockingQueue<DeviceExchangeEnvelope>(2)
        val echoed = ArrayBlockingQueue<String>(1)
        thread(isDaemon = true) { echoOnce(localServer) }
        thread(isDaemon = true) {
            val stream = exchange.accept()
            control.put(read(stream))
            write(stream, DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME))
            control.put(read(stream))
            write(
                stream,
                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_OPEN) {
                    addProperty("tunnelId", TUNNEL_ID)
                    addProperty("broadcastId", BROADCAST_ID)
                    addProperty("scheme", "http")
                }
            )
            val tunnel = exchange.accept()
            data.put(read(tunnel))
            write(tunnel, DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME))
            data.put(read(tunnel))
            // 27 item DM: the consumer's bytes cross the server inside the broadcast's own pinned TLS.
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
            val reply = ByteArray(PAYLOAD.length)
            readFully(tls.getInputStream(), reply)
            echoed.put(String(reply))
        }

        manager.announceBroadcast(publication(localServer.localPort))

        assertEquals(DeviceExchangeEnvelope.TYPE_HELLO, poll(control).type)
        val start = poll(control)
        assertEquals(DeviceExchangeEnvelope.TYPE_BROADCAST_START, start.type)
        assertEquals(BROADCAST_ID, start.obj("broadcast")?.get("broadcastId")?.asString)
        assertEquals(0, start.int("maxListeners"))
        val verifier = start.members.getAsJsonArray("verifiers").single().asJsonObject
        assertEquals("listener", verifier.get("user").asString)
        assertEquals("aGFzaA", verifier.get("hash").asString)
        assertEquals(DeviceExchangeEnvelope.TYPE_HELLO, poll(data).type)
        assertEquals(TUNNEL_ID, poll(data).string("tunnelId"))
        assertEquals(PAYLOAD, echoed.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(SftpTunnelState.Off, manager.state.value)
    }

    @Test
    fun `a published share carries the broadcast on its own control stream`() = withManager { manager ->
        val control = ArrayBlockingQueue<DeviceExchangeEnvelope>(4)
        thread(isDaemon = true) {
            exchange.accept().use { stream ->
                read(stream)
                write(stream, DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME))
                control.put(read(stream))
                write(stream, published(0))
                control.put(read(stream))
                control.put(read(stream))
                stream.getInputStream().read()
            }
        }

        manager.start(localServer.localPort, emptyList())
        withTimeout(WAIT_SECONDS * MILLIS) { manager.state.first { it is SftpTunnelState.Registered } }
        manager.announceBroadcast(publication(localServer.localPort))

        assertEquals(DeviceExchangeEnvelope.TYPE_PUBLISH, poll(control).type)
        assertEquals(DeviceExchangeEnvelope.TYPE_BROADCAST_START, poll(control).type)
        // S4440: a second announce made while the first is still unwritten would supersede it, so it waits.
        manager.announceBroadcast(publication(localServer.localPort))
        assertEquals(DeviceExchangeEnvelope.TYPE_BROADCAST_UPDATE, poll(control).type)
        assertEquals(1, connections.get())
    }

    @Test
    fun `an older announce that seals slowly is never written after a newer one`() = withManager { manager ->
        sealHolds["older"] = CountDownLatch(1)
        val control = ArrayBlockingQueue<DeviceExchangeEnvelope>(SEALED_FRAMES + 2)
        thread(isDaemon = true) {
            exchange.accept().use { stream ->
                repeat(SEALED_ANNOUNCE_HANDSHAKE_READS) { index ->
                    control.put(read(stream))
                    if (index == 0) write(stream, sealedWelcome())
                }
                write(stream, directoryWith(OTHER_DEVICE_ID))
                repeat(SEALED_ANNOUNCE_TAIL_READS) { control.put(read(stream)) }
                stream.getInputStream().read()
            }
        }

        manager.announceBroadcast(publication(localServer.localPort, title = "first"))
        // hello, list, subscribe, start; then the directory arrives and the box goes out as one update.
        repeat(SEALED_ANNOUNCE_HANDSHAKE_READS) { poll(control) }
        assertEquals("first", titleOf(poll(control)))

        manager.announceBroadcast(publication(localServer.localPort, title = "older"))
        manager.announceBroadcast(publication(localServer.localPort, title = "newer"))
        val written = poll(control)
        assertEquals(DeviceExchangeEnvelope.TYPE_BROADCAST_UPDATE, written.type)
        assertEquals("newer", titleOf(written))

        checkNotNull(sealHolds["older"]).countDown()
        assertEquals(null, control.poll(STALE_QUIET_SECONDS, TimeUnit.SECONDS))
    }

    private fun titleOf(envelope: DeviceExchangeEnvelope): String =
        envelope.members.getAsJsonObject("broadcast").get("title").asString

    @Test
    fun `turning the folder off keeps the announced broadcast and its control stream`() = withManager { manager ->
        val control = ArrayBlockingQueue<DeviceExchangeEnvelope>(4)
        thread(isDaemon = true) {
            exchange.accept().use { stream ->
                read(stream)
                write(stream, DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME))
                control.put(read(stream))
                write(stream, published(0))
                repeat(3) { control.put(read(stream)) }
                stream.getInputStream().read()
            }
        }

        manager.start(localServer.localPort, emptyList())
        withTimeout(WAIT_SECONDS * MILLIS) { manager.state.first { it is SftpTunnelState.Registered } }
        manager.announceBroadcast(publication(localServer.localPort))
        assertEquals(DeviceExchangeEnvelope.TYPE_PUBLISH, poll(control).type)
        assertEquals(DeviceExchangeEnvelope.TYPE_BROADCAST_START, poll(control).type)

        assertTrue(manager.unpublishShare())
        val unpublish = poll(control)
        assertEquals(DeviceExchangeEnvelope.TYPE_UNPUBLISH, unpublish.type)
        assertEquals(RESOURCE_ID, unpublish.string("resourceId"))
        assertEquals(SftpTunnelState.Off, manager.state.value)
        manager.announceBroadcast(publication(localServer.localPort))
        assertEquals(DeviceExchangeEnvelope.TYPE_BROADCAST_UPDATE, poll(control).type)
        assertEquals(1, connections.get())
    }

    @Test
    fun `a broadcast for another entry leaves the old stream and announces on a new one`() = withManager { manager ->
        val first = ArrayBlockingQueue<DeviceExchangeEnvelope>(3)
        val second = ArrayBlockingQueue<DeviceExchangeEnvelope>(1)
        thread(isDaemon = true) {
            exchange.accept().use { stream ->
                read(stream)
                write(stream, DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME))
                repeat(3) { first.put(read(stream)) }
            }
            exchange.accept().use { stream ->
                read(stream)
                write(stream, DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME))
                second.put(read(stream))
                stream.getInputStream().read()
            }
        }

        manager.announceBroadcast(publication(localServer.localPort))
        assertEquals(DeviceExchangeEnvelope.TYPE_BROADCAST_START, poll(first).type)
        // The announce runs on the manager's own coroutine; give it the moment to record what it sent.
        Thread.sleep(SETTLE_MS)
        val moved = publication(localServer.localPort)
        manager.announceBroadcast(moved.copy(record = moved.record.copy(deviceId = OTHER_DEVICE_ID)))

        assertEquals(DeviceExchangeEnvelope.TYPE_BROADCAST_END, poll(first).type)
        assertEquals(DeviceExchangeEnvelope.TYPE_BYE, poll(first).type)
        assertEquals(DeviceExchangeEnvelope.TYPE_BROADCAST_START, poll(second).type)
        assertEquals(2, connections.get())
    }

    @Test
    fun `broadcast-started for the announced id puts it on the server, another id does not`() = withManager { manager ->
        val started = ArrayBlockingQueue<Boolean>(1)
        val release = ArrayBlockingQueue<Boolean>(1)
        thread(isDaemon = true) {
            exchange.accept().use { stream ->
                read(stream)
                write(stream, DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME))
                read(stream)
                write(stream, broadcastStarted(OTHER_BROADCAST_ID))
                started.put(true)
                release.take()
                write(stream, broadcastStarted(BROADCAST_ID))
                stream.getInputStream().read()
            }
        }

        manager.announceBroadcast(publication(localServer.localPort))
        checkNotNull(started.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        Thread.sleep(SETTLE_MS)
        assertEquals(SftpServerTunnelManager.BroadcastWire.Sent(BROADCAST_ID), manager.broadcastWire.value)
        release.put(true)

        val wire = withTimeout(WAIT_SECONDS * MILLIS) {
            manager.broadcastWire.first { it is SftpServerTunnelManager.BroadcastWire.Started }
        }
        assertEquals(SftpServerTunnelManager.BroadcastWire.Started(BROADCAST_ID), wire)
        manager.endBroadcast()
        assertEquals(SftpServerTunnelManager.BroadcastWire.None, manager.broadcastWire.value)
    }

    @Test
    fun `a refusal after broadcast-start fails the broadcast with the row of its reason`() = withManager { manager ->
        thread(isDaemon = true) {
            exchange.accept().use { stream ->
                read(stream)
                write(stream, DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME))
                read(stream)
                write(stream, DeviceExchangeEnvelope.refused(DeviceExchangeEnvelope.REASON_UNAVAILABLE))
                stream.getInputStream().read()
            }
        }

        manager.announceBroadcast(publication(localServer.localPort))

        val wire = withTimeout(WAIT_SECONDS * MILLIS) {
            manager.broadcastWire.first { it is SftpServerTunnelManager.BroadcastWire.Failed }
        }
        assertEquals(SftpServerTunnelManager.BroadcastWire.Failed(ExchangeMessage.UNAVAILABLE), wire)
    }

    @Test
    fun `ending a broadcast with no share says broadcast-end and bye`() = withManager { manager ->
        val farewell = ArrayBlockingQueue<DeviceExchangeEnvelope>(2)
        val announced = ArrayBlockingQueue<Boolean>(1)
        thread(isDaemon = true) {
            exchange.accept().use { stream ->
                read(stream)
                write(stream, DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME))
                read(stream)
                announced.put(true)
                farewell.put(read(stream))
                farewell.put(read(stream))
            }
        }

        manager.announceBroadcast(publication(localServer.localPort))
        checkNotNull(announced.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        // The announce runs on the manager's own coroutine; give it the moment to record what it sent.
        Thread.sleep(SETTLE_MS)
        manager.endBroadcast()

        val end = poll(farewell)
        assertEquals(DeviceExchangeEnvelope.TYPE_BROADCAST_END, end.type)
        assertEquals(BROADCAST_ID, end.string("broadcastId"))
        assertEquals(DeviceExchangeEnvelope.TYPE_BYE, poll(farewell).type)
    }

    @Test
    fun `a refused broadcast grant keeps the control stream and marks the grant refused`() = withManager { manager ->
        credentials.begin(BROADCAST_ID)
        relayKeys.begin(BROADCAST_ID)
        val grant = checkNotNull(broadcastGrants.add(BROADCAST_ID, "bob"))
        broadcastGrantEnvelopes = listOf(
            DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_GRANT) {
                addProperty(DeviceExchangeEnvelope.KEY_GRANT_ID, grant.grantId)
                addProperty("broadcastId", BROADCAST_ID)
            }
        )
        val control = ArrayBlockingQueue<DeviceExchangeEnvelope>(4)
        thread(isDaemon = true) {
            val stream = exchange.accept()
            control.put(read(stream))
            write(stream, DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME))
            control.put(read(stream))
            control.put(read(stream))
            write(stream, DeviceExchangeEnvelope.refused(DeviceExchangeEnvelope.REASON_UNAVAILABLE))
            control.put(read(stream))
            stream.getInputStream().read()
        }

        manager.announceBroadcast(publication(localServer.localPort))

        assertEquals(DeviceExchangeEnvelope.TYPE_HELLO, poll(control).type)
        assertEquals(DeviceExchangeEnvelope.TYPE_BROADCAST_START, poll(control).type)
        val sent = poll(control)
        assertEquals(DeviceExchangeEnvelope.TYPE_GRANT, sent.type)
        assertEquals(BROADCAST_ID, sent.string("broadcastId"))
        withTimeout(WAIT_SECONDS * MILLIS) {
            broadcastGrants.grants.first { all -> all.single().state == ExchangeBroadcastGrant.State.REFUSED }
        }
        assertTrue(manager.sendUngrant(grant.grantId))
        val ungrant = poll(control)
        assertEquals(DeviceExchangeEnvelope.TYPE_UNGRANT, ungrant.type)
        assertEquals(grant.grantId, ungrant.string(DeviceExchangeEnvelope.KEY_GRANT_ID))
        assertEquals(1, connections.get())
        assertEquals(SftpTunnelState.Off, manager.state.value)
    }

    @Test
    fun `a refused with no grant pending still ends the session`() = withManager { manager ->
        val control = ArrayBlockingQueue<DeviceExchangeEnvelope>(2)
        thread(isDaemon = true) {
            exchange.accept().use { stream ->
                control.put(read(stream))
                write(stream, DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME))
                control.put(read(stream))
                write(stream, DeviceExchangeEnvelope.refused(DeviceExchangeEnvelope.REASON_UNAVAILABLE))
                stream.getInputStream().read()
            }
        }

        manager.announceBroadcast(publication(localServer.localPort))

        assertEquals(DeviceExchangeEnvelope.TYPE_HELLO, poll(control).type)
        assertEquals(DeviceExchangeEnvelope.TYPE_BROADCAST_START, poll(control).type)
        val refused = withTimeout(WAIT_SECONDS * MILLIS) { manager.state.first { it is SftpTunnelState.Refused } }
        assertEquals(SftpTunnelState.Refused(DeviceExchangeEnvelope.REASON_UNAVAILABLE), refused)
    }

    private fun publication(httpPort: Int, title: String = "Pixel") = ExchangeBroadcastPublication(
        record = SftpRendezvousBroadcast(
            broadcastId = BROADCAST_ID,
            deviceId = DEVICE_ID,
            title = title,
            mode = "AUDIO_ONLY",
            startedAtMs = 0L,
            updatedAtMs = 0L,
            descriptorJson = "{\"url\":\"http://192.168.1.5:$httpPort/live-audio.aac\"}",
            writtenAtMs = 0L,
            ttlSeconds = 0L,
        ),
        maxListeners = ExchangeBroadcastPublication.NO_LISTENER_CAP,
        localPorts = mapOf(ExchangeBroadcastPublication.SCHEME_HTTP to httpPort),
        verifiers = listOf(BroadcastVerifier(user = "listener", salt = "c2FsdA", hash = "aGFzaA")),
    )

    private fun broadcastStarted(broadcastId: String) =
        DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_BROADCAST_STARTED) {
            addProperty("broadcastId", broadcastId)
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
        const val BROADCAST_ID = "EEEEEEEEEEEEEEEEEEEEEA"
        const val OTHER_BROADCAST_ID = "FFFFFFFFFFFFFFFFFFFFFA"
        const val GRANT_ID = "FFFFFFFFFFFFFFFFFFFFFA"
        const val OTHER_DEVICE_ID = "GGGGGGGGGGGGGGGGGGGGGA"
        const val SEALED_FRAMES = 5
        const val SEALED_FIRST_READS = 3
        const val SEALED_ANNOUNCE_HANDSHAKE_READS = 4
        const val SEALED_ANNOUNCE_TAIL_READS = 3
        const val STALE_QUIET_SECONDS = 2L
        const val SETTLE_MS = 300L
        const val TOKEN = "device-token"
        const val PAYLOAD = "SSH-2.0-test\r\n"
        const val WAIT_SECONDS = 10L
        const val RECONNECT_SECONDS = 5L
        const val MILLIS = 1_000L
        const val RETRY_WINDOW_MS = 3_000L
    }
}
