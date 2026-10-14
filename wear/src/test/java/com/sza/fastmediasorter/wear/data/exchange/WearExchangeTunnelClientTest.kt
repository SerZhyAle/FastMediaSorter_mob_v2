package com.sza.fastmediasorter.wear.data.exchange

import android.net.Network
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** S4228: `connect {resourceId}` against a fake exchange server on loopback, plain frames. */
class WearExchangeTunnelClientTest {

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
    private val store = FakeStore(entry)
    private val client = WearExchangeTunnelClient(
        WearExchangeSessionOpener(connector, store, WearExchangeDeviceProfile("Watch", "2.6.0"))
    )

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `connected hands the stream over as raw bytes`() {
        val asked = serve(
            listOf(
                WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_CHANGED),
                WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_CONNECTED),
            ),
            rawAfter = RAW_BYTE,
        )

        val connection = runBlocking { client.open(entry, RESOURCE_ID) }
        val first = connection.input.read()
        connection.close()

        val connect = checkNotNull(asked.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(WearExchangeEnvelope.TYPE_CONNECT, connect.type)
        assertEquals(RESOURCE_ID, connect.string("resourceId"))
        assertEquals(RAW_BYTE, first)
    }

    @Test
    fun `refused closes the stream and names the reason`() {
        serve(
            listOf(
                WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_REFUSED) { addProperty("reason", "not-found") },
            ),
        )

        val refused = assertThrows(WearExchangeTunnelUnavailableException::class.java) {
            runBlocking { client.open(entry, RESOURCE_ID) }
        }

        assertEquals("not-found", refused.reason)
    }

    @Test
    fun `a closed stream before the answer is unavailable`() {
        serve(emptyList())

        val unavailable = assertThrows(WearExchangeTunnelUnavailableException::class.java) {
            runBlocking { client.open(entry, RESOURCE_ID) }
        }

        assertEquals(null, unavailable.reason)
    }

    /** Answers `hello` with `welcome`, then the `connect` with [answers], then optionally one raw byte. */
    private fun serve(
        answers: List<WearExchangeEnvelope>,
        rawAfter: Int? = null,
    ): ArrayBlockingQueue<WearExchangeEnvelope> {
        val asked = ArrayBlockingQueue<WearExchangeEnvelope>(1)
        thread(isDaemon = true) {
            server.accept().use { socket ->
                val input = socket.getInputStream()
                val output = socket.getOutputStream()
                WearExchangeFrameCodec.read(input)
                WearExchangeFrameCodec.write(output, WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_WELCOME))
                WearExchangeFrameCodec.read(input, WearExchangeFrameCodec.AUTHENTICATED_MAX_FRAME_BODY)
                    ?.let(asked::offer)
                answers.forEach { WearExchangeFrameCodec.write(output, it) }
                rawAfter?.let {
                    output.write(it)
                    output.flush()
                    input.read()
                }
            }
        }
        return asked
    }

    private class FakeStore(private val entry: WearExchangeEntry) : WearExchangeEntryStore {
        override val entries: Flow<List<WearExchangeEntry>> = MutableStateFlow(listOf(entry))
        override suspend fun all(): List<WearExchangeEntry> = listOf(entry)
        override suspend fun put(entry: WearExchangeEntry) = Unit
        override suspend fun remove(identity: String) = Unit
        override suspend fun setOutgoing(identity: String) = Unit
        override suspend fun token(identity: String): String? = TOKEN
        override suspend fun saveToken(identity: String, token: String): Boolean = true
        override suspend fun forgetToken(identity: String) = Unit
    }

    private companion object {
        const val DEVICE_ID = "d3ViZGV2aWNlaWQxMjM0"
        const val TOKEN = "token-1"
        const val RESOURCE_ID = "res-42"
        const val RAW_BYTE = 0x53
        const val WAIT_SECONDS = 5L
    }
}
