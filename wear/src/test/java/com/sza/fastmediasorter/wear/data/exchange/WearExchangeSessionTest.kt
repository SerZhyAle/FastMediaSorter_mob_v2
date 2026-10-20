package com.sza.fastmediasorter.wear.data.exchange

import android.net.Network
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** The authenticated `hello` of the watch against a fake exchange server on loopback, plain frames. */
class WearExchangeSessionTest {

    private val server = ServerSocket(0)
    private val timeouts = mutableListOf<Int>()
    private val pinnedKeys = mutableListOf<String?>()
    private var presentedLeaf: String? = null
    private var presentedKey: String? = null
    private val connector = object : WearExchangeConnector {
        override fun connect(
            host: String,
            port: Int,
            pinned: String?,
            pinnedKey: String?,
            readTimeoutMs: Int,
            network: Network?,
        ): WearExchangeConnection {
            timeouts += readTimeoutMs
            pinnedKeys += pinnedKey
            return WearExchangeConnection(Socket(host, port), presentedLeaf, presentedKey)
        }
    }
    private val entry = WearExchangeEntry("127.0.0.1", server.localPort, "owner", DEVICE_ID, null)
    private val store = FakeStore(mutableMapOf(entry.identity to TOKEN), mutableListOf(entry))
    private val opener = WearExchangeSessionOpener(connector, store, WearExchangeDeviceProfile("Watch", "2.6.0"))

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `welcome is parsed into the session`() {
        val welcome = WearExchangeEnvelope.decode(
            """{"schemaVersion":2,"type":"welcome","publicEndpoint":"ex.example:7443",""" +
                """"keepaliveSeconds":30,"features":["relay","cast"]}"""
        )

        val parsed = WearExchangeWelcome.from(welcome)

        assertEquals("ex.example:7443", parsed.publicEndpoint)
        assertEquals(30, parsed.keepaliveSeconds)
        assertEquals(listOf("relay", "cast"), parsed.features)
    }

    @Test
    fun `hello carries the token and the proposed keepalive`() {
        val welcome = WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_WELCOME) { addProperty("keepaliveSeconds", 30) }
        val asked = serve(welcome)

        val session = runBlocking { opener.open(entry, KEEPALIVE, WearExchangeConnector.NO_READ_TIMEOUT) }
        session.close()

        val hello = checkNotNull(asked.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(WearExchangeEnvelope.TYPE_HELLO, hello.type)
        assertEquals(TOKEN, hello.string("deviceToken"))
        assertEquals(DEVICE_ID, hello.string("deviceId"))
        assertEquals(KEEPALIVE, hello.int("keepaliveSeconds"))
        assertEquals(30, session.welcome.keepaliveSeconds)
        assertEquals(listOf(WearExchangeConnector.NO_READ_TIMEOUT), timeouts)
    }

    @Test
    fun `a data stream proposes no keepalive`() {
        val asked = serve(WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_WELCOME))

        runBlocking { opener.open(entry, null, WearExchangeConnector.ENROLL_READ_TIMEOUT_MS) }.close()

