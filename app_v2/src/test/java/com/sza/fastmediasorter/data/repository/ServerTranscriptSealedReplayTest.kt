package com.sza.fastmediasorter.data.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeDirectory
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeEnvelope
import com.sza.fastmediasorter.data.remote.exchange.InMemoryE2eKeyStore
import com.sza.fastmediasorter.data.repository.settings.ExchangeE2eTrustStore
import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import com.sza.fastmediasorter.data.security.e2e.E2eDeviceKey
import com.sza.fastmediasorter.data.security.e2e.E2eOpenResult
import com.sza.fastmediasorter.data.security.e2e.E2eRecipient
import com.sza.fastmediasorter.data.security.e2e.E2eSealedBox
import com.sza.fastmediasorter.data.security.e2e.E2eSegmentStream
import com.sza.fastmediasorter.data.security.e2e.E2eSelf
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeSealIssue
import com.sza.fastmediasorter.domain.model.ExchangeSealState
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Replays the DEVICE-EXCHANGE sealed content transcripts (fmsw-234):
 * - sealed.cases (12 cases covering sealed-folder, sealed-grant, sealed-clipboard)
 * - sealed-devices transcript (device key tracking, capability, rotation)
 * - sealed-folder transcript (sealedAccess / broadcast box opening and revocation)
 * - sealed-grant transcript (grant registration, replacement, ungrant, KEY_CHANGED)
 *
 * S4365 / DEVICE-EXCHANGE 1.3 amendment D6.
 */
class ServerTranscriptSealedReplayTest {

    private val e2eText: String by lazy {
        requireNotNull(javaClass.classLoader?.getResource("device-exchange/e2e-v1.json")).readText()
    }
    private val e2eRoot: JsonObject by lazy { JsonParser.parseString(e2eText).asJsonObject }
    private val e2eDevices: List<JsonObject> by lazy {
        e2eRoot.getAsJsonArray("devices").map { it.asJsonObject }
    }

    private val fmsw234Text: String by lazy {
        requireNotNull(javaClass.classLoader?.getResource("device-exchange/fmsw-234/frames-v2.json")).readText()
    }
    private val fmsw234Root: JsonObject by lazy { JsonParser.parseString(fmsw234Text).asJsonObject }

    @Test
    fun `every recorded sealed case opens to its expected payload for each recipient`() {
        val sealedObj = fmsw234Root.getAsJsonObject("sealed")
        val cases = sealedObj.getAsJsonArray("cases").map { it.asJsonObject }

        assertEquals("must have exactly 12 sealed cases in fmsw-234", 12, cases.size)

        for (case in cases) {
            val caseName = case.get("name").asString
            val context = case.get("context").asString
            val fromObj = case.getAsJsonObject("from")
            val fromDevice = fromObj.get("device").asString
            val fromDeviceId = fromObj.get("deviceId").asString
            val fromKem = e2eDevices.first { it.get("name").asString == fromDevice }
                .getAsJsonObject("e2eKey").get("kem").asString

            val toList = when (val toEl = case.get("to")) {
                is JsonArray -> toEl.map { it.asJsonObject }
                is JsonObject -> listOf(toEl)
                else -> emptyList()
            }
            assertTrue("case $caseName must have recipients", toList.isNotEmpty())

            for (toObj in toList) {
                val readerDevice = toObj.get("device").asString
                val readerDeviceId = toObj.get("deviceId").asString
                val readerPrivKey = e2eDevices.first { it.get("name").asString == readerDevice }
                    .get("privateKey").asString
                val readerKey = E2eDeviceKey.fromPrivate(E2eBytes.fromHex(readerPrivKey))
                val readerSelf = E2eSelf(readerDeviceId, readerKey)

                val box = case.get("box")
                val result = E2eSealedBox.open(
                    box = box,
                    expectedContext = context,
                    self = readerSelf,
                ) { senderId, senderKey ->
                    senderId == fromDeviceId && (E2eDeviceKey.decodeKem(fromKem)?.contentEquals(senderKey) == true)
                }

                assertTrue(
                    "case $caseName for reader $readerDevice must open, got $result",
                    result is E2eOpenResult.Opened
                )
                val opened = result as E2eOpenResult.Opened
                assertEquals(
                    "payload mismatch for $caseName reader $readerDevice",
                    case.getAsJsonObject("payload"),
                    opened.payload
                )

                if (case.has("clipboard")) {
                    val clipboardObj = case.getAsJsonObject("clipboard")
                    val valueKey = E2eSegmentStream.valueKey(opened.contentKey)

                    // Find raw bytes in sealed-clipboard transcript
                    val transcript = fmsw234Root.getAsJsonArray("transcripts").map { it.asJsonObject }
                        .first { it.get("name").asString == "sealed-clipboard" }
                    val rawStep = transcript.getAsJsonArray("steps").map { it.asJsonObject }
                        .first { it.has("hex") && !it.has("json") }
                    val rawBytes = E2eBytes.fromHex(rawStep.get("hex").asString)

                    val outStream = ByteArrayOutputStream()
                    E2eSegmentStream.open(rawBytes.inputStream(), outStream, valueKey)

                    val decryptedText = outStream.toByteArray().toString(Charsets.UTF_8)
                    assertEquals(clipboardObj.get("clear").asString, decryptedText)

                    val clearSha = E2eBytes.hex(E2eBytes.sha256(outStream.toByteArray()))
                    assertEquals(clipboardObj.get("clearSha256").asString, clearSha)

                    assertEquals(clipboardObj.get("sealedSize").asLong, rawBytes.size.toLong())
                    val sealedSha = E2eBytes.hex(E2eBytes.sha256(rawBytes))
                    assertEquals(clipboardObj.get("sealedSha256").asString, sealedSha)
                }
            }
        }
    }

