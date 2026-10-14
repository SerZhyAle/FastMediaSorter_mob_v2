package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeCertificateChangedException
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnection
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnector
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeTrust
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeTunnelUnavailableException
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.DeviceExchangeCredential
import com.sza.fastmediasorter.domain.model.DeviceExchangeEnrollResult
import com.sza.fastmediasorter.domain.model.DeviceExchangeEnrollment
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * The account client of contract DEVICE-EXCHANGE sections 5 and 7.2-7.5 against a fake exchange server on
 * loopback, speaking plain frames (TLS and the trust decision are the connector's, tested apart).
 */
class DeviceExchangeAccountClientTest {

    private val server = ServerSocket(0)
    private val trusts = mutableListOf<ExchangeTrust>()
    private var connectFailure: IOException? = null
    private val tokens = MemoryTokens()

    private val connector = object : ExchangeConnector {
        override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection {
            trusts += trust
            connectFailure?.let { throw it }
            return ExchangeConnection(Socket(host, port), "SHA256:fake")
        }
    }

    private val entry = DeviceExchangeAccountEntry(
        host = "127.0.0.1",
        port = server.localPort,
        login = "Owner.Home",
        deviceId = DEVICE_ID,
        pinnedCertificate = PIN,
    )
    private val enrollment = DeviceExchangeEnrollment(
        deviceName = "Pixel",
        productVersion = "2.6.0",
        roles = listOf("resource-producer", "resource-consumer"),
    )
    private val client = DeviceExchangeAccountClient(connector, tokens)

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `enroll keeps the token and never the password`() = runBlocking {
        val asked = serveOnce { request ->
            DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_ENROLLED) {
                addProperty("deviceToken", TOKEN)
                addProperty("account", "owner.home")
                addProperty("publicEndpoint", "exchange.example.net:44022")
                addProperty("keepaliveSeconds", 30)
                add(
                    "features",
                    JsonArray().apply {
                        add("directory")
                        add("tunnel")
                    },
                )
            }.takeIf { request.type == DeviceExchangeEnvelope.TYPE_ENROLL }
        }

        val result = client.enroll(entry, DeviceExchangeCredential.Password(PASSWORD), enrollment)

        val enroll = checkNotNull(asked.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals("Owner.Home", enroll.string("login"))
        assertEquals(PASSWORD, enroll.string("password"))
        assertEquals(DEVICE_ID, enroll.string("deviceId"))
        assertEquals("fms-android", enroll.string("product"))
        assertEquals("android", enroll.string("platform"))
        assertEquals(listOf("pin-v1"), enroll.members.getAsJsonArray("capabilities").map { it.asString })
        val welcome = (result as DeviceExchangeEnrollResult.Enrolled).welcome
        assertEquals("exchange.example.net:44022", welcome.publicEndpoint)
        assertEquals(listOf("directory", "tunnel"), welcome.features)
        assertEquals(TOKEN, tokens.token(entry))
        assertFalse(tokens.values.values.any { it.contains(PASSWORD) })
        assertEquals(listOf<ExchangeTrust>(ExchangeTrust.Verified(PIN)), trusts)
    }

    @Test
    fun `a pairing code enrolls instead of the password`() = runBlocking {
        val asked =
            serveOnce {
                DeviceExchangeEnvelope.of(
                    DeviceExchangeEnvelope.TYPE_ENROLLED
                ) { addProperty("deviceToken", TOKEN) }
            }

        client.enroll(entry, DeviceExchangeCredential.PairingCode("7KQ2M9XA"), enrollment)

        val enroll = checkNotNull(asked.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals("7KQ2M9XA", enroll.string("pairingCode"))
        assertNull(enroll.string("password"))
    }

    @Test
    fun `bad credentials store nothing`() = runBlocking {
        serveOnce { DeviceExchangeEnvelope.refused(DeviceExchangeEnvelope.REASON_BAD_CREDENTIALS) }

        val result = client.enroll(entry, DeviceExchangeCredential.Password(PASSWORD), enrollment)

        assertEquals(DeviceExchangeEnrollResult.Refused(DeviceExchangeEnvelope.REASON_BAD_CREDENTIALS), result)
        assertNull(tokens.token(entry))
    }

    @Test
    fun `a changed pinned certificate shows both fingerprints`() = runBlocking {
        connectFailure = ExchangeCertificateChangedException(PIN, "SHA256:new")

        val result = client.enroll(entry, DeviceExchangeCredential.Password(PASSWORD), enrollment)

        assertEquals(DeviceExchangeEnrollResult.CertificateChanged(PIN, "SHA256:new"), result)
    }

    @Test
    fun `hello carries the token and opens an authenticated session`() = runBlocking {
        tokens.save(entry, TOKEN)
        val asked =
            serveOnce {
                DeviceExchangeEnvelope.of(
                    DeviceExchangeEnvelope.TYPE_WELCOME
                ) { addProperty("keepaliveSeconds", 45) }
            }

        client.openAuthenticated(entry, keepaliveSeconds = 30).use { session ->
            assertEquals(45, session.welcome.keepaliveSeconds)
        }

        val hello = checkNotNull(asked.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(DeviceExchangeEnvelope.TYPE_HELLO, hello.type)
        assertEquals(DEVICE_ID, hello.string("deviceId"))
        assertEquals(TOKEN, hello.string("deviceToken"))
        assertEquals(30, hello.int("keepaliveSeconds"))
    }

    @Test
    fun `a revoked device forgets its token`() {
        runBlocking { tokens.save(entry, TOKEN) }
        serveOnce { DeviceExchangeEnvelope.refused(DeviceExchangeEnvelope.REASON_DEVICE_REVOKED) }

        val refused = assertThrows(DeviceExchangeRefusedException::class.java) {
            runBlocking { client.openAuthenticated(entry) }
        }

        assertEquals(DeviceExchangeEnvelope.REASON_DEVICE_REVOKED, refused.reason)
        assertNull(runBlocking { tokens.token(entry) })
    }

    @Test
    fun `an entry that never enrolled opens nothing`() {
        assertThrows(DeviceExchangeNotEnrolledException::class.java) {
            runBlocking { client.openAuthenticated(entry) }
        }
        assertTrue(trusts.isEmpty())
    }

    @Test
    fun `revoking the own device sends its deviceId and closes without bye`() = runBlocking {
        tokens.save(entry, TOKEN)
        val asked = ArrayBlockingQueue<DeviceExchangeEnvelope>(2)
        val closedClean = ArrayBlockingQueue<Boolean>(1)
        thread(isDaemon = true) {
            server.accept().use { socket ->
                asked.put(checkNotNull(DeviceExchangeFrameCodec.read(socket.getInputStream())))
                DeviceExchangeFrameCodec.write(
                    socket.getOutputStream(),
                    DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME)
                )
                asked.put(checkNotNull(DeviceExchangeFrameCodec.read(socket.getInputStream())))
                closedClean.put(DeviceExchangeFrameCodec.read(socket.getInputStream()) == null)
            }
        }

        client.revokeOwnDevice(entry)

        assertEquals(DeviceExchangeEnvelope.TYPE_HELLO, asked.poll(WAIT_SECONDS, TimeUnit.SECONDS)?.type)
        val revoke = checkNotNull(asked.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(DeviceExchangeEnvelope.TYPE_REVOKE, revoke.type)
        assertEquals(DEVICE_ID, revoke.string("deviceId"))
        assertEquals(true, closedClean.poll(WAIT_SECONDS, TimeUnit.SECONDS))
    }

    @Test
    fun `a tunnel by resourceId splices bytes end to end`() = runBlocking {
        tokens.save(entry, TOKEN)
        val asked = ArrayBlockingQueue<DeviceExchangeEnvelope>(2)
        thread(isDaemon = true) {
            server.accept().use { socket ->
                asked.put(checkNotNull(DeviceExchangeFrameCodec.read(socket.getInputStream())))
                DeviceExchangeFrameCodec.write(
                    socket.getOutputStream(),
                    DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME)
                )
                asked.put(checkNotNull(DeviceExchangeFrameCodec.read(socket.getInputStream())))
                DeviceExchangeFrameCodec.write(
                    socket.getOutputStream(),
                    DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CONNECTED)
                )
                echo(socket)
            }
        }

        val stream = client.connectByResourceId(entry, RESOURCE_ID)
        val echoed = stream.use { roundTrip(it) }

        assertEquals(DeviceExchangeEnvelope.TYPE_HELLO, asked.poll(WAIT_SECONDS, TimeUnit.SECONDS)?.type)
        val connect = checkNotNull(asked.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(DeviceExchangeEnvelope.TYPE_CONNECT, connect.type)
        assertEquals(RESOURCE_ID, connect.string("resourceId"))
        assertEquals(PAYLOAD, echoed)
    }

    @Test
    fun `a tunnel by shareId needs no hello and splices bytes end to end`() {
        val asked = ArrayBlockingQueue<DeviceExchangeEnvelope>(1)
        thread(isDaemon = true) {
            server.accept().use { socket ->
                asked.put(checkNotNull(DeviceExchangeFrameCodec.read(socket.getInputStream())))
                DeviceExchangeFrameCodec.write(
                    socket.getOutputStream(),
                    DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CONNECTED)
                )
                echo(socket)
            }
        }

        val stream = DeviceExchangeAccountClient.connectByShareId(
            connector,
            "127.0.0.1",
            server.localPort,
            SHARE_ID,
            ExchangeTrust.Verified("SHA256:fake"),
        )
        val echoed = stream.use { roundTrip(it) }

        val connect = checkNotNull(asked.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(DeviceExchangeEnvelope.TYPE_CONNECT, connect.type)
        assertEquals(SHARE_ID, connect.string("shareId"))
        assertEquals(PAYLOAD, echoed)
    }

    @Test
    fun `an unavailable resource is the tunnel failure`() {
        runBlocking { tokens.save(entry, TOKEN) }
        thread(isDaemon = true) {
            server.accept().use { socket ->
                DeviceExchangeFrameCodec.read(socket.getInputStream())
                DeviceExchangeFrameCodec.write(
                    socket.getOutputStream(),
                    DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME)
                )
                DeviceExchangeFrameCodec.read(socket.getInputStream())
                DeviceExchangeFrameCodec.write(
                    socket.getOutputStream(),
                    DeviceExchangeEnvelope.refused(DeviceExchangeEnvelope.REASON_UNAVAILABLE),
                )
            }
        }

        assertThrows(ExchangeTunnelUnavailableException::class.java) {
            runBlocking { client.connectByResourceId(entry, RESOURCE_ID) }
        }
    }

    @Test
    fun `a pairing code is asked for after hello and read past an unrelated push`() = runBlocking {
        tokens.save(entry, TOKEN)
        val asked = serveAfterWelcome(
            DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CHANGED),
            DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_PAIRING_CODE) {
                addProperty("code", PAIRING_CODE)
                addProperty("expiresAt", EXPIRES_AT)
            },
        )

        val issued = client.requestPairingCode(entry)

        assertEquals(DeviceExchangeEnvelope.TYPE_HELLO, asked.poll(WAIT_SECONDS, TimeUnit.SECONDS)?.type)
        assertEquals(DeviceExchangeEnvelope.TYPE_PAIRING_REQUEST, asked.poll(WAIT_SECONDS, TimeUnit.SECONDS)?.type)
        assertEquals(PAIRING_CODE, issued.code)
        assertEquals(EXPIRES_AT, issued.expiresAt)
        assertFalse(issued.toString().contains(PAIRING_CODE))
    }

    @Test
    fun `a refused pairing request carries its reason`() {
        runBlocking { tokens.save(entry, TOKEN) }
        serveAfterWelcome(DeviceExchangeEnvelope.refused(DeviceExchangeEnvelope.REASON_RATE_LIMITED))

        val failure = assertThrows(DeviceExchangeRefusedException::class.java) {
            runBlocking { client.requestPairingCode(entry) }
        }

        assertEquals(DeviceExchangeEnvelope.REASON_RATE_LIMITED, failure.reason)
    }

    @Test
    fun `the directory lists the account devices past an unrelated push`() = runBlocking {
        tokens.save(entry, TOKEN)
        val asked = serveAfterWelcome(
            DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CHANGED),
            DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_DIRECTORY) {
                addProperty("revision", 7)
                add(
                    "devices",
                    JsonArray().apply {
                        add(deviceRecord(DEVICE_ID, "Pixel", "online"))
                        add(deviceRecord(OTHER_DEVICE_ID, "Desk PC", "offline"))
                        add(JsonObject().apply { addProperty("deviceName", "no id") })
                    },
                )
            },
        )

        val directory = client.listDirectory(entry)

        assertEquals(DeviceExchangeEnvelope.TYPE_HELLO, asked.poll(WAIT_SECONDS, TimeUnit.SECONDS)?.type)
        assertEquals(DeviceExchangeEnvelope.TYPE_LIST, asked.poll(WAIT_SECONDS, TimeUnit.SECONDS)?.type)
        assertEquals(7L, directory.revision)
        assertEquals(listOf(DEVICE_ID, OTHER_DEVICE_ID), directory.devices.map { it.deviceId })
        assertTrue(directory.devices[0].isOnline)
        assertFalse(directory.devices[1].isOnline)
    }

    @Test
    fun `a refused list carries its reason`() {
        runBlocking { tokens.save(entry, TOKEN) }
        serveAfterWelcome(DeviceExchangeEnvelope.refused(DeviceExchangeEnvelope.REASON_UNSUPPORTED))

        val failure = assertThrows(DeviceExchangeRefusedException::class.java) {
            runBlocking { client.listDirectory(entry) }
        }

        assertEquals(DeviceExchangeEnvelope.REASON_UNSUPPORTED, failure.reason)
    }

    @Test
    fun `revoking another device names that device`() = runBlocking {
        tokens.save(entry, TOKEN)
        val asked = serveAfterWelcome()

        client.revokeDevice(entry, OTHER_DEVICE_ID)

        assertEquals(DeviceExchangeEnvelope.TYPE_HELLO, asked.poll(WAIT_SECONDS, TimeUnit.SECONDS)?.type)
        val revoke = checkNotNull(asked.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(DeviceExchangeEnvelope.TYPE_REVOKE, revoke.type)
        assertEquals(OTHER_DEVICE_ID, revoke.string("deviceId"))
    }

    private fun deviceRecord(deviceId: String, name: String, presence: String) = JsonObject().apply {
        addProperty("deviceId", deviceId)
        addProperty("deviceName", name)
        addProperty("product", "fms-android")
        addProperty("presence", presence)
    }

    /** Welcomes `hello`, reads the next envelope and answers it with [answers] in order. */
    private fun serveAfterWelcome(vararg answers: DeviceExchangeEnvelope): ArrayBlockingQueue<DeviceExchangeEnvelope> {
        val asked = ArrayBlockingQueue<DeviceExchangeEnvelope>(2)
        thread(isDaemon = true) {
            server.accept().use { socket ->
                asked.put(checkNotNull(DeviceExchangeFrameCodec.read(socket.getInputStream())))
                DeviceExchangeFrameCodec.write(
                    socket.getOutputStream(),
                    DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME)
                )
                asked.put(checkNotNull(DeviceExchangeFrameCodec.read(socket.getInputStream())))
                answers.forEach { DeviceExchangeFrameCodec.write(socket.getOutputStream(), it) }
                socket.getInputStream().read()
            }
        }
        return asked
    }

    /** Answers the first envelope of one connection with [answer] and hands that envelope to the test. */
    private fun serveOnce(
        answer: (DeviceExchangeEnvelope) -> DeviceExchangeEnvelope?
    ): ArrayBlockingQueue<DeviceExchangeEnvelope> {
        val asked = ArrayBlockingQueue<DeviceExchangeEnvelope>(1)
        thread(isDaemon = true) {
            server.accept().use { socket ->
                val request = checkNotNull(DeviceExchangeFrameCodec.read(socket.getInputStream()))
                asked.put(request)
                answer(request)?.let { DeviceExchangeFrameCodec.write(socket.getOutputStream(), it) }
                // Holds the stream until the client closes it, as a server would.
                socket.getInputStream().read()
            }
        }
        return asked
    }

    private fun echo(socket: Socket) {
        val buffer = ByteArray(PAYLOAD.length)
        readFully(socket.getInputStream(), buffer)
        socket.getOutputStream().apply {
            write(buffer)
            flush()
        }
        socket.getInputStream().read()
    }

    private fun roundTrip(stream: ExchangeConnection): String {
        stream.output.write(PAYLOAD.toByteArray())
        stream.output.flush()
        val reply = ByteArray(PAYLOAD.length)
        readFully(stream.input, reply)
        return String(reply)
    }

    private fun readFully(input: java.io.InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) offset += input.read(buffer, offset, buffer.size - offset)
    }

    private class MemoryTokens : DeviceExchangeTokenStore {
        val values = mutableMapOf<String, String>()

        override suspend fun token(entry: DeviceExchangeAccountEntry): String? = values[entry.identity]

        override suspend fun save(entry: DeviceExchangeAccountEntry, token: String): Boolean {
            values[entry.identity] = token
            return true
        }

        override suspend fun forget(entry: DeviceExchangeAccountEntry) {
            values.remove(entry.identity)
        }
    }

    private companion object {
        const val DEVICE_ID = "AAAAAAAAAAAAAAAAAAAAAA"
        const val OTHER_DEVICE_ID = "CCCCCCCCCCCCCCCCCCCCCC"
        const val TOKEN = "tok3n-of-thirty-two-bytes-base64url-xxxxxx"
        const val PASSWORD = "Passw0rdTest"
        const val PIN = "SHA256:pinned"
        const val RESOURCE_ID = "q3Vb7YtK0xP2mN9sLfR4wA"
        const val SHARE_ID = "BBBBBBBBBBBBBBBBBBBBBB"
        const val PAYLOAD = "SSH-2.0-test\r\n"
        const val PAIRING_CODE = "7KQ2M9XA"
        const val EXPIRES_AT = "2026-10-11T12:10:00Z"
        const val WAIT_SECONDS = 10L
    }
}
