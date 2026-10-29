package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.fastmediasorter.data.repository.ExchangeE2eRepository
import com.sza.fastmediasorter.data.security.e2e.E2eDeviceKey
import com.sza.fastmediasorter.data.security.e2e.E2eRecipient
import com.sza.fastmediasorter.data.security.e2e.E2eSealedBox
import com.sza.fastmediasorter.data.security.e2e.E2eSelf
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeSealIssue
import com.sza.fastmediasorter.domain.model.SftpRendezvousBroadcast
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import timber.log.Timber

/** DEVICE-EXCHANGE 27 items DI and DP: a folder through the server is used only from its opened `sealedAccess`. */
class ExchangeSealedContentOpenerTest {

    private val phone = E2eSelf(PRODUCER, E2eDeviceKey.generate())
    private val tablet = E2eSelf(CONSUMER, E2eDeviceKey.generate())
    private val opener = ExchangeSealedContentOpener(mockk(relaxed = true))
    private val sealing = ExchangeSealing(tablet, emptyList(), emptyMap(), mapOf(PRODUCER to phone.key.kem))

    private fun record(sealedAccess: String? = null, descriptor: String? = null, fingerprint: String? = FINGERPRINT) =
        SftpRendezvousResource(
            resourceId = RESOURCE,
            deviceId = PRODUCER,
            kind = SftpRendezvousResource.KIND_SFTP_SHARE,
            name = "Phone",
            descriptor = descriptor,
            updatedAtMs = 0L,
            writtenAtMs = 0L,
            ttlSeconds = 0L,
            hostKeyFingerprint = fingerprint,
            sealedAccess = sealedAccess,
        )

    private fun box(context: String = E2eSealedBox.accessContext(RESOURCE), sender: E2eSelf = phone): String {
        val access = JsonObject().apply { addProperty("descriptor", CODE) }
        val to = listOf(E2eRecipient(CONSUMER, tablet.key.publicKey))
        return E2eSealedBox.seal(access, context, sender, to).toString()
    }

    @Test
    fun `an opened box gives the descriptor and the planner sees a clear record`() {
        val opened = opener.openAccess(sealing, record(sealedAccess = box()))

        assertEquals(CODE, opened.descriptor)
        assertNull(opened.sealedAccess)
        assertNull(opened.sealIssue)
    }

    @Test
    fun `a clear access through the server is dropped as e2e-missing`() {
        val opened = opener.openAccess(sealing, record(descriptor = CODE))

        assertNull(opened.descriptor)
        assertEquals(ExchangeSealIssue.E2E_MISSING, opened.sealIssue)
    }

    @Test
    fun `a moved box, an untrusted sender and a changed host key are sealed-failed`() {
        val stranger = E2eSelf(PRODUCER, E2eDeviceKey.generate())
        val cases = listOf(
            record(sealedAccess = box(context = E2eSealedBox.accessContext("another"))),
            record(sealedAccess = box(sender = stranger)),
            record(sealedAccess = box(), fingerprint = "SHA256:BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"),
        )

        cases.forEach { case ->
            val opened = opener.openAccess(sealing, case)
            assertNull(opened.descriptor)
            assertEquals(ExchangeSealIssue.SEALED_FAILED, opened.sealIssue)
        }
    }

    @Test
    fun `a guest opens a grant from its first sender and refuses another key later`() {
        val trust = ExchangeSealedContentOpener.GrantTrust(emptyMap())
        val guest = ExchangeSealing(tablet, emptyList(), emptyMap(), emptyMap())

        val first = opener.openAccess(guest, grantRecord(grantBox(phone)), trust)
        val forged = opener.openAccess(guest, grantRecord(grantBox(E2eSelf(PRODUCER, E2eDeviceKey.generate()))), trust)

        assertEquals(CODE, first.descriptor)
        assertEquals(mapOf(GRANT to phone.key.kem), trust.learned)
        assertNull(forged.descriptor)
        assertEquals(ExchangeSealIssue.KEY_CHANGED, forged.sealIssue)
    }

    private fun grantRecord(box: String) = record(sealedAccess = box).copy(grantId = GRANT)

    private fun grantBox(sender: E2eSelf): String {
        val access = JsonObject().apply { add("access", JsonObject().apply { addProperty("descriptor", CODE) }) }
        val to = listOf(E2eRecipient(CONSUMER, tablet.key.publicKey))
        return E2eSealedBox.seal(access, E2eSealedBox.grantContext(GRANT), sender, to).toString()
    }