    @Test
    fun `directory replay proves device key replacement and revocation in sealed-devices`() = runBlocking {
        val transcript = fmsw234Root.getAsJsonArray("transcripts").map { it.asJsonObject }
            .first { it.get("name").asString == "sealed-devices" }
        val steps = transcript.getAsJsonArray("steps").map { it.asJsonObject }

        val dirRev5Step = steps.first {
            it.has("json") &&
                it.get("json").asString.contains("\"revision\":5") &&
                it.get("json").asString.contains("\"type\":\"directory\"")
        }
        val dirRev6Step = steps.first {
            it.has("json") &&
                it.get("json").asString.contains("\"revision\":6") &&
                it.get("json").asString.contains("\"type\":\"directory\"")
        }

        val dirRev5 = DeviceExchangeDirectory.from(DeviceExchangeEnvelope.decode(dirRev5Step.get("json").asString))
        val dirRev6 = DeviceExchangeDirectory.from(DeviceExchangeEnvelope.decode(dirRev6Step.get("json").asString))

        val phonePrivHex = e2eDevices.first { it.get("name").asString == "phone" }.get("privateKey").asString
        val phoneKey = E2eDeviceKey.fromPrivate(E2eBytes.fromHex(phonePrivHex))
        val phoneId = "TqeXuVTNb4LpmDXnaXlw_w"
        val tabletId = "hrbbWm6H_F-UwViNKkSpKw"
        val pcId = "uktA8-r8vBzc2AQEF4sx2A"

        val keyStore = InMemoryE2eKeyStore()
        keyStore.keys[phoneId] = phoneKey

        val trustedMap = mutableMapOf<String, String>()
        val trustStore = mockk<ExchangeE2eTrustStore>()
        coEvery { trustStore.trusted(phoneId) } answers { trustedMap.toMap() }
        coEvery { trustStore.learn(phoneId, any()) } answers {
            val firstSeen = secondArg<Map<String, String>>()
            firstSeen.forEach { (k, v) -> if (k !in trustedMap) trustedMap[k] = v }
        }
        coEvery { trustStore.retain(phoneId, any()) } answers {
            val listed = secondArg<Set<String>>()
            trustedMap.keys.retainAll(listed)
        }

        val repo = ExchangeE2eRepository(keyStore, trustStore)
        val entry = DeviceExchangeAccountEntry("127.0.0.1", 22022, "owner", phoneId, null)

        // Revision 5: 2 devices (phone and tablet), both capable
        val sealing5 = repo.sealing(entry, dirRev5.devices)
        assertEquals(1, sealing5.recipients.size)
        assertEquals(tabletId, sealing5.recipients[0].deviceId)
        assertEquals(ExchangeSealState.TRUSTED, sealing5.states[tabletId])
        assertEquals(ExchangeSealState.TRUSTED, sealing5.states[phoneId])

        // Revision 6: 3 devices (PC added without e2e-v1 capability)
        val sealing6 = repo.sealing(entry, dirRev6.devices)
        assertEquals(1, sealing6.recipients.size)
        assertEquals(ExchangeSealState.E2E_MISSING, sealing6.states[pcId])

        // Revocation: when tablet is removed, retain drops tablet
        trustStore.retain(phoneId, setOf(phoneId))
        assertTrue("revoked tablet must be removed from trusted map", tabletId !in trustedMap)
    }

