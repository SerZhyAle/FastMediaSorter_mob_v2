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
import com.sza.fastmediasorter.domain.model.DeviceExchangeInvitation
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
    private val pins = RecordingPinRecorder()
    private var presentedLeaf = "SHA256:fake"
    private var presentedKey: String? = null

    private val connector = object : ExchangeConnector {
        override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection {
            trusts += trust
            connectFailure?.let { throw it }
            return ExchangeConnection(Socket(host, port), presentedLeaf, presentedKey)
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
    private val e2eKeys = InMemoryE2eKeyStore()
    private val client = DeviceExchangeAccountClient(connector, tokens, e2eKeys, pins)

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
        assertEquals(listOf("pin-v1", "e2e-v1"), enroll.members.getAsJsonArray("capabilities").map { it.asString })
        val declared = enroll.members.getAsJsonObject("e2eKey")
        assertEquals(1, declared.get("v").asInt)
        assertEquals(e2eKeys.keys.getValue(DEVICE_ID).kem, declared.get("kem").asString)
        assertEquals(KEM_LENGTH, declared.get("kem").asString.length)
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
    fun `a same-key renewal is written back as the entry's new leaf`() = runBlocking {
        val pinned = entry.copy(pinnedKey = KEY)
        tokens.save(pinned, TOKEN)
        presentedLeaf = RENEWED_LEAF
        presentedKey = KEY
        serveOnce { DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME) }

        client.openAuthenticated(pinned).close()

        assertEquals(listOf<ExchangeTrust>(ExchangeTrust.Verified(PIN, KEY)), trusts)
        assertEquals(listOf(RecordingPinRecorder.Recorded(pinned.identity, RENEWED_LEAF, KEY)), pins.recorded)
    }

    @Test
    fun `a pin saved without its key learns the key of the leaf that matched`() = runBlocking {
        tokens.save(entry, TOKEN)
        presentedLeaf = PIN
        presentedKey = KEY
        serveOnce { DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME) }

        client.openAuthenticated(entry).close()

        assertEquals(listOf(RecordingPinRecorder.Recorded(entry.identity, PIN, KEY)), pins.recorded)
    }

    @Test
    fun `a pinned entry that already knows its leaf and key records nothing`() = runBlocking {
        val pinned = entry.copy(pinnedKey = KEY)
        tokens.save(pinned, TOKEN)
        presentedLeaf = PIN
        presentedKey = KEY
        serveOnce { DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME) }

        client.openAuthenticated(pinned).close()

        assertTrue(pins.recorded.isEmpty())
    }

    @Test
    fun `an unpinned entry never gains a pin from a connect`() = runBlocking {
        val trusted = entry.copy(pinnedCertificate = null)
        tokens.save(trusted, TOKEN)
        presentedLeaf = RENEWED_LEAF
        presentedKey = KEY
        serveOnce { DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME) }

        client.openAuthenticated(trusted).close()

        assertTrue(pins.recorded.isEmpty())
    }

    @Test
    fun `an enrollment reports the leaf and key it accepted`() = runBlocking {
        presentedLeaf = PIN
        presentedKey = KEY
        serveOnce {
            DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_ENROLLED) { addProperty("deviceToken", TOKEN) }
        }

        val result = client.enroll(entry, DeviceExchangeCredential.Password(PASSWORD), enrollment)

        val enrolled = result as DeviceExchangeEnrollResult.Enrolled
        assertEquals(PIN, enrolled.leafFingerprint)
        assertEquals(KEY, enrolled.keyFingerprint)
    }

    @Test
    fun `a welcome naming the guest kind is read as guest`() = runBlocking {
        tokens.save(entry, TOKEN)
        serveOnce {
            DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME) { addProperty("accountKind", "guest") }
        }

        client.openAuthenticated(entry).use { session ->
            assertEquals("guest", session.welcome.accountKind)
        }
    }

    @Test
    fun `a welcome with no kind comes from a 0_20 server and is a member`() = runBlocking {
        tokens.save(entry, TOKEN)
        serveOnce { DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME) }

        client.openAuthenticated(entry).use { session ->
            assertEquals("member", session.welcome.accountKind)
        }
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
    fun `a signed-out device keeps its token for the next sign-in`() {
        runBlocking { tokens.save(entry, TOKEN) }
        serveOnce { DeviceExchangeEnvelope.refused(DeviceExchangeEnvelope.REASON_SIGNED_OUT) }

        val refused = assertThrows(DeviceExchangeRefusedException::class.java) {
            runBlocking { client.openAuthenticated(entry) }
        }

        assertEquals(DeviceExchangeEnvelope.REASON_SIGNED_OUT, refused.reason)
        assertEquals(TOKEN, runBlocking { tokens.token(entry) })
    }

    @Test
    fun `register with a one-time invitation carries the device members and keeps the token`() = runBlocking {
        val asked = serveOnce { request ->
            DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_ENROLLED) {
                addProperty("deviceToken", TOKEN)
            }.takeIf { request.type == DeviceExchangeEnvelope.TYPE_REGISTER }
        }

        val result = client.register(
            entry,
            DeviceExchangeCredential.Password(PASSWORD),
            DeviceExchangeInvitation.OneTime(INVITATION),
            enrollment,
        )

        val register = checkNotNull(asked.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals("Owner.Home", register.string("login"))
        assertEquals(PASSWORD, register.string("password"))
        assertEquals(INVITATION, register.string("invitation"))
        assertNull(register.string("invitationPassword"))
        assertEquals(DEVICE_ID, register.string("deviceId"))
        assertEquals("fms-android", register.string("product"))
        assertTrue(result is DeviceExchangeEnrollResult.Enrolled)
        assertEquals(TOKEN, tokens.token(entry))
    }

    @Test
    fun `register with the shared password and a refusal stores nothing`() = runBlocking {
        val asked = serveOnce { DeviceExchangeEnvelope.refused("login-taken") }

        val result = client.register(
            entry,
            DeviceExchangeCredential.Password(PASSWORD),
            DeviceExchangeInvitation.SharedPassword("Shared-Invite-1"),
            enrollment,
        )

        val register = checkNotNull(asked.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals("Shared-Invite-1", register.string("invitationPassword"))
        assertNull(register.string("invitation"))
        assertEquals(DeviceExchangeEnrollResult.Refused("login-taken"), result)
        assertNull(tokens.token(entry))
    }

    @Test
    fun `a register the server closes unanswered is no answer, not an unreachable host`() = runBlocking {
        // An older server meets `register` as an invalid first envelope and closes the stream (19 item AN).
        thread(isDaemon = true) {
            server.accept().use { socket -> DeviceExchangeFrameCodec.read(socket.getInputStream()) }
        }

        val result = client.register(
            entry,
            DeviceExchangeCredential.Password(PASSWORD),
            DeviceExchangeInvitation.OneTime(INVITATION),
            enrollment,
        )

        assertTrue(result is DeviceExchangeEnrollResult.NoAnswer)
        assertNull(tokens.token(entry))
    }

    @Test
    fun `a password change keeps the fresh token of this device`() = runBlocking {
        tokens.save(entry, TOKEN)
        val asked = serveAfterWelcome(
            DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_PASSWORD_CHANGED) {
                addProperty("deviceToken", NEW_TOKEN)
            }
        )

        client.changePassword(
            entry,
            DeviceExchangeCredential.Password(PASSWORD),
            DeviceExchangeCredential.Password(NEW_PASSWORD),
        )

        assertEquals(DeviceExchangeEnvelope.TYPE_HELLO, asked.poll(WAIT_SECONDS, TimeUnit.SECONDS)?.type)
        val change = checkNotNull(asked.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(DeviceExchangeEnvelope.TYPE_PASSWORD_CHANGE, change.type)
        assertEquals(PASSWORD, change.string("oldPassword"))
        assertEquals(NEW_PASSWORD, change.string("newPassword"))
        assertEquals(NEW_TOKEN, tokens.token(entry))
    }

    @Test
    fun `a refused password change keeps the old token`() {
        runBlocking { tokens.save(entry, TOKEN) }
        serveAfterWelcome(DeviceExchangeEnvelope.refused("password-policy"))

        val refused = assertThrows(DeviceExchangeRefusedException::class.java) {
            runBlocking {
                client.changePassword(
                    entry,
                    DeviceExchangeCredential.Password(PASSWORD),
                    DeviceExchangeCredential.Password("short"),
                )
            }
        }

        assertEquals("password-policy", refused.reason)
        assertEquals(TOKEN, runBlocking { tokens.token(entry) })
    }

    @Test
    fun `an account deletion sends the password and returns on account-deleted`() = runBlocking {
        tokens.save(entry, TOKEN)
        val asked = serveAfterWelcome(DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_ACCOUNT_DELETED))

        client.deleteAccount(entry, DeviceExchangeCredential.Password(PASSWORD))

        assertEquals(DeviceExchangeEnvelope.TYPE_HELLO, asked.poll(WAIT_SECONDS, TimeUnit.SECONDS)?.type)
        val delete = checkNotNull(asked.poll(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(DeviceExchangeEnvelope.TYPE_ACCOUNT_DELETE, delete.type)
        assertEquals(PASSWORD, delete.string("password"))
    }

    @Test
    fun `a wrong password refuses the account deletion`() {
        runBlocking { tokens.save(entry, TOKEN) }
        serveAfterWelcome(DeviceExchangeEnvelope.refused(DeviceExchangeEnvelope.REASON_BAD_CREDENTIALS))

        val refused = assertThrows(DeviceExchangeRefusedException::class.java) {
            runBlocking { client.deleteAccount(entry, DeviceExchangeCredential.Password(PASSWORD)) }
        }

        assertEquals(DeviceExchangeEnvelope.REASON_BAD_CREDENTIALS, refused.reason)
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
        const val KEM_LENGTH = 87
        const val OTHER_DEVICE_ID = "CCCCCCCCCCCCCCCCCCCCCC"
        const val TOKEN = "tok3n-of-thirty-two-bytes-base64url-xxxxxx"
        const val PASSWORD = "Passw0rdTest"
        const val NEW_PASSWORD = "N3wPassw0rdTest"
        const val NEW_TOKEN = "fresh-token-of-thirty-two-bytes-base64url-x"
        const val INVITATION = "q3Vb7YtK0xP2mN9s-_R4wA"
        const val PIN = "SHA256:pinned"
        const val KEY = "SHA256:key"
        const val RENEWED_LEAF = "SHA256:renewed"
        const val RESOURCE_ID = "q3Vb7YtK0xP2mN9sLfR4wA"
        const val SHARE_ID = "BBBBBBBBBBBBBBBBBBBBBB"
        const val PAYLOAD = "SSH-2.0-test\r\n"
        const val PAIRING_CODE = "7KQ2M9XA"
        const val EXPIRES_AT = "2026-10-11T12:10:00Z"
        const val WAIT_SECONDS = 10L
    }
}
