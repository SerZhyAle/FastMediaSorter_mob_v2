package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnection
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnector
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeTrust
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeClipboardKind
import com.sza.fastmediasorter.domain.model.ExchangeClipboardMeta
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * The clipboard client of contract DEVICE-EXCHANGE 22 items BV, BW and BY against a fake exchange server on
 * loopback, written from the contract text because the real server (FMS_W 229) is not available to the suite.
 */
class DeviceExchangeClipboardClientTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val server = ServerSocket(0)
    private val tokens = object : DeviceExchangeTokenStore {
        override suspend fun token(entry: DeviceExchangeAccountEntry): String = TOKEN
        override suspend fun save(entry: DeviceExchangeAccountEntry, token: String): Boolean = true
        override suspend fun forget(entry: DeviceExchangeAccountEntry) = Unit
    }
    private val connector = object : ExchangeConnector {
        override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection =
            ExchangeConnection(Socket(host, port), "SHA256:fake")
    }
    private val entry = DeviceExchangeAccountEntry("127.0.0.1", server.localPort, "owner", DEVICE_ID, null)
    private val client = DeviceExchangeClipboardClient(DeviceExchangeAccountClient(connector, tokens))

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `a server without the clipboard is never asked`() = runBlocking {
        val seen = serve(welcome(features = listOf("directory"))) { _, _, _ -> }

        val answer = client.state(entry)

        assertEquals(ExchangeClipboardState.NotOffered, answer.result)
        assertNull(seen.poll(WAIT_SECONDS, TimeUnit.SECONDS))
    }

    @Test
    fun `a guest account is never asked`() = runBlocking {
        val seen = serve(welcome(kind = "guest")) { _, _, _ -> }

        val answer = client.state(entry)

        assertEquals(ExchangeClipboardState.Guest, answer.result)
        assertEquals("guest", answer.welcome.accountKind)
        assertNull(seen.poll(WAIT_SECONDS, TimeUnit.SECONDS))
    }

    @Test
    fun `the state carries the value of the account`() = runBlocking {
        serve(welcome()) { request, _, output ->
            assertEquals(DeviceExchangeEnvelope.TYPE_CLIPBOARD_INFO, request.type)
            write(
                output,
                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CHANGED),
                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CLIPBOARD_STATE) {
                    add("value", valueMembers(PAYLOAD))
                },
            )
        }

        val state = client.state(entry).result as ExchangeClipboardState.Value

        assertEquals(ExchangeClipboardKind.FILE, state.value.kind)
        assertEquals(PAYLOAD.size.toLong(), state.value.size)
        assertEquals("Laptop", state.value.sourceDeviceName)
    }

    @Test
    fun `an empty clipboard has no value`() = runBlocking {
        serve(welcome()) { _, _, output ->
            write(output, DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CLIPBOARD_STATE))
        }

        assertEquals(ExchangeClipboardState.Empty, client.state(entry).result)
    }

    @Test
    fun `a put writes exactly the declared bytes and succeeds on clipboard-stored`() = runBlocking {
        val received = ArrayBlockingQueue<ByteArray>(1)
        serve(welcome()) { request, input, output ->
            assertEquals("file", request.string("kind"))
            assertEquals("report.pdf", request.string("name"))
            write(output, DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CLIPBOARD_READY))
            received.put(ByteArray(PAYLOAD.size).also { DataInputStream(input).readFully(it) })
            write(
                output,
                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CLIPBOARD_STORED) {
                    addProperty("storedAt", STORED_AT)
                },
            )
        }
        val progress = mutableListOf<Long>()

        val result = client.put(entry, fileMeta(), ByteArrayInputStream(PAYLOAD + EXTRA)) { progress += it }.result

        assertTrue(result is ExchangeClipboardPutResult.Stored)
        assertArrayEquals(PAYLOAD, received.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(PAYLOAD.size.toLong(), progress.last())
    }

    @Test
    fun `a put over the limit is refused before any byte`() = runBlocking {
        val bytesAfterRefusal = ArrayBlockingQueue<Int>(1)
        serve(welcome()) { _, input, output ->
            write(output, DeviceExchangeEnvelope.refused(DeviceExchangeEnvelope.REASON_LIMIT_REACHED))
            bytesAfterRefusal.put(input.read())
        }

        val result = client.put(entry, fileMeta(), ByteArrayInputStream(PAYLOAD)) {}.result

        assertEquals(ExchangeClipboardPutResult.Refused("limit-reached"), result)
        assertEquals(-1, bytesAfterRefusal.poll(WAIT_SECONDS, TimeUnit.SECONDS))
    }

    @Test
    fun `a source shorter than its declared size breaks the send`() {
        serve(welcome()) { _, _, output -> write(output, DeviceExchangeEnvelope.of(TYPE_READY)) }

        assertThrows(IOException::class.java) {
            runBlocking { client.put(entry, fileMeta(), ByteArrayInputStream(PAYLOAD.copyOf(1))) {} }
        }
    }

    @Test
    fun `a get keeps a value whose size and hash match`() = runBlocking {
        serve(welcome()) { request, _, output ->
            assertEquals(CLIP_ID, request.string("clipId"))
            sendValue(output, PAYLOAD, sha256(PAYLOAD))
        }
        val target = folder.newFile()

        val result = client.get(entry, CLIP_ID, target) {}.result

        assertTrue(result is ExchangeClipboardGetResult.Received)
        assertArrayEquals(PAYLOAD, target.readBytes())
    }

    @Test
    fun `a get whose hash differs deletes what arrived`() = runBlocking {
        serve(welcome()) { _, _, output -> sendValue(output, PAYLOAD, sha256(EXTRA)) }
        val target = folder.newFile()

        val result = client.get(entry, null, target) {}.result

        assertEquals(ExchangeClipboardGetResult.Damaged, result)
        assertFalse(target.exists())
    }

    @Test
    fun `a get answered clipboard-empty is empty`() = runBlocking {
        serve(welcome()) { _, _, output -> write(output, DeviceExchangeEnvelope.of(TYPE_EMPTY)) }

        assertEquals(ExchangeClipboardGetResult.Empty, client.get(entry, null, folder.newFile()) {}.result)
    }

    private fun sendValue(output: OutputStream, bytes: ByteArray, sha: String) {
        write(
            output,
            DeviceExchangeEnvelope(DeviceExchangeEnvelope.TYPE_CLIPBOARD_VALUE, valueMembers(bytes, sha)),
        )
        output.write(bytes)
        output.flush()
    }

    private fun valueMembers(bytes: ByteArray, sha: String = sha256(bytes)) = JsonObject().apply {
        addProperty("clipId", CLIP_ID)
        addProperty("kind", "file")
        addProperty("size", bytes.size)
        addProperty("sha256", sha)
        addProperty("storedAt", STORED_AT)
        addProperty("sourceDeviceName", "Laptop")
        addProperty("mimeType", "application/pdf")
        addProperty("name", "report.pdf")
    }

    private fun fileMeta() =
        ExchangeClipboardMeta(ExchangeClipboardKind.FILE, PAYLOAD.size.toLong(), "application/pdf", "report.pdf")

    private fun welcome(features: List<String> = listOf("clipboard"), kind: String? = null) =
        DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME) {
            add("features", JsonArray().apply { features.forEach(::add) })
            kind?.let { addProperty("accountKind", it) }
        }

    /**
     * Answers `hello` with [welcome], then hands the next envelope - none when the client closes first - to
     * [handle] with the raw streams, and offers that envelope to the returned queue.
     */
    private fun serve(
        welcome: DeviceExchangeEnvelope,
        handle: (DeviceExchangeEnvelope, InputStream, OutputStream) -> Unit,
    ): ArrayBlockingQueue<DeviceExchangeEnvelope> {
        val seen = ArrayBlockingQueue<DeviceExchangeEnvelope>(1)
        thread(isDaemon = true) {
            server.accept().use { socket ->
                val input = socket.getInputStream()
                val output = socket.getOutputStream()
                checkNotNull(DeviceExchangeFrameCodec.read(input))
                DeviceExchangeFrameCodec.write(output, welcome)
                val request = runCatching { DeviceExchangeFrameCodec.read(input, MAX_BODY) }.getOrNull()
                    ?: return@use
                seen.put(request)
                handle(request, input, output)
                input.read()
            }
        }
        return seen
    }

    private fun write(output: OutputStream, vararg envelopes: DeviceExchangeEnvelope) =
        envelopes.forEach { DeviceExchangeFrameCodec.write(output, it, MAX_BODY) }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val DEVICE_ID = "AAAAAAAAAAAAAAAAAAAAAA"
        const val TOKEN = "tok3n"
        const val CLIP_ID = "q3Vb7YtK0xP2mN9sLfR4wA"
        const val STORED_AT = "2026-10-14T10:00:00Z"
        const val TYPE_READY = DeviceExchangeEnvelope.TYPE_CLIPBOARD_READY
        const val TYPE_EMPTY = DeviceExchangeEnvelope.TYPE_CLIPBOARD_EMPTY
        const val MAX_BODY = DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED
        const val WAIT_SECONDS = 2L
        val PAYLOAD = "%PDF-1.7 clipboard value".toByteArray()
        val EXTRA = "trailing bytes the client must not send".toByteArray()
    }
}