    @Test
    fun `directory replay proves sealed folder access opening and revocation in sealed-folder`() = runBlocking {
        val transcript = fmsw234Root.getAsJsonArray("transcripts").map { it.asJsonObject }
            .first { it.get("name").asString == "sealed-folder" }
        val steps = transcript.getAsJsonArray("steps").map { it.asJsonObject }

        val dirRev14Step = steps.first {
            it.has("json") &&
                it.get("json").asString.contains("\"revision\":14") &&
                it.get("json").asString.contains("\"type\":\"directory\"")
        }
        val dirRev14 = DeviceExchangeDirectory.from(DeviceExchangeEnvelope.decode(dirRev14Step.get("json").asString))

        assertEquals(1, dirRev14.resources.size)
        assertEquals(1, dirRev14.broadcasts.size)

        val tabletPrivHex = e2eDevices.first { it.get("name").asString == "tablet" }.get("privateKey").asString
        val tabletKey = E2eDeviceKey.fromPrivate(E2eBytes.fromHex(tabletPrivHex))
        val tabletId = "8Iykvix08hEQapkCPAQuoQ"
        val phoneId = "K8xpP12DJgWYz6Pzg6keLA"
        val phoneKem = e2eDevices.first { it.get("name").asString == "phone" }.getAsJsonObject(
            "e2eKey"
        ).get("kem").asString

        val tabletSelf = E2eSelf(tabletId, tabletKey)
        val sealing = ExchangeSealing(
            self = tabletSelf,
            recipients = emptyList(),
            states = emptyMap(),
            trusted = mapOf(phoneId to phoneKem),
        )

        val repo = mockk<ExchangeE2eRepository>()
        coEvery { repo.sealing(any(), any()) } returns sealing
        coEvery { repo.grantSenders(any()) } returns emptyMap()
        coEvery { repo.learnGrantSenders(any(), any()) } returns Unit

        val opener = ExchangeSealedContentOpener(repo)
        val entry = DeviceExchangeAccountEntry("127.0.0.1", 22022, "owner", tabletId, null)

        val opened = opener.open(entry, dirRev14)

        // Resource was opened
        val openedResource = opened.resources[0]
        assertEquals("FMSSFTP2:vector-not-a-secret", openedResource.descriptor)
        assertNull(openedResource.sealedAccess)
        assertNull(openedResource.sealIssue)

        // Broadcast was opened
        val openedBroadcast = opened.broadcasts[0]
        val cases = fmsw234Root.getAsJsonObject("sealed").getAsJsonArray("cases").map { it.asJsonObject }
        val broadcastPayload = cases.first { it.get("name").asString == "sealed-folder/broadcast" }.getAsJsonObject(
            "payload"
        )
        val expectedRelayKey = broadcastPayload.get("relayKey").asString
        assertTrue(
            "opened descriptorJson must contain relayKey",
            openedBroadcast.descriptorJson.contains(expectedRelayKey)
        )
        assertNull(openedBroadcast.sealed)
        assertNull(openedBroadcast.sealIssue)

        // Revocation proof: find revoked box in sealed.cases (sealed-folder/access-device-revoked)
        val revokedCase = cases.first { it.get("name").asString == "sealed-folder/access-device-revoked" }
        val revokedBox = revokedCase.get("box")

        // Try opening revoked box as watch (C7rMfWxafTiRna2RkSrgrg)
        val watchPrivHex = e2eDevices.first { it.get("name").asString == "watch" }.get("privateKey").asString
        val watchKey = E2eDeviceKey.fromPrivate(E2eBytes.fromHex(watchPrivHex))
        val watchSelf = E2eSelf("C7rMfWxafTiRna2RkSrgrg", watchKey)
        val watchSealing = ExchangeSealing(watchSelf, emptyList(), emptyMap(), mapOf(phoneId to phoneKem))

        val resourceWithRevokedBox = openedResource.copy(
            descriptor = null,
            sealedAccess = revokedBox.toString(),
            hostKeyFingerprint = "SHA256:fake",
        )
        val openedForWatch = opener.openAccess(watchSealing, resourceWithRevokedBox)
        assertNull("revoked device must not open descriptor", openedForWatch.descriptor)
        assertEquals(
            "revoked device access must be SEALED_FAILED",
            ExchangeSealIssue.SEALED_FAILED,
            openedForWatch.sealIssue
        )
    }

