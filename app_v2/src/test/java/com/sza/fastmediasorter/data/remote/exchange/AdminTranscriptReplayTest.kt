package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnection
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnector
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeTrust
import com.sza.fastmediasorter.data.repository.ExchangeServerAdminRepository
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeAdminKinds
import com.sza.fastmediasorter.domain.model.ExchangeAdminOwnLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Replays the `admin-native` transcript of the FMS_Win 245 completion corpus (DEVICE-EXCHANGE 1.4 `REMOTE-ADMIN.md`)
 * through the production repository and client: a loopback server plays each recorded server frame and checks each
 * client frame against the recorded one. The client supplies its own request ids (the corpus notes), so a request
 * is compared with the recorded id substituted, and a reply is played back under the id the client sent.
 */
class AdminTranscriptReplayTest {

    private val server = ServerSocket(0)
    private val connector = object : ExchangeConnector {
        override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection =
            ExchangeConnection(Socket(host, port), "SHA256:fake")
    }
    private val entry = DeviceExchangeAccountEntry(
        host = "127.0.0.1",
        port = server.localPort,
        login = "owner",
        deviceId = "d1",
        pinnedCertificate = "SHA256:pin",
    )
    private val repository = ExchangeServerAdminRepository(DeviceExchangeAdminClient(connector), Dispatchers.IO)
    private val mismatches = Collections.synchronizedList(mutableListOf<String>())

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `the completion corpus and its provenance match their pinned SHA-256 hashes`() {
        val pinned = listOf(
            CORPUS to "5CF90A6F6B1990155DFB346C581B97EB4F690531530F4E7A832219A1156C194B",
            "device-exchange/fmsw-245-completion/PROVENANCE.txt" to
                "6F35F2FAF9540A7357F0E6D092502F3809A754C7151FF3D78110E00DFFED3B5E",
        )
        for ((path, expected) in pinned) {
            val resource = requireNotNull(javaClass.classLoader?.getResource(path)) { "missing resource $path" }
            val bytes = resource.readBytes()
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02X".format(it) }
            assertEquals("hash mismatch for $path", expected, actual)
        }
    }

    @Test
    fun `each recorded sign-in refusal keeps its reason`() = runBlocking {
        val cases = listOf(
            "admin-disabled" to "admin-disabled",
            "admin-wrong" to "bad-credentials",
            "admin-waiting-slot" to "bad-credentials",
            "admin-rate-limited" to "rate-limited",
            "admin-capacity" to "capacity",
        )
        for ((stream, reason) in cases) {
            val steps = stepsOf(stream)
            val done = replay(steps)
            val refused = assertThrows(DeviceExchangeRefusedException::class.java) {
                runBlocking { repository.signIn(entry, passwordOf(steps)) }
            }
            assertEquals("reason of $stream", reason, refused.reason)
            assertTrue(done.await(WAIT_SECONDS, TimeUnit.SECONDS))
        }
        assertEquals(emptyList<String>(), mismatches.toList())
    }

