package com.sza.fastmediasorter.wear.data.exchange

import android.net.Network
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.security.e2e.E2eClipboard
import com.sza.fastmediasorter.data.security.e2e.E2eDeviceKey
import com.sza.fastmediasorter.data.security.e2e.E2eOpenResult
import com.sza.fastmediasorter.data.security.e2e.E2eRecipient
import com.sza.fastmediasorter.data.security.e2e.E2eSealedBox
import com.sza.fastmediasorter.data.security.e2e.E2eSegmentStream
import com.sza.fastmediasorter.data.security.e2e.E2eSelf
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardFailure
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardKind
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardOffer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * S4227: the clipboard wire of contract DEVICE-EXCHANGE 22, sealed by 27 item DN, against a fake server on
 * loopback, plain frames.
 */
class WearServerClipboardClientTest {

    private val server = ServerSocket(0)
    private val connector = object : WearExchangeConnector {
        override fun connect(
            host: String,
            port: Int,
            pinned: String?,
            pinnedKey: String?,
            readTimeoutMs: Int,
            network: Network?,
        ): WearExchangeConnection = WearExchangeConnection(Socket(host, port))
    }
    private val entry = WearExchangeEntry("127.0.0.1", server.localPort, "owner", DEVICE_ID, null)
    private val store = TokenStore(entry.identity)
    private val watch = E2eSelf(DEVICE_ID, E2eDeviceKey.generate())
    private val phone = E2eSelf(PHONE_ID, E2eDeviceKey.generate())
    private val keys = FakeWearExchangeE2eStore().apply { keys[DEVICE_ID] = watch.key }
    private val client = WearServerClipboardClient(
        WearExchangeSessionOpener(connector, store, WearExchangeDeviceProfile("Watch", "2.6.0")),
        WearExchangeE2e(keys),
    )
    private val content = "a picture of a cat".toByteArray()
    private val offer = WearServerClipboardOffer(
        kind = WearServerClipboardKind.IMAGE,
        size = content.size.toLong(),
        mimeType = "image/png",
        name = null,
    )

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `a put seals the value for the account's phone`() {
        val received = ArrayBlockingQueue<Pair<WearExchangeEnvelope, ByteArray>>(1)
        serve { socket ->
            val put = checkNotNull(read(socket))
            write(socket, WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_CLIPBOARD_READY))
            val size = checkNotNull(put.long("size")).toInt()
            received.offer(put to ByteArray(size).also { DataInputStream(socket.getInputStream()).readFully(it) })
            write(socket, WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_CLIPBOARD_STORED))
        }

        val result = runBlocking { client.put(entry, offer, sha256Of(content), ByteArrayInputStream(content), null) {} }

        assertEquals(WearServerClipboardPut.Stored, result)
        val (put, bytes) = checkNotNull(received.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals("sealed", put.string("kind"))
        assertNull(put.string("mimeType"))
        val trustsWatch = { from: String, key: ByteArray ->
            from == DEVICE_ID && key.contentEquals(watch.key.publicKey)
        }
        val opened = E2eSealedBox.open(put.members.get("sealed"), E2eSealedBox.CLIPBOARD_CONTEXT, phone, trustsWatch)
            as E2eOpenResult.Opened
        assertEquals("image/png", opened.payload.get("mimeType").asString)
        val clear = ByteArrayOutputStream()
        E2eSegmentStream.open(bytes.inputStream(), clear, E2eSegmentStream.valueKey(opened.contentKey))
        assertArrayEquals(content, clear.toByteArray())
    }

    @Test
    fun `a refused put sends no bytes`() {
        val afterRefusal = ArrayBlockingQueue<Int>(1)
        serve { socket ->
            read(socket)
            write(socket, refused("limit-reached"))
            afterRefusal.offer(socket.getInputStream().read())
        }

        val result = runBlocking { client.put(entry, offer, sha256Of(content), ByteArrayInputStream(content), null) {} }

        assertEquals(WearServerClipboardPut.Failed(WearServerClipboardFailure.LIMIT_REACHED), result)
        assertEquals(-1, afterRefusal.poll(WAIT_SECONDS, TimeUnit.SECONDS))
    }

    @Test
    fun `a guest put is refused before the envelope`() {
        val asked = ArrayBlockingQueue<String>(1)
        serve(accountKind = "guest") { socket -> asked.offer(read(socket)?.type ?: "closed") }

        val result = runBlocking { client.put(entry, offer, sha256Of(content), ByteArrayInputStream(content), null) {} }

        assertEquals(WearServerClipboardPut.Failed(WearServerClipboardFailure.NOT_PERMITTED), result)
        assertEquals("closed", asked.poll(WAIT_SECONDS, TimeUnit.SECONDS))
    }

    @Test
    fun `a server without the clipboard feature is never asked`() {
        val asked = ArrayBlockingQueue<String>(1)
        serve(features = emptyList()) { socket -> asked.offer(read(socket)?.type ?: "closed") }

        val result = runBlocking { client.put(entry, offer, sha256Of(content), ByteArrayInputStream(content), null) {} }

        assertEquals(WearServerClipboardPut.Failed(WearServerClipboardFailure.NO_CLIPBOARD_FEATURE), result)
        assertEquals("closed", asked.poll(WAIT_SECONDS, TimeUnit.SECONDS))
    }

    @Test
    fun `a server without sealed is sent nothing`() {
        val asked = ArrayBlockingQueue<String>(1)
        serve(features = listOf("clipboard")) { socket -> asked.offer(read(socket)?.type ?: "closed") }

        val result = runBlocking { client.put(entry, offer, sha256Of(content), ByteArrayInputStream(content), null) {} }

        assertEquals(WearServerClipboardPut.Failed(WearServerClipboardFailure.NO_SEALED_FEATURE), result)
        assertEquals("closed", asked.poll(WAIT_SECONDS, TimeUnit.SECONDS))
    }

    @Test
    fun `a get opens a sealed value whole`() {
        val value = sealedValue(content)
        serve { socket ->
            read(socket)
            write(socket, WearExchangeEnvelope(WearExchangeEnvelope.TYPE_CLIPBOARD_VALUE, value.first))
            socket.getOutputStream().write(value.second)
            socket.getOutputStream().flush()
        }
        val sink = ByteArrayOutputStream()

        val result = runBlocking { client.get(entry, null, null, { sink }) {} }

        assertEquals(WearServerClipboardKind.IMAGE, (result as WearServerClipboardGet.Received).value.kind)
        assertArrayEquals(content, sink.toByteArray())
    }

    @Test
    fun `a get whose segment was changed is never received`() {
        val value = sealedValue(content)
        value.second[0] = (value.second[0].toInt() xor 1).toByte()
        value.first.addProperty("sha256", sha256Of(value.second))
        serve { socket ->
            read(socket)
            write(socket, WearExchangeEnvelope(WearExchangeEnvelope.TYPE_CLIPBOARD_VALUE, value.first))
            socket.getOutputStream().write(value.second)
            socket.getOutputStream().flush()
        }

        val result = runBlocking { client.get(entry, null, null, { ByteArrayOutputStream() }) {} }

        assertEquals(WearServerClipboardGet.Failed(WearServerClipboardFailure.SEALED_FAILED), result)
    }

    @Test
    fun `a clear value is never received`() {
        serve { socket ->
            read(socket)
            write(socket, WearExchangeEnvelope(WearExchangeEnvelope.TYPE_CLIPBOARD_VALUE, clearMembers()))
        }

        val result = runBlocking { client.get(entry, null, null, { ByteArrayOutputStream() }) {} }

        assertEquals(WearServerClipboardGet.Failed(WearServerClipboardFailure.E2E_MISSING), result)
    }

    @Test
    fun `an empty clipboard is answered empty`() {
        serve { socket ->
            read(socket)
            write(socket, WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_CLIPBOARD_EMPTY))
        }

        val result = runBlocking { client.get(entry, null, null, { ByteArrayOutputStream() }) {} }

        assertEquals(WearServerClipboardGet.Empty, result)
    }

    @Test
    fun `info opens the state value and reads an empty state`() {
        val value = sealedValue(content)
        serve { socket ->
            read(socket)
            write(socket, WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_KEEPALIVE))
            val state = WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_CLIPBOARD_STATE) { add("value", value.first) }
            write(socket, state)
            read(socket)
        }

        val answered = runBlocking { client.info(entry, null) } as WearServerClipboardInfo.Answered

        assertEquals("Phone", answered.value?.sourceDeviceName)
        assertEquals(content.size.toLong(), answered.value?.size)

        serve { socket ->
            read(socket)
            write(socket, WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_CLIPBOARD_STATE))
            read(socket)
        }

        assertNull((runBlocking { client.info(entry, null) } as WearServerClipboardInfo.Answered).value)
    }

    // What the phone put: the box over the clear members and the segments, the server's members outside.
    private fun sealedValue(clear: ByteArray): Pair<JsonObject, ByteArray> {
        val payload = E2eClipboard.payload("image", "image/png", null, clear.size.toLong(), sha256Of(clear))
        val to = listOf(E2eRecipient(DEVICE_ID, watch.key.publicKey))
        val (box, contentKey) = E2eSealedBox.sealWithKey(payload, E2eSealedBox.CLIPBOARD_CONTEXT, phone, to)
        val sealed = ByteArrayOutputStream().also {
            E2eSegmentStream.seal(clear.inputStream(), it, E2eSegmentStream.valueKey(contentKey))
        }.toByteArray()
        val members = JsonObject().apply {
            addProperty("clipId", "Y2xpcGJvYXJkY2xpcGlk")
            addProperty("kind", "sealed")
            addProperty("size", sealed.size)
            addProperty("sha256", sha256Of(sealed))
            addProperty("storedAt", "2026-10-14T20:00:00Z")
            addProperty("sourceDeviceId", PHONE_ID)
            addProperty("sourceDeviceName", "Phone")
            add("sealed", box)
        }
        return members to sealed
    }

    private fun clearMembers() = JsonObject().apply {
        addProperty("clipId", "Y2xpcGJvYXJkY2xpcGlk")
        addProperty("kind", "image")
        addProperty("size", content.size)
        addProperty("sha256", sha256Of(content))
        addProperty("storedAt", "2026-10-14T20:00:00Z")
        addProperty("mimeType", "image/png")
    }

    private fun directory() = WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_DIRECTORY) {
        addProperty("revision", 1)
        val device = JsonObject().apply {
            addProperty("deviceId", PHONE_ID)
            addProperty("deviceName", "Phone")
            addProperty("product", "fms-android")
            addProperty("presence", "online")
            add("capabilities", JsonArray().apply { add("e2e-v1") })
            add("e2eKey", phone.key.toWire())
        }
        add("devices", JsonArray().apply { add(device) })
    }

    // Answers `hello` with a welcome and the client's `list` with the account's phone, then runs [script].
    private fun serve(
        features: List<String> = listOf("clipboard", "sealed"),
        accountKind: String = "member",
        script: (Socket) -> Unit,
    ) {
        thread(isDaemon = true) {
            server.accept().use { socket ->
                read(socket)
                write(
                    socket,
                    WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_WELCOME) {
                        add("features", JsonArray().apply { features.forEach(::add) })
                        addProperty("accountKind", accountKind)
                    }
                )
                val sealedServer = "sealed" in features && "clipboard" in features && accountKind != "guest"
                if (sealedServer) {
                    read(socket)
                    write(socket, directory())
                }
                script(socket)
            }
        }
    }

    private fun read(socket: Socket): WearExchangeEnvelope? =
        WearExchangeFrameCodec.read(socket.getInputStream(), WearExchangeFrameCodec.AUTHENTICATED_MAX_FRAME_BODY)

    private fun write(socket: Socket, envelope: WearExchangeEnvelope) = WearExchangeFrameCodec.write(
        socket.getOutputStream(),
        envelope,
        WearExchangeFrameCodec.AUTHENTICATED_MAX_FRAME_BODY,
    )

    private fun refused(reason: String) =
        WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_REFUSED) { addProperty("reason", reason) }

    private fun sha256Of(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private class TokenStore(private val identity: String) : WearExchangeEntryStore {
        override val entries: Flow<List<WearExchangeEntry>> = MutableStateFlow(emptyList())
        override suspend fun all(): List<WearExchangeEntry> = emptyList()
        override suspend fun put(entry: WearExchangeEntry) = Unit
        override suspend fun remove(identity: String) = Unit
        override suspend fun setOutgoing(identity: String) = Unit
        override suspend fun token(identity: String): String? = TOKEN.takeIf { identity == this.identity }
        override suspend fun saveToken(identity: String, token: String): Boolean = true
        override suspend fun forgetToken(identity: String) = Unit
    }

    private companion object {
        const val DEVICE_ID = "d3ViZGV2aWNlaWQxMjM0"
        const val PHONE_ID = "cGhvbmVwaG9uZXBob25l"
        const val TOKEN = "token-1"
        const val WAIT_SECONDS = 5L
    }
}