    @Test
    fun `directory replay proves guest grant registration, replacement and ungrant in sealed-grant`() = runBlocking {
        val transcript = fmsw234Root.getAsJsonArray("transcripts").map { it.asJsonObject }
            .first { it.get("name").asString == "sealed-grant" }
        val steps = transcript.getAsJsonArray("steps").map { it.asJsonObject }

        val cases = fmsw234Root.getAsJsonObject("sealed").getAsJsonArray("cases").map { it.asJsonObject }
        val replacedGrantCase = cases.first { it.get("name").asString == "sealed-grant/grant-access-replaced" }

        val guestPrivHex = e2eDevices.first { it.get("name").asString == "guest" }.get("privateKey").asString
        val guestKey = E2eDeviceKey.fromPrivate(E2eBytes.fromHex(guestPrivHex))
        val guestId = "m0uFU9mDGkX7F5BUoSZd9w"
        val phoneKem = e2eDevices.first { it.get("name").asString == "phone" }.getAsJsonObject(
            "e2eKey"
        ).get("kem").asString

        val guestSelf = E2eSelf(guestId, guestKey)
        val guestSealing = ExchangeSealing(guestSelf, emptyList(), emptyMap(), emptyMap())

        val opener = ExchangeSealedContentOpener(mockk(relaxed = true))
        val grantTrust = ExchangeSealedContentOpener.GrantTrust(emptyMap())

        val grantId = "oRARogh11uH-ihvyza-UhQ"
        val resource = SftpRendezvousResource(
            resourceId = "vector-res-id",
            deviceId = "yeNYb65tQd5squCnwn7-Dw",
            kind = SftpRendezvousResource.KIND_SFTP_SHARE,
            name = "Folder",
            descriptor = null,
            updatedAtMs = 0L,
            writtenAtMs = 0L,
            ttlSeconds = 0L,
            grantId = grantId,
            sealedAccess = replacedGrantCase.get("box").toString(),
        )

        val opened = opener.openAccess(guestSealing, resource, grantTrust)
        assertEquals("FMSSFTP2:vector-guest-not-a-secret", opened.descriptor)
        assertNull(opened.sealIssue)
        assertEquals(phoneKem, grantTrust.learned[grantId])

        // Forged grant sender from another key must fail with KEY_CHANGED
        val forgedKey = E2eDeviceKey.generate()
        val forgedSender = E2eSelf("yeNYb65tQd5squCnwn7-Dw", forgedKey)
        val forgedBox = E2eSealedBox.seal(
            payload = JsonObject().apply { add("access", JsonObject().apply { addProperty("descriptor", "forged") }) },
            context = E2eSealedBox.grantContext(grantId),
            sender = forgedSender,
            recipients = listOf(E2eRecipient(guestId, guestKey.publicKey)),
        )

        val forgedResource = resource.copy(sealedAccess = forgedBox.toString())
        val forgedOpened = opener.openAccess(guestSealing, forgedResource, grantTrust)
        assertNull(forgedOpened.descriptor)
        assertEquals(
            "grant from unexpected key must be KEY_CHANGED",
            ExchangeSealIssue.KEY_CHANGED,
            forgedOpened.sealIssue
        )
    }
}
