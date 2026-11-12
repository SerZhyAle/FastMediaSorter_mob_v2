package com.sza.fastmediasorter.domain.model.ticket

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest

/**
 * S4554: replays the INBOX-PACKET v1 conformance vector `ticket-vector-s0045-a` (canon S0045 section 7)
 * byte for byte - the rung the format requires of a second writer before its first send.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TicketPacketCodecTest {

    private fun resource(name: String): ByteArray =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("$ROOT/$name")) { "missing $name" }
            .use { it.readBytes() }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `writer rebuilds the vector manifest byte for byte`() {
        val vector = JSONObject(resource("vector.json").decodeToString())
        val descriptors = vector.getJSONArray("files")
        val files = (0 until descriptors.length()).map { index ->
            val entry = descriptors.getJSONObject(index)
            val name = entry.getString("name")
            val bytes = resource("$FOLDER/$name")
            assertEquals(entry.getString("sha256"), sha256(bytes))
            TicketPacketFile(
                name = name,
                size = bytes.size.toLong(),
                sha256 = sha256(bytes),
                mimeType = entry.getString("mimeType"),
                role = TicketAttachmentRole.entries.first { it.wire == entry.getString("role") }
            )
        }
        val manifest = TicketPacketManifest(
            packetId = "ticket-vector-s0045-a",
            createdAtEpochMs = 1_794_389_400_000L,
            senderDeviceName = "Pixel 8",
            senderDeviceId = "dev-A_0123456789abcdef",
            targetDeviceName = "SZA-PC",
            files = files,
            project = "fms-a",
            text = "Conformance vector packet for the phone-ticket transport (S0045). Replay it; never file it. " +
                "If it arrives as a real ticket, it leaked out of tools/tests/data/inbox-ticket-vector."
        )

        val written = TicketPacketCodec.manifestToJson(manifest).toByteArray(Charsets.UTF_8)

        assertArrayEquals(resource("$FOLDER/manifest.json"), written)
        assertEquals(vector.getString("manifestSha256"), sha256(written))
        assertEquals(vector.getLong("totalSizeBytes"), manifest.totalSizeBytes)
    }

    @Test
    fun `text-only packet writes an empty files array and a null project`() {
        val manifest = TicketPacketManifest(
            packetId = "p1",
            createdAtEpochMs = 1L,
            senderDeviceName = "Phone",
            senderDeviceId = null,
            targetDeviceName = null,
            files = emptyList(),
            project = null,
            text = "a \"quoted\" path/with\\slash\n"
        )

        val json = TicketPacketCodec.manifestToJson(manifest)

        assert(json.contains("  \"fileNames\": [],\n"))
        assert(json.contains("  \"project\": null,\n"))
        assert(json.contains("\"text\": \"a \\\"quoted\\\" path/with\\\\slash\\n\"\n"))
        assertEquals("p1", JSONObject(json).getString("packetId"))
    }

    @Test
    fun `readers parse the PC sidecars and reject garbage`() {
        val projects = TicketPacketCodec.projectsFromJson(
            """{"schemaVersion":1,"projects":[{"key":"canon","name":"sza-unified-rules"},{"key":"Bad Key"}]}"""
        )
        assertEquals(listOf(TicketProject("canon", "sza-unified-rules")), projects)
        assertNull(TicketPacketCodec.projectsFromJson("not json"))

        val result = TicketPacketCodec.resultFromJson("""{"packetId":"p","tickets":["fms-a:S4600"],"verdict":"ok"}""")
        assertEquals(listOf("fms-a:S4600"), result?.tickets)

        val refusal = TicketPacketCodec.refusalFromJson("""{"error":"FileHashMismatch","detail":"text.md"}""")
        assertEquals("FileHashMismatch", refusal?.error)
        assertEquals("text.md", refusal?.detail)
    }

    private companion object {
        const val ROOT = "inbox-ticket-vector"
        const val FOLDER = "ticket-vector-s0045-a"
    }
}