    @Test
    fun `a guest does not list a record that carries only the registering box`() = runTest {
        val e2e = mockk<ExchangeE2eRepository>(relaxed = true)
        coEvery { e2e.sealing(any(), any()) } returns ExchangeSealing(tablet, emptyList(), emptyMap(), emptyMap())
        coEvery { e2e.grantSenders(any()) } returns emptyMap()
        val registering = E2eSealedBox.registration(E2eSealedBox.grantContext(GRANT), phone).toString()
        val directory = DeviceExchangeDirectory(
            revision = 1L,
            devices = emptyList(),
            resources = listOf(grantRecord(registering), grantRecord(grantBox(phone)).copy(resourceId = "r-2")),
            broadcasts = listOf(broadcastRecord(registering).copy(grantId = GRANT)),
        )

        val guest = DeviceExchangeAccountEntry("h", PORT, "bob", CONSUMER, null)

        val opened = ExchangeSealedContentOpener(e2e).open(guest, directory)

        assertEquals(listOf("r-2"), opened.resources.map { it.resourceId })
        assertEquals(emptyList<SftpRendezvousBroadcast>(), opened.broadcasts)
    }

    @Test
    fun `an entry without its own key uses nothing that came sealed`() {
        val keyless = ExchangeSealing(null, emptyList(), emptyMap(), mapOf(PRODUCER to phone.key.kem))

        val opened = opener.openAccess(keyless, record(sealedAccess = box()))

        assertNull(opened.descriptor)
        assertEquals(ExchangeSealIssue.E2E_MISSING, opened.sealIssue)
    }

    private fun broadcastRecord(sealed: String?) = SftpRendezvousBroadcast(
        broadcastId = BROADCAST,
        deviceId = PRODUCER,
        title = "Phone",
        mode = "AUDIO_ONLY",
        startedAtMs = null,
        updatedAtMs = 0L,
        descriptorJson = """{"schemaVersion":1,"url":"http://192.168.1.20:8768/a.aac","mode":"AUDIO_ONLY"}""",
        writtenAtMs = 0L,
        ttlSeconds = 0L,
        sealed = sealed,
    )

    @Test
    fun `an opened broadcast box hands its own pair to the descriptor in memory`() {
        val payload = JsonObject().apply {
            add(
                "credentials",
                JsonObject().apply {
                    addProperty("user", "account")
                    addProperty("password", "own-secret")
                }
            )
        }
        val to = listOf(E2eRecipient(CONSUMER, tablet.key.publicKey))
        val box = E2eSealedBox.seal(payload, E2eSealedBox.broadcastContext(BROADCAST), phone, to).toString()

        val opened = opener.openBroadcast(sealing, broadcastRecord(box))

        val credentials = JsonParser.parseString(opened.descriptorJson).asJsonObject.getAsJsonObject("credentials")
        assertEquals("account", credentials.get("user").asString)
        assertEquals("own-secret", credentials.get("password").asString)
        assertNull(opened.sealed)
    }

    @Test
    fun `a broadcast without a box carries no credentials`() {
        val opened = opener.openBroadcast(sealing, broadcastRecord(null))

        assertNull(JsonParser.parseString(opened.descriptorJson).asJsonObject.get("credentials"))
    }

    @Test
    fun `a guest opens a granted broadcast from the grant's first sender and refuses another key later`() {
        val trust = ExchangeSealedContentOpener.GrantTrust(emptyMap())
        val guest = ExchangeSealing(tablet, emptyList(), emptyMap(), emptyMap())

        val first = opener.openBroadcast(guest, grantedBroadcast(phone), trust)
        val forged = opener.openBroadcast(guest, grantedBroadcast(E2eSelf(PRODUCER, E2eDeviceKey.generate())), trust)

        val credentials = JsonParser.parseString(first.descriptorJson).asJsonObject.getAsJsonObject("credentials")
        assertEquals("grant-user", credentials.get("user").asString)
        assertNull(first.sealIssue)
        assertEquals(mapOf(GRANT to phone.key.kem), trust.learned)
        assertNull(JsonParser.parseString(forged.descriptorJson).asJsonObject.get("credentials"))
        assertEquals(ExchangeSealIssue.KEY_CHANGED, forged.sealIssue)
    }

