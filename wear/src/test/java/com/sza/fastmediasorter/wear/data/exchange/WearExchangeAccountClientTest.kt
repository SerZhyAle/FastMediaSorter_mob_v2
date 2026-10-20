package com.sza.fastmediasorter.wear.data.exchange

import android.net.Network
import com.sza.fastmediasorter.data.security.e2e.E2eDeviceKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * S4158: the watch's `enroll` and self-revoke against a fake exchange server on loopback, speaking plain frames
 * (TLS and the trust decision are the connector's, tested apart).
 */
class WearExchangeAccountClientTest {

    private val server = ServerSocket(0)
    private val pins = mutableListOf<String?>()
    private val connector = object : WearExchangeConnector {
        override fun connect(
            host: String,
            port: Int,
            pinned: String?,
            pinnedKey: String?,
            readTimeoutMs: Int,
            network: Network?,
        ): WearExchangeConnection {
            pins += pinned
            return WearExchangeConnection(Socket(host, port))
        }
    }
    private val client = WearExchangeAccountClient(connector)
    private val entry = WearExchangeEntry("127.0.0.1", server.localPort, "Owner.Home", DEVICE_ID, PIN)
    private val profile = WearExchangeDeviceProfile("Galaxy Watch", "2.6.0")

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `enroll sends the code as the watch product and hands the token back`() {
        val asked = serve(
            WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_ENROLLED) { addProperty("deviceToken", TOKEN) },
        )

        val result = client.enroll(entry, CODE, profile)

        val enroll = checkNotNull(asked.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(WearExchangeEnvelope.TYPE_ENROLL, enroll.type)
        assertEquals(CODE, enroll.string("pairingCode"))
        assertNull(enroll.string("password"))
        assertEquals("Owner.Home", enroll.string("login"))
        assertEquals(DEVICE_ID, enroll.string("deviceId"))
        assertEquals("fms-wear", enroll.string("product"))
        assertEquals(
            "[\"broadcaster\",\"receiver\",\"clipboard\",\"resource-consumer\"]",
            enroll.members.get("roles").toString()
        )
        assertEquals("[\"AUDIO_ONLY\"]", enroll.members.getAsJsonObject("receiver").get("modes").toString())
        assertEquals(TOKEN, (result as WearExchangeEnrollResult.Enrolled).token)
        assertEquals(listOf<String?>(PIN), pins)
    }

    @Test
    fun `enroll with a key declares e2eKey and the capability e2e-v1`() {
        val asked = serve(
            WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_ENROLLED) { addProperty("deviceToken", TOKEN) },
        )
        val key = E2eDeviceKey.generate()

        client.enroll(entry, CODE, profile, key)

        val enroll = checkNotNull(asked.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals("[\"e2e-v1\"]", enroll.members.get("capabilities").toString())
        assertEquals(key.kem, enroll.members.getAsJsonObject("e2eKey").get("kem").asString)
        assertEquals(1, enroll.members.getAsJsonObject("e2eKey").get("v").asInt)
    }

    @Test
    fun `a refusal carries the server reason`() {
        serve(WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_REFUSED) { addProperty("reason", "bad-credentials") })

        assertEquals(WearExchangeEnrollResult.Refused("bad-credentials"), client.enroll(entry, CODE, profile))
    }

    @Test
    fun `a refused certificate is told apart from an unreachable server`() {
        val refusing = object : WearExchangeConnector {
            override fun connect(
                host: String,
                port: Int,
                pinned: String?,
                pinnedKey: String?,
                readTimeoutMs: Int,
                network: Network?,
            ): WearExchangeConnection =
                throw WearExchangeCertificateChangedException()
        }

        val result = WearExchangeAccountClient(refusing).enroll(entry, CODE, profile)

        assertEquals(WearExchangeEnrollResult.CertificateChanged, result)
    }

    @Test
    fun `revoke says hello with the token and revokes its own device`() {
        val asked = ArrayBlockingQueue<WearExchangeEnvelope>(2)
        thread(isDaemon = true) {
            server.accept().use { socket ->
                asked.put(checkNotNull(WearExchangeFrameCodec.read(socket.getInputStream())))
                WearExchangeFrameCodec.write(
                    socket.getOutputStream(),
                    WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_WELCOME),
                )
                asked.put(checkNotNull(WearExchangeFrameCodec.read(socket.getInputStream())))
            }
        }

        client.revokeOwnDevice(entry, TOKEN, "2.6.0")

        val hello = checkNotNull(asked.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(WearExchangeEnvelope.TYPE_HELLO, hello.type)
        assertEquals(TOKEN, hello.string("deviceToken"))
        val revoke = checkNotNull(asked.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(WearExchangeEnvelope.TYPE_REVOKE, revoke.type)
        assertEquals(DEVICE_ID, revoke.string("deviceId"))
    }

    @Test
    fun `the result text never prints the token`() {
        assertTrue(!WearExchangeEnrollResult.Enrolled(TOKEN).toString().contains(TOKEN))
    }

    private fun serve(answer: WearExchangeEnvelope): ArrayBlockingQueue<WearExchangeEnvelope> {
        val asked = ArrayBlockingQueue<WearExchangeEnvelope>(1)
        thread(isDaemon = true) {
            server.accept().use { socket ->
                asked.put(checkNotNull(WearExchangeFrameCodec.read(socket.getInputStream())))
                WearExchangeFrameCodec.write(socket.getOutputStream(), answer)
                socket.getInputStream().read()
            }
        }
        return asked
    }

    private companion object {
        const val DEVICE_ID = "AAAAAAAAAAAAAAAAAAAAAA"
        const val TOKEN = "tok3n-of-thirty-two-bytes-base64url-xxxxxx"
        const val CODE = "7KQ2M9XA"
        const val PIN = "SHA256:pinned"
        const val WAIT_SECONDS = 10L
    }
}