    @Test
    fun `a server that closes without an answer is unanswered`() {
        val steps = stepsOf("admin-excluded")
        val done = replay(steps)
        assertThrows(DeviceExchangeWireException::class.java) {
            runBlocking { repository.signIn(entry, passwordOf(steps)) }
        }
        assertTrue(done.await(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(emptyList<String>(), mismatches.toList())
    }

    @Test
    fun `a lapsed token ends the session`() = runBlocking {
        val steps = stepsOf("admin-lapsed")
        val done = replay(steps)
        val session = repository.signIn(entry, passwordOf(steps))
        assertThrows(DeviceExchangeAdminSessionLapsedException::class.java) {
            runBlocking { repository.status(session) }
        }
        session.close()
        assertTrue(done.await(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(emptyList<String>(), mismatches.toList())
    }

    @Test
    fun `every carried operation matches the recorded stream`() = runBlocking {
        val steps = stepsOf("admin-ops")
        val done = replay(steps)
        val session = repository.signIn(entry, passwordOf(steps))

        val status = repository.status(session)
        assertEquals(1, status.accounts)
        assertEquals(DEFAULT_MAX_ACCOUNTS, status.maxAccounts)
        val policy = repository.policy(session)
        assertEquals(DEFAULT_CLIPBOARD_BYTES, policy.defaults.clipboardBytes)
        assertEquals(SET_MAX_ACCOUNTS, repository.setPolicy(session, SET_MAX_ACCOUNTS, null).maxAccounts)
        val alice = repository.accounts(session).single()
        assertEquals(ALICE, alice.login)
        assertNull(alice.ownLimits)
        assertEquals(DEFAULT_DEVICES, alice.effective.devices)
        assertFalse(repository.addAccount(session, BOB, "BobPassword123!".toCharArray(), MEMBER).overCap)
        repository.setAccountLimits(session, BOB, ExchangeAdminOwnLimits(devicesPerAccount = BOB_DEVICES))
        assertEquals(0, repository.setPassword(session, BOB, "NewBobPassword123!".toCharArray()))
        assertTrue(repository.setKind(session, BOB, ExchangeAdminKinds.GUEST))
        val created = repository.createInvitation(session, MEMBER, null)
        assertEquals("rbC4B3JZPj6OY1U7ZjJSCQ", created.code)
        assertTrue(created.connectionString.startsWith("FMSEXCH1:"))
        val codeHash = repository.invitations(session).single().codeHash
        assertEquals(created.invitation.codeHash, codeHash)
        repository.withdrawInvitation(session, codeHash)
        assertFalse(repository.sharedInvitation(session).enabled)
        repository.setSharedInvitation(session, "SharedPassw0rdTest!".toCharArray(), ExchangeAdminKinds.GUEST)
        assertTrue(repository.devices(session, ALICE).isEmpty())
        val revoke = assertThrows(DeviceExchangeAdminReplyException::class.java) {
            runBlocking { repository.revokeDevice(session, ALICE, "non-existent-device-id") }
        }
        assertEquals("not-found", revoke.code)
        repository.removeAccount(session, BOB)
        session.close()

        assertTrue(done.await(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(emptyList<String>(), mismatches.toList())
    }

    // The steps of one stream, without the operations a phone does not carry and the replies to them.
    private fun stepsOf(stream: String): List<JsonObject> {
        val text = requireNotNull(javaClass.classLoader?.getResource(CORPUS)) { "missing $CORPUS" }.readText()
        val transcript = JsonParser.parseString(text).asJsonObject.getAsJsonArray("transcripts")
            .map(JsonElement::getAsJsonObject)
            .single { it.get("name").asString == "admin-native" }
        val all = transcript.getAsJsonArray("steps").map(JsonElement::getAsJsonObject)
            .filter { it.get("stream").asString == stream }
        val kept = mutableListOf<JsonObject>()
        var skipReply = false
        for (step in all) {
            when {
                skipReply -> skipReply = false
                bodyOf(step)?.get("op")?.asString in NOT_CARRIED -> skipReply = true
                else -> kept += step
            }
        }
        check(kept.isNotEmpty()) { "no steps for $stream" }
        return kept
    }

    private fun passwordOf(steps: List<JsonObject>): CharArray =
        checkNotNull(bodyOf(steps.first())).get("password").asString.toCharArray()

    private fun bodyOf(step: JsonObject): JsonObject? =
        step.get("json")?.asString?.let { JsonParser.parseString(it).asJsonObject }

    private fun replay(steps: List<JsonObject>): CountDownLatch {
        val done = CountDownLatch(1)
        thread(isDaemon = true) {
            try {
                server.accept().use { socket -> play(socket, steps) }
            } catch (e: IOException) {
                mismatches += "server stream failed: ${e.message}"
            } finally {
                done.countDown()
            }
        }
        return done
    }

    private fun play(socket: Socket, steps: List<JsonObject>) {
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        var clientId: JsonElement? = null
        for (step in steps) {
            val recorded = bodyOf(step) ?: return
            if (step.get("direction").asString == CLIENT_TO_SERVER) {
                val sent = String(ByteArray(input.readInt()).also(input::readFully), Charsets.UTF_8)
                val actual = JsonParser.parseString(sent).asJsonObject
                clientId = actual.get(ID)
                // Byte-exact apart from the id: the recorded id goes back in place of the client's own.
                recorded.get(ID)?.let { actual.add(ID, it) }
                val expected = String(hexBody(step.get("hex").asString), Charsets.UTF_8)
                if (actual.toString() != expected) mismatches += "expected $expected, sent $sent"
            } else {
                if (recorded.has(ID)) clientId?.let { recorded.add(ID, it) }
                val body = recorded.toString().toByteArray(Charsets.UTF_8)
                output.writeInt(body.size)
                output.write(body)
                output.flush()
            }
        }
    }

    private fun hexBody(hex: String): ByteArray =
        hex.drop(FRAME_PREFIX_HEX).chunked(2).map { it.toInt(HEX_RADIX).toByte() }.toByteArray()

    private companion object {
        const val CORPUS = "device-exchange/fmsw-245-completion/frames-v2.json"
        const val CLIENT_TO_SERVER = "client->server"
        const val ID = "id"
        const val FRAME_PREFIX_HEX = 8
        const val HEX_RADIX = 16
        const val WAIT_SECONDS = 5L
        const val ALICE = "alice"
        const val BOB = "bob"
        const val MEMBER = ExchangeAdminKinds.MEMBER
        const val BOB_DEVICES = 4
        const val DEFAULT_MAX_ACCOUNTS = 10
        const val SET_MAX_ACCOUNTS = 8
        const val DEFAULT_DEVICES = 64
        const val DEFAULT_CLIPBOARD_BYTES = 5_368_709_120L
        val NOT_CARRIED = setOf("device.pairing-code", "grant.list")
    }
}
