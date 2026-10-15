package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnection
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnector
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeTrust
import com.sza.fastmediasorter.data.security.e2e.E2eDeviceKey
import com.sza.fastmediasorter.data.security.e2e.E2eOpenResult
import com.sza.fastmediasorter.data.security.e2e.E2eRecipient
import com.sza.fastmediasorter.data.security.e2e.E2eSealedBox
import com.sza.fastmediasorter.data.security.e2e.E2eSegmentStream
import com.sza.fastmediasorter.data.security.e2e.E2eSelf
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
import java.io.ByteArrayOutputStream
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
 * The clipboard client of contract DEVICE-EXCHANGE 22 items BV, BW and BY, sealed by 27 item DN, against a fake
 * exchange server on loopback, written from the contract text because the real server is not available to the suite.
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
    private val accounts = DeviceExchangeAccountClient(connector, tokens, InMemoryE2eKeyStore())
    private val client = DeviceExchangeClipboardClient(accounts)

    private val self = E2eSelf(DEVICE_ID, E2eDeviceKey.generate())
    private val peer = E2eSelf(PEER_ID, E2eDeviceKey.generate())
    private val keys = ExchangeClipboardKeySource {
        ExchangeClipboardKeys(self, listOf(E2eRecipient(PEER_ID, peer.key.publicKey))) { from, key ->
            from == PEER_ID && key.contentEquals(peer.key.publicKey)
        }
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `a server without the clipboard is never asked`() = runBlocking {
        val seen = serve(welcome(features = listOf("directory", "sealed"))) { _, _, _ -> }

        val answer = client.state(entry, keys)

        assertEquals(ExchangeClipboardState.NotOffered, answer.result)
        assertNull(seen.poll(WAIT_SECONDS, TimeUnit.SECONDS))
    }

    @Test
    fun `a guest account is never asked`() = runBlocking {
        val seen = serve(welcome(kind = "guest")) { _, _, _ -> }

        val answer = client.state(entry, keys)

        assertEquals(ExchangeClipboardState.Guest, answer.result)
        assertEquals("guest", answer.welcome.accountKind)
        assertNull(seen.poll(WAIT_SECONDS, TimeUnit.SECONDS))
    }

    @Test
    fun `a server without sealed is sent nothing and asked nothing`() = runBlocking {
        val seen = serve(welcome(features = listOf("clipboard"))) { _, _, _ -> }

        val put = client.put(entry, fileMeta(), sha256(PAYLOAD), ByteArrayInputStream(PAYLOAD), keys) {}

        assertEquals(ExchangeClipboardPutResult.NotSealed, put.result)
        assertNull(seen.poll(WAIT_SECONDS, TimeUnit.SECONDS))
    }

    @Test
    fun `the state opens the sealed value of the account`() = runBlocking {
        serve(welcome()) { request, _, output ->
            assertEquals(DeviceExchangeEnvelope.TYPE_CLIPBOARD_INFO, request.type)
            write(
                output,
                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CHANGED),
                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CLIPBOARD_STATE) {
                    add("value", sealedValue(PAYLOAD).members)
                },
            )
        }

        val state = client.state(entry, keys).result as ExchangeClipboardState.Value

        assertEquals(ExchangeClipboardKind.FILE, state.value.kind)
        assertEquals(PAYLOAD.size.toLong(), state.value.size)
        assertEquals("report.pdf", state.value.name)
        assertEquals("Laptop", state.value.sourceDeviceName)
    }

    @Test
    fun `a clear value from the server is never used`() = runBlocking {
        serve(welcome()) { _, _, output ->
            write(
                output,
                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CLIPBOARD_STATE) {
                    add("value", clearMembers(PAYLOAD))
                },
            )
        }

        assertEquals(ExchangeClipboardState.Unsealed, client.state(entry, keys).result)
    }

    @Test
    fun `an empty clipboard has no value`() = runBlocking {
        serve(welcome()) { _, _, output ->
            write(output, DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CLIPBOARD_STATE))
        }

        assertEquals(ExchangeClipboardState.Empty, client.state(entry, keys).result)
    }

    @Test
    fun `a put seals the value, carries no clear name and succeeds on clipboard-stored`() = runBlocking {
        val received = ArrayBlockingQueue<Pair<DeviceExchangeEnvelope, ByteArray>>(1)
        serve(welcome()) { request, input, output ->
            write(output, DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CLIPBOARD_READY))
            val sealedSize = request.members.get("size").asInt
            received.put(request to ByteArray(sealedSize).also { DataInputStream(input).readFully(it) })
            write(
                output,
                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CLIPBOARD_STORED) {
                    addProperty("storedAt", STORED_AT)
                },
            )
        }
        val progress = mutableListOf<Long>()

        val source = ByteArrayInputStream(PAYLOAD + EXTRA)
        val result = client.put(entry, fileMeta(), sha256(PAYLOAD), source, keys) { progress += it }.result

        assertTrue(result is ExchangeClipboardPutResult.Stored)
        val (request, bytes) = checkNotNull(received.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals("sealed", request.string("kind"))
        assertNull(request.string("name"))
        assertNull(request.string("mimeType"))
        assertEquals(E2eSegmentStream.sealedSize(PAYLOAD.size.toLong()), bytes.size.toLong())
        val trustsSelf = { from: String, key: ByteArray -> from == DEVICE_ID && key.contentEquals(self.key.publicKey) }
        val box = request.members.get("sealed")
        val opened = E2eSealedBox.open(box, E2eSealedBox.CLIPBOARD_CONTEXT, peer, trustsSelf) as E2eOpenResult.Opened
        assertEquals("report.pdf", opened.payload.get("name").asString)
        assertEquals(sha256(PAYLOAD), opened.payload.get("sha256").asString)
        val clear = ByteArrayOutputStream()
        E2eSegmentStream.open(bytes.inputStream(), clear, E2eSegmentStream.valueKey(opened.contentKey))
        assertArrayEquals(PAYLOAD, clear.toByteArray())
        assertEquals(PAYLOAD.size.toLong(), progress.last())
    }

    @Test
    fun `a put with nobody to seal for sends nothing`() = runBlocking {
        val seen = serve(welcome()) { _, _, _ -> }
        val nobody = ExchangeClipboardKeySource { ExchangeClipboardKeys(self, emptyList()) { _, _ -> false } }

        val source = ByteArrayInputStream(PAYLOAD)
        val result = client.put(entry, fileMeta(), sha256(PAYLOAD), source, nobody) {}.result

        assertEquals(ExchangeClipboardPutResult.NoRecipient, result)
        assertNull(seen.poll(WAIT_SECONDS, TimeUnit.SECONDS))
    }

    @Test
    fun `a put over the limit is refused before any byte`() = runBlocking {
        val bytesAfterRefusal = ArrayBlockingQueue<Int>(1)
        serve(welcome()) { _, input, output ->
            write(output, DeviceExchangeEnvelope.refused(DeviceExchangeEnvelope.REASON_LIMIT_REACHED))
            bytesAfterRefusal.put(input.read())
        }

        val result = client.put(entry, fileMeta(), sha256(PAYLOAD), ByteArrayInputStream(PAYLOAD), keys) {}.result

        assertEquals(ExchangeClipboardPutResult.Refused("limit-reached"), result)
        assertEquals(-1, bytesAfterRefusal.poll(WAIT_SECONDS, TimeUnit.SECONDS))
    }

    @Test
    fun `a source shorter than its declared size breaks the send`() {
        serve(welcome()) { _, _, output -> write(output, DeviceExchangeEnvelope.of(TYPE_READY)) }

        assertThrows(IOException::class.java) {
            runBlocking {
                client.put(entry, fileMeta(), sha256(PAYLOAD), ByteArrayInputStream(PAYLOAD.copyOf(1)), keys) {}
            }
        }
    }

    @Test
    fun `a get opens a value whose sizes and hashes match`() = runBlocking {
        serve(welcome()) { request, _, output ->
            assertEquals(CLIP_ID, request.string("clipId"))
            sendValue(output, sealedValue(PAYLOAD))
        }
        val target = folder.newFile()

        val result = client.get(entry, CLIP_ID, target, keys) {}.result

        assertTrue(result is ExchangeClipboardGetResult.Received)
        assertArrayEquals(PAYLOAD, target.readBytes())
    }

    @Test
    fun `a get whose segment was changed places nothing`() = runBlocking {
        val value = sealedValue(PAYLOAD)
        value.bytes[0] = (value.bytes[0].toInt() xor 1).toByte()
        value.members.addProperty("sha256", sha256(value.bytes))
        serve(welcome()) { _, _, output -> sendValue(output, value) }
        val target = folder.newFile()

        val result = client.get(entry, null, target, keys) {}.result

        assertEquals(ExchangeClipboardGetResult.SealedFailed, result)
        assertFalse(target.exists())
    }

    @Test
    fun `a get answered clipboard-empty is empty`() = runBlocking {
        serve(welcome()) { _, _, output -> write(output, DeviceExchangeEnvelope.of(TYPE_EMPTY)) }

        assertEquals(ExchangeClipboardGetResult.Empty, client.get(entry, null, folder.newFile(), keys) {}.result)
    }

    private class SealedValue(val members: JsonObject, val bytes: ByteArray)

    // What a peer device put: the box over the clear members, the segments, the server's own members outside.
    private fun sealedValue(clear: ByteArray): SealedValue {
        val meta = fileMeta()
        val (box, contentKey) = E2eSealedBox.sealWithKey(
            DeviceExchangeClipboardClient.payloadOf(meta, sha256(clear)),
            E2eSealedBox.CLIPBOARD_CONTEXT,
            peer,
            listOf(E2eRecipient(DEVICE_ID, self.key.publicKey)),
        )
        val sealed = ByteArrayOutputStream().also {
            E2eSegmentStream.seal(clear.inputStream(), it, E2eSegmentStream.valueKey(contentKey))
        }.toByteArray()
        val members = JsonObject().apply {
            addProperty("clipId", CLIP_ID)
            addProperty("kind", "sealed")
            addProperty("size", sealed.size)
            addProperty("sha256", sha256(sealed))
            addProperty("storedAt", STORED_AT)
            addProperty("sourceDeviceName", "Laptop")
            add("sealed", box)
        }
        return SealedValue(members, sealed)
    }

    private fun clearMembers(bytes: ByteArray) = JsonObject().apply {
        addProperty("clipId", CLIP_ID)
        addProperty("kind", "file")
        addProperty("size", bytes.size)
        addProperty("sha256", sha256(bytes))
        addProperty("storedAt", STORED_AT)
        addProperty("mimeType", "application/pdf")
        addProperty("name", "report.pdf")
    }

    private fun sendValue(output: OutputStream, value: SealedValue) {
        write(output, DeviceExchangeEnvelope(DeviceExchangeEnvelope.TYPE_CLIPBOARD_VALUE, value.members))
        output.write(value.bytes)
        output.flush()
    }

    private fun fileMeta() =
        ExchangeClipboardMeta(ExchangeClipboardKind.FILE, PAYLOAD.size.toLong(), "application/pdf", "report.pdf")

    private fun welcome(features: List<String> = listOf("clipboard", "sealed"), kind: String? = null) =
        DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME) {
            add("features", JsonArray().apply { features.forEach(::add) })
            kind?.let { addProperty("accountKind", it) }
        }

    private fun directory() = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_DIRECTORY) {
        addProperty("revision", 1)
    }

    /**
     * Answers `hello` with [welcome] and the client's `list` with an empty directory, then hands the next envelope -
     * none when the client closes first - to [handle] with the raw streams, and offers it to the returned queue.
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
                var request = runCatching { DeviceExchangeFrameCodec.read(input, MAX_BODY) }.getOrNull()
                if (request?.type == DeviceExchangeEnvelope.TYPE_LIST) {
                    write(output, directory())
                    request = runCatching { DeviceExchangeFrameCodec.read(input, MAX_BODY) }.getOrNull()
                }
                if (request != null) {
                    seen.put(request)
                    handle(request, input, output)
                    input.read()
                }
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
        const val PEER_ID = "BBBBBBBBBBBBBBBBBBBBBB"
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
