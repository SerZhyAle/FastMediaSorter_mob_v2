package com.sza.fastmediasorter.data.remote.exchange

import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnection
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnector
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeTrust
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import timber.log.Timber
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class DeviceExchangeAdminClientTest {

    private val server = ServerSocket(0)
    private val trusts = mutableListOf<ExchangeTrust>()
    private val connector = object : ExchangeConnector {
        override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection {
            trusts += trust
            return ExchangeConnection(Socket(host, port), "SHA256:fake")
        }
    }
    private val entry = DeviceExchangeAccountEntry(
        host = "127.0.0.1",
        port = server.localPort,
        login = "owner",
        deviceId = "d1",
        pinnedCertificate = "SHA256:pin",
    )
    private val client = DeviceExchangeAdminClient(connector)
    private val received = Collections.synchronizedList(mutableListOf<DeviceExchangeEnvelope>())
    private val logged = Collections.synchronizedList(mutableListOf<String>())
    private val tree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            logged += message
        }
    }

    @After
    fun tearDown() {
        if (tree in Timber.forest()) Timber.uproot(tree)
        server.close()
    }

    // Answers each received envelope with whatever [answer] returns; null ends the stream.
    private fun serve(answer: (DeviceExchangeEnvelope) -> DeviceExchangeEnvelope?): CountDownLatch {
        val finished = CountDownLatch(1)
        thread(isDaemon = true) {
            server.accept().use { socket ->
                var open = true
                while (open) {
                    val reply = runCatching { DeviceExchangeFrameCodec.read(socket.getInputStream(), MAX) }
                        .getOrNull()?.also { received += it }?.let(answer)
                    reply?.let { DeviceExchangeFrameCodec.write(socket.getOutputStream(), it, MAX) }
                    open = reply != null
                }
            }
            finished.countDown()
        }
        return finished
    }

    private fun authOk() = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_ADMIN_AUTH_OK) {
        addProperty("token", TOKEN)
        addProperty("expiresSeconds", 900)
    }

    private fun reply(request: DeviceExchangeEnvelope, build: com.google.gson.JsonObject.() -> Unit) =
        DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_ADMIN_REPLY) {
            add("id", request.members.get("id"))
            build()
        }

    @Test
    fun `sign-in sends admin-auth first on the pinned trust and keeps nothing in the log`() = runBlocking {
        Timber.plant(tree)
        serve { request ->
            when (request.type) {
                DeviceExchangeEnvelope.TYPE_ADMIN_AUTH -> authOk()
                else -> reply(request) {
                    addProperty("ok", true)
                    add("data", com.google.gson.JsonParser.parseString("""{"enabled":true,"kind":"guest"}"""))
                }
            }
        }
        val session = client.signIn(entry, PASSWORD.toCharArray())
        val data = session.call(DeviceExchangeAdminCodec.OP_SHARED_INVITATION_STATUS)
        session.close()
        assertEquals(DeviceExchangeEnvelope.TYPE_ADMIN_AUTH, received.first().type)
        assertEquals(PASSWORD, received.first().string("password"))
        assertEquals(TOKEN, received[1].string("token"))
        assertTrue(DeviceExchangeAdminCodec.readSharedInvitation(data).enabled)
        assertEquals(ExchangeTrust.Verified("SHA256:pin", null), trusts.single())
        assertFalse(logged.any { it.contains(PASSWORD) || it.contains(TOKEN) })
    }

    @Test
    fun `each sign-in refusal keeps its reason`() = runBlocking {
        listOf("admin-disabled", "bad-credentials", "rate-limited", "capacity", "tls-required").forEach { reason ->
            val done = serve { DeviceExchangeEnvelope.refused(reason) }
            try {
                client.signIn(entry, PASSWORD.toCharArray())
                fail("expected a refusal for $reason")
            } catch (e: DeviceExchangeRefusedException) {
                assertEquals(reason, e.reason)
            }
            done.await(5, TimeUnit.SECONDS)
        }
    }

    @Test(expected = DeviceExchangeWireException::class)
    fun `a server that closes without an answer is unanswered`(): Unit = runBlocking {
        serve { null }
        client.signIn(entry, PASSWORD.toCharArray())
    }

    @Test
    fun `a failed reply throws its code and a foreign id is skipped`() = runBlocking {
        serve { request ->
            if (request.type == DeviceExchangeEnvelope.TYPE_ADMIN_AUTH) {
                authOk()
            } else {
                reply(request) {
                    addProperty("ok", false)
                    addProperty("code", "exists")
                    addProperty("error", "account exists")
                }
            }
        }
        val session = client.signIn(entry, PASSWORD.toCharArray())
        try {
            session.call(DeviceExchangeAdminCodec.OP_ACCOUNT_ADD) { addProperty("login", "anna") }
            fail("expected a reply failure")
        } catch (e: DeviceExchangeAdminReplyException) {
            assertEquals("exists", e.code)
        } finally {
            session.close()
        }
    }

    @Test(expected = DeviceExchangeAdminSessionLapsedException::class)
    fun `a refusal after sign-in is a lapsed session`(): Unit = runBlocking {
        serve { request ->
            if (request.type == DeviceExchangeEnvelope.TYPE_ADMIN_AUTH) {
                authOk()
            } else {
                DeviceExchangeEnvelope.refused("bad-credentials")
            }
        }
        client.signIn(entry, PASSWORD.toCharArray()).use { it.call(DeviceExchangeAdminCodec.OP_STATUS) }
    }

    @Test
    fun `a cancelled call closes the stream`() = runBlocking {
        val done = serve { request ->
            if (request.type == DeviceExchangeEnvelope.TYPE_ADMIN_AUTH) {
                authOk()
            } else {
                null.also { Thread.sleep(SILENCE_MS) }
            }
        }
        val session = client.signIn(entry, PASSWORD.toCharArray())
        val call = async(Dispatchers.IO) { runCatching { session.call(DeviceExchangeAdminCodec.OP_STATUS) } }
        delay(CANCEL_AFTER_MS)
        call.cancel()
        assertNull(runCatching { call.await() }.getOrNull()?.getOrNull())
        assertTrue(done.await(SILENCE_MS * 2, TimeUnit.MILLISECONDS))
    }

    private companion object {
        const val PASSWORD = "operator-secret"
        const val TOKEN = "session-token-value"
        const val SILENCE_MS = 1_000L
        const val CANCEL_AFTER_MS = 200L
        const val MAX = DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED
    }
}
