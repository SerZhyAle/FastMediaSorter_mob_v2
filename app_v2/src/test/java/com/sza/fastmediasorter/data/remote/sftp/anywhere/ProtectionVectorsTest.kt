package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeDirectory
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeEnvelope
import com.sza.fastmediasorter.domain.model.ExchangeListedResource
import com.sza.fastmediasorter.domain.model.ExchangeResourceAccess
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rung 1 vectors of contract DEVICE-EXCHANGE 17 item AL, `vectors/protection-v1.json` of the catalog. The test
 * resource is the catalog artifact byte for byte. A `legacy` consumer never learns a protected resource's
 * access; a `capable` one does, and this client takes the capable side.
 */
class ProtectionVectorsTest {

    private val steps: List<JsonObject> = checkNotNull(
        javaClass.classLoader?.getResourceAsStream("device-exchange/protection-v1.json")
    ) { "protection-v1.json test resource missing" }
        .use { JsonParser.parseString(it.readBytes().toString(Charsets.UTF_8)).asJsonObject }
        .getAsJsonArray("transcripts").first().asJsonObject
        .getAsJsonArray("steps").map(JsonElement::getAsJsonObject)

    private fun directories(stream: String): List<DeviceExchangeDirectory> = steps
        .filter { it.get("stream").asString == stream && it.get("direction").asString == "server->client" }
        .map { DeviceExchangeEnvelope.decode(it.get("json").asString) }
        .filter { it.type == DeviceExchangeEnvelope.TYPE_DIRECTORY }
        .map(DeviceExchangeDirectory::from)

    private fun listed(directory: DeviceExchangeDirectory): List<ExchangeListedResource> =
        directory.resources.map { ExchangeListedResource("server", it, true) }

    @Test
    fun `a consumer that declared pin-v1 is told the access and proves the root named in it`() {
        val protected = listed(directories("capable").first()).single()

        assertTrue(protected.record.pinProtected)
        assertEquals(SftpRendezvousResource.PROTECTION_PIN_V1, protected.record.protectionScheme)
        assertEquals(ExchangeResourceAccess.PIN_REQUIRED, protected.access)
        assertEquals("Private", protected.record.proofRoot)
    }

    @Test
    fun `a legacy consumer sees the lock and no access`() {
        val protected = listed(directories("legacy").first()).single()

        assertTrue(protected.record.pinProtected)
        assertNull(protected.record.descriptor)
        assertNull(protected.record.proofRoot)
        assertEquals(ExchangeResourceAccess.PIN_NO_ACCESS, protected.access)
    }

    @Test
    fun `unpublishing removes the record for both consumers`() {
        listOf("legacy", "capable").forEach { stream ->
            val frames = directories(stream)
            assertEquals(stream, 2, frames.size)
            assertTrue(stream, frames.last().resources.isEmpty())
            assertTrue(stream, frames.last().revision > frames.first().revision)
        }
    }

    @Test
    fun `the Drive wrapper of a protected record is schema 2 and decodes locked without access`() {
        val note = steps.mapNotNull { it.get("note")?.asString }
            .first { it.startsWith(WRAPPER_NOTE_PREFIX) }
            .removePrefix(WRAPPER_NOTE_PREFIX)

        assertTrue(note.contains("\"schemaVersion\":2"))
        val record = SftpRendezvousCodec.decode(note) as SftpRendezvousResource

        assertTrue(record.pinProtected)
        assertNull(record.descriptor)
        assertEquals(ExchangeResourceAccess.PIN_NO_ACCESS, ExchangeListedResource("drive", record, true).access)
    }

    private companion object {
        const val WRAPPER_NOTE_PREFIX = "Drive wrapper for a protected resource: "
    }
}
