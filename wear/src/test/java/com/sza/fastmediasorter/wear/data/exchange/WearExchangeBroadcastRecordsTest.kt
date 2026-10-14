package com.sza.fastmediasorter.wear.data.exchange

import com.sza.fastmediasorter.wear.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.wear.data.broadcast.BroadcastEndpointDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Contract 6.3 and 7.7 as the watch announces its broadcast. */
class WearExchangeBroadcastRecordsTest {

    private val lan = BroadcastDescriptorDto(
        url = LAN_URL,
        title = "Wrist",
        sourceId = "src",
        endpoints = listOf(BroadcastEndpointDto(url = LAN_URL, transport = "HTTP", audioCodec = "aac")),
        isLive = true,
    )
    private val publication = WearExchangeBroadcastPublication(
        broadcastId = "b1",
        deviceId = "dev",
        lan = lan,
        publicEndpoint = "ex.example:7443",
        certFingerprint = "SHA256:pin",
        startedAtMs = 0L,
    )

    @Test
    fun `the relay endpoint follows the LAN one while an upload runs`() {
        val endpoints = endpoints(publication, relayUploading = true)

        assertEquals(listOf("HTTP", "RELAY"), endpoints.map { it.transport })
        val relay = endpoints[1]
        assertEquals("https://ex.example:7443/v2/b/b1/stream", relay.url)
        assertEquals("SHA256:pin", relay.certFingerprint)
        assertEquals(2_000L, relay.targetLatencyMs)
        assertEquals("AUDIO_ONLY", relay.mode)
    }

    @Test
    fun `no upload, no relay endpoint`() {
        val endpoints = endpoints(publication, relayUploading = false)

        assertEquals(listOf("HTTP"), endpoints.map { it.transport })
    }

    @Test
    fun `the record carries the descriptor verbatim and UTC times`() {
        val record = WearExchangeBroadcastRecords.record(publication, relayUploading = false, updatedAtMs = 1_000L)

        assertEquals("b1", record.get("broadcastId").asString)
        assertEquals("dev", record.get("deviceId").asString)
        assertEquals("Wrist", record.get("title").asString)
        assertEquals("AUDIO_ONLY", record.get("mode").asString)
        assertEquals("1970-01-01T00:00:00Z", record.get("startedAt").asString)
        assertEquals("1970-01-01T00:00:01Z", record.get("updatedAt").asString)
        val descriptor = record.getAsJsonObject("descriptor")
        assertEquals(LAN_URL, descriptor.get("url").asString)
        assertEquals("src", descriptor.get("sourceId").asString)
    }

    @Test
    fun `a platform-trusted server carries no fingerprint`() {
        val trusted = publication.copy(certFingerprint = null)

        val relay = endpoints(trusted, relayUploading = true).last()

        assertNull(relay.certFingerprint)
        val json = WearExchangeBroadcastRecords.record(trusted, relayUploading = true, updatedAtMs = 0L).toString()
        assertFalse(json.contains("certFingerprint"))
    }

    @Test
    fun `a broadcast id is in the share-id form`() {
        assertEquals(22, WearExchangeBroadcastRecords.newBroadcastId().length)
    }

    private fun endpoints(publication: WearExchangeBroadcastPublication, relayUploading: Boolean) =
        checkNotNull(WearExchangeBroadcastRecords.descriptor(publication, relayUploading).endpoints)

    private companion object {
        const val LAN_URL = "http://192.168.1.50:8080/listen"
    }
}