    private fun grantedBroadcast(sender: E2eSelf): SftpRendezvousBroadcast {
        val payload = JsonObject().apply {
            add(
                "credentials",
                JsonObject().apply {
                    addProperty("user", "grant-user")
                    addProperty("password", "grant-secret")
                }
            )
        }
        val to = listOf(E2eRecipient(CONSUMER, tablet.key.publicKey))
        val box = E2eSealedBox.seal(payload, E2eSealedBox.grantContext(GRANT), sender, to).toString()
        return broadcastRecord(box).copy(grantId = GRANT)
    }

    private fun ownBroadcastBox(
        sender: E2eSelf = phone,
        to: List<E2eRecipient> = listOf(E2eRecipient(CONSUMER, tablet.key.publicKey)),
        context: String = E2eSealedBox.broadcastContext(BROADCAST),
    ): String = E2eSealedBox.seal(JsonObject().apply { addProperty("relayKey", "k") }, context, sender, to).toString()

    @Test
    fun `an own broadcast whose box opens has no issue and the outcome is opened`() {
        val opening = opener.opening(sealing, broadcastRecord(ownBroadcastBox()))

        assertNull(opening.record.sealIssue)
        assertEquals("opened", opening.outcome)
    }

    @Test
    fun `an own broadcast whose box does not open is marked sealed-failed and says why`() {
        val stranger = E2eSelf(PRODUCER, E2eDeviceKey.generate())
        val addressedElsewhere = listOf(E2eRecipient("someone-else", tablet.key.publicKey))
        val cases = mapOf(
            "not-addressed" to ownBroadcastBox(to = addressedElsewhere),
            "untrusted-sender" to ownBroadcastBox(sender = stranger),
            "failed-bad_tag" to ownBroadcastBox(context = E2eSealedBox.broadcastContext("another")),
            "failed-malformed" to "{}",
        )

        cases.forEach { (outcome, box) ->
            val opening = opener.opening(sealing, broadcastRecord(box))

            assertEquals(outcome, ExchangeSealIssue.SEALED_FAILED, opening.record.sealIssue)
            assertEquals(outcome, opening.outcome)
            assertNull(opening.record.sealed)
        }
    }

    @Test
    fun `an own broadcast delivered without a box gets no issue`() {
        val opening = opener.opening(sealing, broadcastRecord(null))

        assertNull(opening.record.sealIssue)
        assertEquals("no-box", opening.outcome)
    }

    @Test
    fun `an own sealed broadcast on a device without its own key is marked e2e-missing`() {
        val keyless = ExchangeSealing(null, emptyList(), emptyMap(), mapOf(PRODUCER to phone.key.kem))

        val opening = opener.opening(keyless, broadcastRecord(ownBroadcastBox()))

        assertEquals(ExchangeSealIssue.E2E_MISSING, opening.record.sealIssue)
        assertEquals("no-device-key", opening.outcome)
    }

    @Test
    fun `a directory pass logs each broadcast outcome once until it changes`() = runTest {
        val e2e = mockk<ExchangeE2eRepository>(relaxed = true)
        coEvery { e2e.sealing(any(), any()) } returns sealing
        coEvery { e2e.grantSenders(any()) } returns emptyMap()
        val lines = mutableListOf<String>()
        val tree = object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                if (message.contains("opener")) lines += message
            }
        }
        Timber.plant(tree)
        try {
            val subject = ExchangeSealedContentOpener(e2e)
            val entry = DeviceExchangeAccountEntry("h", PORT, "bob", CONSUMER, null)
            val broken = broadcastRecord(ownBroadcastBox(context = E2eSealedBox.broadcastContext("another")))
            val whole = DeviceExchangeDirectory(1L, emptyList(), emptyList(), listOf(broken))

            subject.open(entry, whole)
            subject.open(entry, whole)
            subject.open(entry, whole.copy(broadcasts = listOf(broadcastRecord(ownBroadcastBox()))))

            assertEquals(2, lines.size)
            assertTrue(lines[0], lines[0].contains("outcome=failed-bad_tag"))
            assertTrue(lines[1], lines[1].contains("outcome=opened"))
            assertTrue(lines.joinToString(), lines.none { it.contains("relayKey") })
        } finally {
            Timber.uproot(tree)
        }
    }

    private companion object {
        const val PRODUCER = "phone-1"
        const val CONSUMER = "tablet-1"
        const val RESOURCE = "r-1"
        const val GRANT = "g-1"
        const val BROADCAST = "b-1"
        const val PORT = 7443
        const val KEY_HASH = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val FINGERPRINT = "SHA256:" + KEY_HASH
        const val CODE = "FMSSFTP1:h=192.168.1.20&p=2222&u=fms&pw=secret&fp=SHA256%3A" + KEY_HASH
    }
}
