package com.sza.fastmediasorter.wear.data.broadcast

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.GZIPInputStream

/**
 * S2509 Phase 01. Plain JVM, no Android: the descriptor is the only interoperability boundary between
 * two independently built modules, and a compiler cannot see across it - a renamed field or a dropped
 * prefix compiles perfectly on both sides and produces a broadcast no shipped listener can open.
 *
 * Assertions read the JSON through Gson rather than `org.json`, which exists on this classpath only as
 * the Android stub and throws "not mocked" in a plain unit test.
 *
 * The QR case decodes the payload rather than comparing it against a recorded string, so it fails on a
 * wire change and survives a harmless change in gzip's own output.
 */
class BroadcastDescriptorSerializerTest {

    private val serializer = BroadcastDescriptorSerializer()

    @Test
    fun jsonCarriesTheFourContractFields() {
        val json = parse(serializer.serialize(descriptor()))

        assertEquals(
            "The released phone parser refuses anything above 1, so the watch must claim exactly 1",
            1,
            json.get("schemaVersion").asInt
        )
        assertEquals(URL, json.get("url").asString)
        assertEquals(TITLE, json.get("title").asString)
        assertEquals(
            "The watch has no camera, so this is the only mode it may ever claim",
            "AUDIO_ONLY",
            json.get("mode").asString
        )
    }

    /** ADR-3: a source discriminator would raise the version and strand every shipped listener. */
    @Test
    fun jsonCarriesNoWatchSpecificField() {
        val json = parse(serializer.serialize(descriptor()))

        assertEquals("The descriptor grew a field the phone contract does not declare", 4, json.size())
        assertFalse("A source discriminator is exactly what ADR-3 forbids", json.has("source"))
    }

    @Test
    fun compressedFormRoundTripsThroughTheSharedWireShape() {
        val payload = serializer.serializeCompressed(descriptor())

        assertTrue(
            "The barcode form must carry the prefix the phone scanner recognises: '$payload'",
            payload.startsWith(BroadcastDescriptorSerializer.COMPRESSED_PREFIX)
        )
        val json = parse(inflate(payload.removePrefix(BroadcastDescriptorSerializer.COMPRESSED_PREFIX)))
        assertEquals(1, json.get("schemaVersion").asInt)
        assertEquals(URL, json.get("url").asString)
        assertEquals("AUDIO_ONLY", json.get("mode").asString)
    }

    /** The point of compressing at all: a watch-sized code has to stay scannable. */
    @Test
    fun compressedFormIsShorterThanTheJsonItCarries() {
        val plain = serializer.serialize(longTitleDescriptor())
        val compressed = serializer.serializeCompressed(longTitleDescriptor())

        assertTrue(
            "Compression did not pay for itself: ${compressed.length} against ${plain.length}",
            compressed.length < plain.length
        )
    }

    @Test
    fun v2DescriptorFieldsSerializeWithContractKeys() {
        val dto = BroadcastDescriptorDto(
            url = URL,
            title = TITLE,
            sourceId = "watch-123",
            isLive = true,
            targetLatencyMs = 1000L,
            endpoints = listOf(
                BroadcastEndpointDto(
                    url = URL,
                    transport = "HTTP",
                    mode = "AUDIO_ONLY",
                    sampleRate = 44100,
                    bitrate = 64000,
                    isLive = true,
                    targetLatencyMs = 1000L
                )
            )
        )
        val json = parse(serializer.serialize(dto))

        assertEquals("watch-123", json.get("sourceId").asString)
        assertTrue(json.get("isLive").asBoolean)
        assertEquals(1000L, json.get("targetLatencyMs").asLong)
        assertTrue(json.has("endpoints"))

        val endpoint = json.getAsJsonArray("endpoints").get(0).asJsonObject
        assertEquals(URL, endpoint.get("url").asString)
        assertEquals("HTTP", endpoint.get("transport").asString)
        assertEquals("AUDIO_ONLY", endpoint.get("mode").asString)
        assertEquals(44100, endpoint.get("sampleRate").asInt)
        assertEquals(64000, endpoint.get("bitrate").asInt)
    }

    @Test
    fun compressedPrefixMatchesPhoneBarcodePrefixConstant() {
        assertEquals("FMSBCAST1:", BroadcastDescriptorSerializer.COMPRESSED_PREFIX)
    }

    private fun descriptor() = BroadcastDescriptorDto(url = URL, title = TITLE)

    private fun longTitleDescriptor() =
        BroadcastDescriptorDto(url = URL, title = TITLE.repeat(LONG_TITLE_REPEATS))

    private fun parse(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    private fun inflate(base64Gzip: String): String {
        val compressed = Base64.getDecoder().decode(base64Gzip)
        return GZIPInputStream(ByteArrayInputStream(compressed)).use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
    }

    private companion object {
        const val URL = "http://192.168.1.42:41234/listen"
        const val TITLE = "Galaxy Watch"
        const val LONG_TITLE_REPEATS = 20
    }
}
