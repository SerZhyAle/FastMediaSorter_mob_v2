package com.sza.fastmediasorter.data.security.e2e

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.fastmediasorter.data.security.e2e.E2eBytes.fromHex
import com.sza.fastmediasorter.data.security.e2e.E2eBytes.hex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The contract artifact `vectors/e2e-v1.json` (DEVICE-EXCHANGE item DR, conformance paragraph).
 *
 * The first test pins the committed file to its generator, so a change to the sealing code that
 * changes a byte fails here instead of in a second product. The others replay the file the way a
 * second product does before its first sealed send: from the file alone, never from the generator.
 */
class E2eVectorsTest {

    private val text: String by lazy {
        requireNotNull(javaClass.classLoader?.getResource(RESOURCE)) {
            "missing test resource $RESOURCE - copy build/e2e-vectors/e2e-v1.json after a first run"
        }.readText()
    }
    private val root: JsonObject by lazy { JsonParser.parseString(text).asJsonObject }

    @Test
    fun `the committed vectors are exactly what the generator produces`() {
        val rendered = E2eVectorsGenerator.render(E2eVectorsGenerator.generate())
        File("build/e2e-vectors").mkdirs()
        File("build/e2e-vectors/e2e-v1.json").writeText(rendered)

        assertEquals(rendered, text.replace("\r\n", "\n"))
    }

    @Test
    fun `every device key and check code is reproduced from its private key`() {
        root.getAsJsonArray("devices").map { it.asJsonObject }.forEach { device ->
            val key = E2eDeviceKey.fromPrivate(fromHex(device.get("privateKey").asString))

            assertEquals(device.getAsJsonObject("e2eKey").get("kem").asString, key.kem)
            assertEquals(device.get("checkCode").asString, E2eDeviceKey.checkCode(key.publicKey))
        }
    }

    @Test
    fun `every box opens for each recipient to its payload`() {
        root.getAsJsonArray("boxes").map { it.asJsonObject }.forEach { case ->
            case.getAsJsonArray("to").map { it.asString }.forEach { reader ->
                val result = open(case.get("box"), case.get("context").asString, reader, "phone")

                assertTrue("${case.get("name")} for $reader", result is E2eOpenResult.Opened)
                assertEquals(case.getAsJsonObject("payload"), (result as E2eOpenResult.Opened).payload)
            }
        }
    }

    @Test
    fun `every failing case opens nothing for its stated reason`() {
        root.getAsJsonArray("failing").map { it.asJsonObject }.forEach { case ->
            val source = root.getAsJsonArray("boxes").map { it.asJsonObject }
                .first { it.get("name").asString == case.get("box").asString }
            val box = case.get("replacedBox") ?: source.get("box")
            val result = open(
                box,
                case.get("context").asString,
                case.get("openAs").asString,
                case.get("readerTrustsForPhone").asString,
            )

            assertEquals(case.get("name").asString, case.get("expected").asString, failureOf(result))
        }
    }

    @Test
    fun `every sealed clipboard value seals and opens to its published digests`() {
        root.getAsJsonArray("clipboard").map { it.asJsonObject }.forEach { case ->
            val opened = open(case.get("box"), E2eSealedBox.CLIPBOARD_CONTEXT, "tablet", "phone")
                as E2eOpenResult.Opened
            val valueKey = E2eSegmentStream.valueKey(opened.contentKey)
            val clear = E2eVectorsGenerator.clearPattern(case.get("clearSize").asInt)
            val sealed = ByteArrayOutputStream().also { E2eSegmentStream.seal(clear.inputStream(), it, valueKey) }
                .toByteArray()
            val reopened = ByteArrayOutputStream().also { E2eSegmentStream.open(sealed.inputStream(), it, valueKey) }

            assertEquals(case.get("valueKey").asString, hex(valueKey))
            assertEquals(case.get("sealedSize").asLong, sealed.size.toLong())
            assertEquals(case.get("sealedSha256").asString, hex(E2eBytes.sha256(sealed)))
            assertEquals(case.get("sealedSize").asLong, E2eSegmentStream.sealedSize(clear.size.toLong()))
            assertEquals(case.get("clearSha256").asString, hex(E2eBytes.sha256(reopened.toByteArray())))
        }
    }

    @Test
    fun `a truncated or reordered sealed value opens nothing`() {
        root.getAsJsonArray("clipboard").map { it.asJsonObject }.forEach { case ->
            val valueKey = fromHex(case.get("valueKey").asString)
            val clear = E2eVectorsGenerator.clearPattern(case.get("clearSize").asInt)
            val sealed = ByteArrayOutputStream().also { E2eSegmentStream.seal(clear.inputStream(), it, valueKey) }
                .toByteArray()
            val segment = E2eSegmentStream.SEGMENT_SIZE + E2eAead.TAG_LENGTH
            val segments = case.get("segments").asInt
            val truncated = sealed.copyOf((segments - 1) * segment)

            assertTrue(fails(truncated, valueKey))
            if (segments > 1) {
                val reordered = E2eBytes.concat(
                    sealed.copyOfRange(segment, minOf(2 * segment, sealed.size)),
                    sealed.copyOfRange(0, segment),
                    sealed.copyOfRange(minOf(2 * segment, sealed.size), sealed.size),
                )
                assertTrue(fails(reordered, valueKey))
            }
        }
    }