        assertNull(checkNotNull(asked.poll(WAIT_SECONDS, TimeUnit.SECONDS)).int("keepaliveSeconds"))
    }

    @Test
    fun `a refusal carries its reason`() {
        serve(WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_REFUSED) { addProperty("reason", "device-revoked") })

        val refused = assertThrows(WearExchangeRefusedException::class.java) {
            runBlocking { opener.open(entry, KEEPALIVE, WearExchangeConnector.NO_READ_TIMEOUT) }
        }

        assertEquals("device-revoked", refused.reason)
    }

    @Test
    fun `an entry without a token never connects`() {
        store.tokens.clear()

        assertThrows(WearExchangeNotEnrolledException::class.java) {
            runBlocking { opener.open(entry, KEEPALIVE, WearExchangeConnector.NO_READ_TIMEOUT) }
        }
        assertEquals(emptyList<Int>(), timeouts)
    }

    @Test
    fun `a fresh welcome writes the clipboard facts back once`() {
        val welcome = WearExchangeEnvelope.decode(
            """{"schemaVersion":2,"type":"welcome","features":["clipboard"],"accountKind":"member"}"""
        )
        serve(welcome)
        runBlocking { opener.open(entry, null, WearExchangeConnector.ENROLL_READ_TIMEOUT_MS) }.close()
        serve(welcome)
        runBlocking { opener.open(entry, null, WearExchangeConnector.ENROLL_READ_TIMEOUT_MS) }.close()

        assertEquals(1, store.puts.size)
        assertEquals(listOf("clipboard"), store.puts.single().features)
        assertEquals("member", store.puts.single().accountKind)
        assertTrue(store.puts.single().clipboardEligible)
    }

    @Test
    fun `a guest welcome makes the entry ineligible`() {
        serve(
            WearExchangeEnvelope.decode(
                """{"schemaVersion":2,"type":"welcome","features":["clipboard"],"accountKind":"guest"}"""
            )
        )

        runBlocking { opener.open(entry, null, WearExchangeConnector.ENROLL_READ_TIMEOUT_MS) }.close()

        assertFalse(store.puts.single().clipboardEligible)
    }

    @Test
    fun `a same-key renewal replaces the stored leaf`() {
        val pinned = entry.copy(pinnedCertificate = LEAF, pinnedKey = KEY)
        val pinnedStore = FakeStore(mutableMapOf(pinned.identity to TOKEN), mutableListOf(pinned))
        val pinnedOpener = WearExchangeSessionOpener(connector, pinnedStore, WearExchangeDeviceProfile("Watch", "2.6"))
        presentedLeaf = RENEWED_LEAF
        presentedKey = KEY
        serve(WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_WELCOME))

        runBlocking { pinnedOpener.open(pinned, null, WearExchangeConnector.ENROLL_READ_TIMEOUT_MS) }.close()

        assertEquals(listOf<String?>(KEY), pinnedKeys)
        assertEquals(RENEWED_LEAF, pinnedStore.puts.single().pinnedCertificate)
        assertEquals(KEY, pinnedStore.puts.single().pinnedKey)
    }

    @Test
    fun `a pin handed over without a key learns it from the matching leaf`() {
        val pinned = entry.copy(pinnedCertificate = LEAF)
        val pinnedStore = FakeStore(mutableMapOf(pinned.identity to TOKEN), mutableListOf(pinned))
        val pinnedOpener = WearExchangeSessionOpener(connector, pinnedStore, WearExchangeDeviceProfile("Watch", "2.6"))
        presentedLeaf = LEAF
        presentedKey = KEY
        serve(WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_WELCOME))

        runBlocking { pinnedOpener.open(pinned, null, WearExchangeConnector.ENROLL_READ_TIMEOUT_MS) }.close()

        assertEquals(LEAF, pinnedStore.puts.single().pinnedCertificate)
        assertEquals(KEY, pinnedStore.puts.single().pinnedKey)
    }

    @Test
    fun `an unpinned entry never gains a pin from a connect`() {
        presentedLeaf = RENEWED_LEAF
        presentedKey = KEY
        serve(WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_WELCOME))

        runBlocking { opener.open(entry, null, WearExchangeConnector.ENROLL_READ_TIMEOUT_MS) }.close()

        assertTrue(store.puts.all { it.pinnedCertificate == null && it.pinnedKey == null })
    }

    private fun serve(answer: WearExchangeEnvelope): ArrayBlockingQueue<WearExchangeEnvelope> {
        val asked = ArrayBlockingQueue<WearExchangeEnvelope>(1)
        thread(isDaemon = true) {
            server.accept().use { socket ->
                WearExchangeFrameCodec.read(socket.getInputStream())?.let(asked::offer)
                WearExchangeFrameCodec.write(socket.getOutputStream(), answer)
                socket.getInputStream().read()
            }
        }
        return asked
    }

    private class FakeStore(
        val tokens: MutableMap<String, String>,
        val held: MutableList<WearExchangeEntry>,
    ) : WearExchangeEntryStore {
        val puts = mutableListOf<WearExchangeEntry>()
        override val entries: Flow<List<WearExchangeEntry>> = MutableStateFlow(emptyList())
        override suspend fun all(): List<WearExchangeEntry> = held.toList()
        override suspend fun put(entry: WearExchangeEntry) {
            puts += entry
            held.replaceAll { if (it.identity == entry.identity) entry else it }
        }
        override suspend fun remove(identity: String) = Unit
        override suspend fun setOutgoing(identity: String) = Unit
        override suspend fun token(identity: String): String? = tokens[identity]
        override suspend fun saveToken(identity: String, token: String): Boolean = true
        override suspend fun forgetToken(identity: String) {
            tokens.remove(identity)
        }
    }

    private companion object {
        const val DEVICE_ID = "d3ViZGV2aWNlaWQxMjM0"
        const val TOKEN = "token-1"
        const val KEEPALIVE = 30
        const val WAIT_SECONDS = 5L
        const val LEAF = "SHA256:leaf"
        const val RENEWED_LEAF = "SHA256:renewed"
        const val KEY = "SHA256:key"
    }
}
