package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.fastmediasorter.domain.model.SftpRendezvousBroadcast
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import com.sza.fastmediasorter.domain.model.SftpRendezvousPlayPair
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rung 1 golden vectors of contract DEVICE-EXCHANGE sections 6 and 8.1, `vectors/records-v1.json` of the
 * catalog, which this repository produces. The file under test resources is the catalog artifact byte for
 * byte; a change here is a change of the artifact and of the contract's document log.
 */
class DeviceExchangeRecordVectorsTest {

    private val vectors: JsonObject = checkNotNull(
        javaClass.classLoader?.getResourceAsStream("device-exchange/records-v1.json")
    ) { "records-v1.json test resource missing" }
        .use { JsonParser.parseString(it.readBytes().toString(Charsets.UTF_8)).asJsonObject }

    private fun list(key: String): List<JsonObject> = vectors.getAsJsonArray(key).map(JsonElement::getAsJsonObject)

    private fun JsonObject.text(key: String): String = get(key).asString

    private fun valid(name: String): String = list("valid").first { it.text("name") == name }.text("text")

    @Test
    fun `every valid text re-encodes byte for byte and names its Drive file`() {
        val valid = list("valid")
        assertTrue(valid.size >= 7)
        valid.forEach { vector ->
            val name = vector.text("name")
            val record = checkNotNull(SftpRendezvousCodec.decode(vector.text("text"))) { name }
            assertEquals(name, vector.text("text"), SftpRendezvousCodec.encode(record))
            assertEquals(
                name,
                vector.text("fileName"),
                SftpRendezvousCodec.fileName(vector.text("type"), vector.text("id")),
            )
        }
    }

    @Test
    fun `every tolerated text re-encodes to its canonical text`() {
        list("tolerated").forEach { vector ->
            val record = checkNotNull(SftpRendezvousCodec.decode(vector.text("text"))) { vector.text("name") }
            assertEquals(vector.text("name"), vector.text("canonical"), SftpRendezvousCodec.encode(record))
        }
    }

    @Test
    fun `every skipped text decodes to nothing`() {
        val skipped = list("skipped")
        assertTrue(skipped.size >= 10)
        skipped.forEach { vector -> assertNull(vector.text("name"), SftpRendezvousCodec.decode(vector.text("text"))) }
    }

    @Test
    fun `the full device carries its receiver pairs`() {
        val device = SftpRendezvousCodec.decode(valid("device-full")) as SftpRendezvousDevice
        val receiver = checkNotNull(device.receiver)
        assertEquals(listOf("AUDIO_ONLY", "VIDEO_AUDIO"), receiver.modes)
        assertEquals(listOf("HTTP", "RTSP", "RELAY", "TUNNEL"), receiver.transports)
        assertEquals(SftpRendezvousPlayPair("AUDIO_ONLY", "RELAY"), receiver.plays?.first())
        assertTrue(device.isOnline)
        val withoutPlays = SftpRendezvousCodec.decode(valid("device-receiver-without-plays")) as SftpRendezvousDevice
        assertNull(checkNotNull(withoutPlays.receiver).plays)
        assertNull((SftpRendezvousCodec.decode(valid("device-minimal")) as SftpRendezvousDevice).receiver)
    }

    @Test
    fun `the full resource carries its share id, root, public port and presence`() {
        val resource = SftpRendezvousCodec.decode(valid("resource-full")) as SftpRendezvousResource
        assertEquals("q3Vb7YtK0xP2mN9sLfR4wA", resource.shareId)
        assertEquals("/DCIM", resource.root)
        assertEquals(44_023, resource.publicPort)
        assertEquals(SftpRendezvousDevice.PRESENCE_ONLINE, resource.presence)
        assertFalse(resource.toString().contains("q3Vb7YtK0xP2mN9sLfR4wA"))
        val minimal = SftpRendezvousCodec.decode(valid("resource-minimal")) as SftpRendezvousResource
        assertNull(minimal.shareId)
        assertNull(minimal.presence)
    }

    @Test
    fun `a resource without access is listed with no descriptor`() {
        val text = list("tolerated").first { it.text("name") == "resource-without-access" }.text("text")
        val resource = SftpRendezvousCodec.decode(text) as SftpRendezvousResource
        assertNull(resource.descriptor)
        assertNull(resource.root)
        assertEquals("Pixel 8", resource.name)
    }

    @Test
    fun `the broadcast keeps its descriptor verbatim and out of logs`() {
        val broadcast = SftpRendezvousCodec.decode(valid("broadcast-audio-lan-relay-tunnel")) as SftpRendezvousBroadcast
        val descriptor = JsonParser.parseString(broadcast.descriptorJson).asJsonObject
        val transports = descriptor.getAsJsonArray("endpoints").map { it.asJsonObject.get("transport").asString }
        assertEquals(listOf("HTTP", "RELAY", "TUNNEL"), transports)
        assertEquals("AUDIO_ONLY", broadcast.mode)
        assertEquals(120L, broadcast.ttlSeconds)
        assertFalse(broadcast.toString().contains(broadcast.broadcastId))
    }
}