    @Test
    fun `the relay chunks open with the allowed group keys only`() {
        val relay = root.getAsJsonObject("relay")
        val broadcastId = relay.get("broadcastId").asString
        val groups = relay.getAsJsonObject("groupKeys")
        val guest = fromHex(groups.get("guest-1").asString)
        val own = fromHex(groups.get("own-1").asString)
        val link = fromHex(groups.get("link-1").asString)
        relay.getAsJsonArray("chunks").map { it.asJsonObject }
            .filter { it.get("flag").asInt == HEADER_FLAG }
            .forEach { chunk ->
                val records = requireNotNull(E2eRelayRecords.split(E2eBytes.fromB64url(chunk.get("payload").asString)))
                val ring = records.first().body
                val epoch = chunk.get("epoch").asLong

                assertEquals(E2eRelayRecords.TYPE_KEY_RING, records.first().type)
                assertTrue(E2eRelayRecords.openKeyRing(broadcastId, ring, link) != null)
                assertEquals(epoch == 1L, E2eRelayRecords.openKeyRing(broadcastId, ring, guest) != null)
                assertEquals(epoch == 1L, E2eRelayRecords.openKeyRing(broadcastId, ring, own) != null)
            }
    }

    @Test
    fun `every relay media record opens to its published clear bytes`() {
        val relay = root.getAsJsonObject("relay")
        val broadcastId = relay.get("broadcastId").asString
        val link = fromHex(relay.getAsJsonObject("groupKeys").get("link-1").asString)
        val keys = mutableMapOf<Long, ByteArray>()
        relay.getAsJsonArray("chunks").map { it.asJsonObject }.forEach { chunk ->
            val flag = chunk.get("flag").asInt
            val records = requireNotNull(E2eRelayRecords.split(E2eBytes.fromB64url(chunk.get("payload").asString)))
            val clear = records.mapNotNull { record ->
                when (record.type) {
                    E2eRelayRecords.TYPE_KEY_RING -> {
                        val ring = requireNotNull(E2eRelayRecords.openKeyRing(broadcastId, record.body, link))
                        keys[ring.epoch] = ring.epochKey
                        null
                    }
                    else -> {
                        val media = E2eRelayRecords.openMedia(broadcastId, record.body, flag) { keys[it] }
                        media as E2eRelayMedia.Clear
                    }
                }
            }

            assertEquals(chunk.getAsJsonArray("clearMedia").map { it.asString }, clear.map { hex(it.bytes) })
        }
    }

    @Test
    fun `a media record read under the wrong flag fails its tag`() {
        val relay = root.getAsJsonObject("relay")
        val broadcastId = relay.get("broadcastId").asString
        val chunk = relay.getAsJsonArray("chunks").map { it.asJsonObject }.first { it.get("flag").asInt == 1 }
        val epochKey = fromHex(relay.getAsJsonObject("epochKeys").get(chunk.get("epoch").asString).asString)
        val record = requireNotNull(E2eRelayRecords.split(E2eBytes.fromB64url(chunk.get("payload").asString))).first()

        assertEquals(E2eRelayMedia.Failed, E2eRelayRecords.openMedia(broadcastId, record.body, 0) { epochKey })
        assertArrayEquals(
            fromHex(chunk.getAsJsonArray("clearMedia")[0].asString),
            (E2eRelayRecords.openMedia(broadcastId, record.body, 1) { epochKey } as E2eRelayMedia.Clear).bytes,
        )
    }

    private fun device(name: String): JsonObject =
        root.getAsJsonArray("devices").map { it.asJsonObject }.first { it.get("name").asString == name }

    private fun open(box: com.google.gson.JsonElement, context: String, reader: String, trustedForPhone: String) =
        E2eSealedBox.open(
            box,
            context,
            E2eSelf(
                device(reader).get("deviceId").asString,
                E2eDeviceKey.fromPrivate(fromHex(device(reader).get("privateKey").asString)),
            ),
        ) { from, key ->
            val trusted = if (from == device("phone").get("deviceId").asString) trustedForPhone else null
            trusted != null && E2eDeviceKey.decodeKem(device(trusted).getAsJsonObject("e2eKey").get("kem").asString)
                ?.contentEquals(key) == true
        }

    private fun failureOf(result: E2eOpenResult): String = when (result) {
        is E2eOpenResult.Failed -> result.reason.name
        is E2eOpenResult.Opened -> "OPENED"
    }

    private fun fails(sealed: ByteArray, valueKey: ByteArray): Boolean =
        runCatching { E2eSegmentStream.open(sealed.inputStream(), ByteArrayOutputStream(), valueKey) }.isFailure

    private companion object {
        const val RESOURCE = "device-exchange/e2e-v1.json"
        const val HEADER_FLAG = 2
    }
}
