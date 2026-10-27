package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.sza.fastmediasorter.data.remote.sftp.anywhere.SftpRendezvousCodec
import com.sza.fastmediasorter.domain.model.SftpRendezvousBroadcast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import timber.log.Timber

/** S4441: the broadcasts half of the directory names a record by prefixes only and logs every dropped record. */
class ExchangeBroadcastDiagnosticsTest {

    private fun record(
        sealed: String? = null,
        descriptor: String = """{"mode":"VIDEO_ONLY","sourceId":"$DEVICE:screen","key":"secret-key"}""",
    ) = SftpRendezvousBroadcast(
        broadcastId = BROADCAST,
        deviceId = DEVICE,
        title = "Screen",
        mode = "VIDEO_ONLY",
        startedAtMs = null,
        updatedAtMs = 0L,
        descriptorJson = descriptor,
        writtenAtMs = 0L,
        ttlSeconds = 0L,
        sealed = sealed,
    )

    // A wire broadcast body with only the members given, so each test names the one it breaks.
    private fun wire(
        broadcastId: String? = BROADCAST,
        deviceId: String? = DEVICE,
        mode: String? = "VIDEO_ONLY",
        descriptor: JsonElement? = JsonObject().apply { addProperty("mode", "VIDEO_ONLY") },
    ) = JsonObject().apply {
        broadcastId?.let { addProperty("broadcastId", it) }
        deviceId?.let { addProperty("deviceId", it) }
        mode?.let { addProperty("mode", it) }
        descriptor?.let { add("descriptor", it) }
    }

    private fun capture(block: () -> Unit): List<String> {
        val lines = mutableListOf<String>()
        val tree = object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                lines += message
            }
        }
        Timber.plant(tree)
        try {
            block()
        } finally {
            Timber.uproot(tree)
        }
        return lines
    }

    @Test
    fun `a record is named by 8 character prefixes, the source suffix and the sealed flag`() {
        val line = ExchangeBroadcastDiagnostics.describe(record(sealed = "{}"))

        assertEquals("broadcast=0123abcd device=9f8e7d6c mode=VIDEO_ONLY source=screen sealed=yes", line)
    }

    @Test
    fun `a record without a source id or box reads dash and no`() {
        val line = ExchangeBroadcastDiagnostics.describe(record(descriptor = """{"mode":"VIDEO_ONLY"}"""))

        assertTrue(line, line.endsWith("source=- sealed=no"))
    }

    @Test
    fun `the line carries neither the full ids nor any descriptor member`() {
        val line = ExchangeBroadcastDiagnostics.describe(record(sealed = "{}"))

        assertTrue(line, !line.contains(BROADCAST) && !line.contains(DEVICE) && !line.contains("secret-key"))
    }

    @Test
    fun `a broadcast body that cannot be read is dropped with its reason and the others stay`() {
        val type = SftpRendezvousCodec.TYPE_BROADCAST

        val lines = capture {
            assertNotNull(ExchangeBroadcastDiagnostics.decode(type, wire()))
            assertNull(ExchangeBroadcastDiagnostics.decode(type, wire(mode = null)))
            assertNull(ExchangeBroadcastDiagnostics.decode(type, wire(descriptor = JsonPrimitive("{}"))))
            assertNull(ExchangeBroadcastDiagnostics.decode(type, JsonParser.parseString("\"text\"")))
        }

        assertEquals(3, lines.size)
        assertTrue(lines[0], lines[0].contains("no-mode broadcast=0123abcd device=9f8e7d6c"))
        assertTrue(lines[1], lines[1].contains("descriptor-not-an-object"))
        assertTrue(lines[2], lines[2].contains("not-an-object"))
    }

    @Test
    fun `the rejection reason agrees with what the codec decodes`() {
        val bodies = listOf(
            wire(),
            wire(broadcastId = null),
            wire(broadcastId = " "),
            wire(deviceId = null),
            wire(mode = ""),
            wire(descriptor = null),
        )

        bodies.forEach { candidate ->
            val dropped = SftpRendezvousCodec.decodeBody(SftpRendezvousCodec.TYPE_BROADCAST, candidate) == null
            val rejected = SftpRendezvousCodec.broadcastRejection(candidate) != null
            assertEquals(candidate.toString(), dropped, rejected)
        }
    }

    private companion object {
        const val BROADCAST = "0123abcd-ffff-4444-8888-aaaaaaaaaaaa"
        const val DEVICE = "9f8e7d6c-1111-2222-3333-bbbbbbbbbbbb"
    }
}
