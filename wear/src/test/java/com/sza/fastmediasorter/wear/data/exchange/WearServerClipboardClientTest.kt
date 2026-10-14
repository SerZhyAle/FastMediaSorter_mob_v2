package com.sza.fastmediasorter.wear.data.exchange

import android.net.Network
import com.google.gson.JsonArray
import com.google.gson.JsonObject
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

/** S4227: the clipboard wire of contract DEVICE-EXCHANGE 22 against a fake server on loopback, plain frames. */
class WearServerClipboardClientTest {

    private val server = ServerSocket(0)
    private val connector = object : WearExchangeConnector {
        override fun connect(
            host: String,
            port: Int,
            pinned: String?,
            readTimeoutMs: Int,
            network: Network?,
        ): WearExchangeConnection = WearExchangeConnection(Socket(host, port))
    }
    private val entry = WearExchangeEntry("127.0.0.1", server.localPort, "owner", DEVICE_ID, null)
    private val store = TokenStore(entry.identity)
    private val client = WearServerClipboardClient(
        WearExchangeSessionOpener(connector, store, WearExchangeDeviceProfile("Watch", "2.6.0"))
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
    fun `a put stores exactly the declared bytes`() {
        val received = ArrayBlockingQueue<ByteArray>(1)
        serve { socket ->
            val put = read(socket)
            assertEquals("image", put?.string("kind"))
            write(socket, WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_CLIPBOARD_READY))
            val bytes = ByteArray(content.size).also { DataInputStream(socket.getInputStream()).readFully(it) }
            received.offer(bytes)
            write(socket, WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_CLIPBOARD_STORED))
        }

        val result = runBlocking { client.put(entry, offer, ByteArrayInputStream(content), null) {} }

        assertEquals(WearServerClipboardPut.Stored, result)
        assertArrayEquals(content, received.poll(WAIT_SECONDS, TimeUnit.SECONDS))
    }

    @Test
    fun `a refused put sends no bytes`() {
        val afterRefusal = ArrayBlockingQueue<Int>(1)
        serve { socket ->
            read(socket)
            write(socket, refused("limit-reached"))
            afterRefusal.offer(socket.getInputStream().read())
        }

        val result = runBlocking { client.put(entry, offer, ByteArrayInputStream(content), null) {} }

        assertEquals(WearServerClipboardPut.Failed(WearServerClipboardFailure.LIMIT_REACHED), result)
        assertEquals(-1, afterRefusal.poll(WAIT_SECONDS, TimeUnit.SECONDS))
    }

    @Test
    fun `a guest put is refused before the envelope`() {
        val asked = ArrayBlockingQueue<String>(1)
        serve(accountKind = "guest") { socket -> asked.offer(read(socket)?.type ?: "closed") }

        val result = runBlocking { client.put(entry, offer, ByteArrayInputStream(content), null) {} }

        assertEquals(WearServerClipboardPut.Failed(WearServerClipboardFailure.NOT_PERMITTED), result)
        assertEquals("closed", asked.poll(WAIT_SECONDS, TimeUnit.SECONDS))
    }

    @Test
    fun `a server without the clipboard feature is never asked`() {
        val asked = ArrayBlockingQueue<String>(1)
        serve(features = emptyList()) { socket -> asked.offer(read(socket)?.type ?: "closed") }

        val result = runBlocking { client.put(entry, offer, ByteArrayInputStream(content), null) {} }

        assertEquals(WearServerClipboardPut.Failed(WearServerClipboardFailure.NO_CLIPBOARD_FEATURE), result)
        assertEquals("closed", asked.poll(WAIT_SECONDS, TimeUnit.SECONDS))
    }

    @Test
    fun `a get whose bytes match is received whole`() {
        serve { socket ->
            read(socket)
            write(socket, valueEnvelope(WearExchangeEnvelope.TYPE_CLIPBOARD_VALUE, sha256Of(content)))
            socket.getOutputStream().write(content)
            socket.getOutputStream().flush()
        }
        val sink = ByteArrayOutputStream()

        val result = runBlocking { client.get(entry, null, null, { sink }) {} }

        assertEquals(WearServerClipboardKind.IMAGE, (result as WearServerClipboardGet.Received).value.kind)
        assertArrayEquals(content, sink.toByteArray())
    }

    @Test
    fun `a get with a wrong hash is never received`() {
        serve { socket ->
            read(socket)
            write(socket, valueEnvelope(WearExchangeEnvelope.TYPE_CLIPBOARD_VALUE, sha256Of("other".toByteArray())))
            socket.getOutputStream().write(content)
            socket.getOutputStream().flush()
        }

        val result = runBlocking { client.get(entry, null, null, { ByteArrayOutputStream() }) {} }

        assertEquals(WearServerClipboardGet.Failed(WearServerClipboardFailure.HASH_MISMATCH), result)
    }

    @Test
    fun `a get that ends early is a broken transfer`() {
        serve { socket ->
            read(socket)
            write(socket, valueEnvelope(WearExchangeEnvelope.TYPE_CLIPBOARD_VALUE, sha256Of(content)))
            socket.getOutputStream().write(content, 0, content.size / 2)
            socket.getOutputStream().flush()
            socket.shutdownOutput()
        }

        val result = runBlocking { client.get(entry, null, null, { ByteArrayOutputStream() }) {} }

        assertEquals(WearServerClipboardGet.Failed(WearServerClipboardFailure.TRANSFER_BROKEN), result)
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
    fun `info returns the state value and an empty state`() {
        serve { socket ->
            read(socket)
            write(socket, WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_KEEPALIVE))
            val state = WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_CLIPBOARD_STATE) {
                add("value", valueEnvelope(WearExchangeEnvelope.TYPE_CLIPBOARD_STATE, sha256Of(content)).members)
            }
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

    private fun serve(
        features: List<String> = listOf("clipboard"),
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

    private fun valueEnvelope(type: String, sha256: String) = WearExchangeEnvelope(
        type,
        JsonObject().apply {
            addProperty("clipId", "Y2xpcGJvYXJkY2xpcGlk")
            addProperty("kind", "image")
            addProperty("size", content.size)
            addProperty("sha256", sha256)
            addProperty("storedAt", "2026-10-14T20:00:00Z")
            addProperty("sourceDeviceId", "cGhvbmVwaG9uZXBob25l")
            addProperty("sourceDeviceName", "Phone")
            addProperty("mimeType", "image/png")
        }
    )

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
        const val TOKEN = "token-1"
        const val WAIT_SECONDS = 5L
    }
}
