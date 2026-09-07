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

    @Test
    fun testMalformedInputReturnsNull() {
        val parsed = parser.parse("not json or marker")
        assertNull(parsed)
    }
}
