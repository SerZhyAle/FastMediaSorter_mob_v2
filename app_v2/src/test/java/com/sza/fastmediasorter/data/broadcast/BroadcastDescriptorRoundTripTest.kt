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
}
