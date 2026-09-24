package com.sza.fastmediasorter.data.broadcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BroadcastDescriptorRoundTripTest {

    private val serializer = BroadcastDescriptorSerializer()
    private val parser = BroadcastDescriptorParser()

    @Test
    fun testPlainJsonRoundTrip() {
        val dto = BroadcastDescriptorDto(
            schemaVersion = 1,
            url = "http://192.168.1.100:8080/audio.mp3",
            title = "Test Phone Stream",
            mode = "AUDIO_ONLY"
        )
        val serialized = serializer.serialize(dto)
        val parsed = parser.parse(serialized)

        assertNotNull(parsed)
        assertEquals(dto, parsed)
    }

    @Test
    fun testCompressedBarcodeRoundTrip() {
        val dto = BroadcastDescriptorDto(
            schemaVersion = 1,
            url = "http://192.168.1.100:8080/audio.mp3",
            title = "Test Phone Stream Barcode",
            mode = "AUDIO_ONLY"
        )
        val compressed = serializer.serializeCompressed(dto)
        val parsed = parser.parse(compressed)

        assertNotNull(parsed)
        assertEquals(dto, parsed)
    }

    @Test
    fun testUnsupportedSchemaVersionReturnsNull() {
        val json = """{"schemaVersion":99,"url":"http://1.2.3.4","mode":"AUDIO_ONLY"}"""
        val parsed = parser.parse(json)
        assertNull(parsed)
    }

    /**
     * S2813: an old broadcaster names no source, and a new one names it at the same schema version.
     * Both have to parse, or the compatibility the optional field was chosen for does not exist.
     */
    @Test
    fun testDescriptorWithoutSourceIdStillParses() {
        val json = """{"schemaVersion":1,"url":"http://192.168.1.7:8768/a.aac","mode":"AUDIO_ONLY"}"""

        val parsed = parser.parse(json)

        assertNotNull(parsed)
        assertNull("an absent source id must stay absent rather than become empty", parsed?.sourceId)
    }

    @Test
    fun testDescriptorWithSourceIdSurvivesTheBarcodeForm() {
        val dto = BroadcastDescriptorDto(
            schemaVersion = 1,
            url = "http://192.168.1.7:41000/live-audio.aac",
            title = "Galaxy Watch",
            mode = "AUDIO_ONLY",
            sourceId = "6f1a8f0e-0f4e-4a2b-9d1c-2b7f1a8f0e00"
        )

        assertEquals(dto, parser.parse(serializer.serializeCompressed(dto)))
    }

    @Test
    fun testMalformedInputReturnsNull() {
        val parsed = parser.parse("not json or marker")
        assertNull(parsed)
    }

    @Test
    fun testDescriptorV2WithEndpointsAndLiveMarkers() {
        val endpoint1 = BroadcastEndpointDto(
            url = "http://192.168.1.97:8768/live-audio.aac",
            transport = "HTTP",
            mode = "AUDIO_ONLY",
            audioCodec = "AAC",
            sampleRate = 44100,
            bitrate = 128000,
            isLive = true,
            targetLatencyMs = 1000
        )
        val endpoint2 = BroadcastEndpointDto(
            url = "rtsp://192.168.1.97:8554/live",
            transport = "RTSP",
            mode = "VIDEO_AUDIO",
            videoCodec = "H264",
            audioCodec = "AAC",
            isLive = true,
            targetLatencyMs = 1000
        )
        val dto = BroadcastDescriptorDto(
            schemaVersion = 1,
            url = endpoint1.url,
            title = "Phone Multi-Track",
            mode = endpoint1.mode,
            sourceId = "test-device-id",
            endpoints = listOf(endpoint1, endpoint2),
            isLive = true,
            targetLatencyMs = 1000
        )

        val serialized = serializer.serialize(dto)
        val parsed = parser.parse(serialized)

        assertNotNull(parsed)
        assertEquals(dto, parsed)
        assertEquals(2, parsed?.getEffectiveEndpoints()?.size)
    }

    /**
     * S3057: the proposed second transport adds only `transport` values - the URL is the session
     * capability - so a descriptor carrying them has to survive both channels unchanged in shape.
     */
    @Test
    fun testDescriptorV2WithRelayAndP2pEndpoints() {
        val lan = BroadcastEndpointDto(url = LAN_URL, transport = "HTTP", isLive = true, targetLatencyMs = 1000)
        val p2p = BroadcastEndpointDto(url = P2P_URL, transport = "P2P", isLive = true, targetLatencyMs = 1000)
        val relay = BroadcastEndpointDto(url = RELAY_URL, transport = "RELAY", isLive = true, targetLatencyMs = 2000)
        val dto = BroadcastDescriptorDto(
            url = LAN_URL,
            mode = "AUDIO_ONLY",
            sourceId = "test-device-id",
            endpoints = listOf(lan, p2p, relay),
            isLive = true,
            targetLatencyMs = 1000
        )

        assertEquals(dto, parser.parse(serializer.serialize(dto)))
        val fromBarcode = parser.parse(serializer.serializeCompressed(dto))
        assertEquals(dto, fromBarcode)
        assertEquals(
            "the fallback order is the list order, so it must survive the barcode form",
            listOf("HTTP", "P2P", "RELAY"),
            fromBarcode?.getEffectiveEndpoints()?.map { it.transport }
        )
    }

    /** S3057: with no LAN address the relay URL is top-level, and a reader of `url` alone plays it as HTTP. */
    @Test
    fun testRelayOnlyDescriptorReadsAsHttpForLegacyReaders() {
        val dto = BroadcastDescriptorDto(url = RELAY_URL, mode = "AUDIO_ONLY")

        assertEquals("HTTP", dto.getEffectiveEndpoints().single().transport)
    }

    @Test
    fun testEffectiveEndpointsFallbackForLegacyDto() {
        val dto = BroadcastDescriptorDto(
            schemaVersion = 1,
            url = "http://192.168.1.10:8768/live",
            mode = "AUDIO_ONLY"
        )

        val effective = dto.getEffectiveEndpoints()
        assertEquals(1, effective.size)
        assertEquals("http://192.168.1.10:8768/live", effective[0].url)
        assertEquals("HTTP", effective[0].transport)
        assertEquals("AUDIO_ONLY", effective[0].mode)
    }

    private companion object {
        const val LAN_URL = "http://192.168.1.97:8768/live-audio.aac"
        const val P2P_URL = "wss://exchange.example/v1/signal/q3Zp0v8kR2mX7yT1bN5cWg"
        const val RELAY_URL = "https://exchange.example/v1/s/q3Zp0v8kR2mX7yT1bN5cWg/live-audio.aac"
    }
}
