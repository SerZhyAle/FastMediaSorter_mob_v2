package com.sza.fastmediasorter.data.broadcast

import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorParser.ParseOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.Base64
import java.util.zip.GZIPOutputStream

class BroadcastDescriptorParserHardeningTest {

    private val parser = BroadcastDescriptorParser()
    private val serializer = BroadcastDescriptorSerializer()

    private val dto = BroadcastDescriptorDto(
        schemaVersion = 1,
        url = "http://192.168.1.100:8080/audio.mp3",
        title = "Link import",
        mode = "AUDIO_ONLY"
    )

    @Test
    fun textAboveTheCeilingIsMalformed() {
        val padding = " ".repeat(BroadcastDescriptorParser.MAX_DESCRIPTOR_SIZE + 1)

        assertEquals(ParseOutcome.Malformed, parser.parseDetailed("{$padding}"))
    }

    @Test
    fun inflatedSizeAboveTheCeilingIsMalformed() {
        val bomb = ByteArrayOutputStream().use { sink ->
            GZIPOutputStream(sink).use { it.write(ByteArray(BroadcastDescriptorParser.MAX_DESCRIPTOR_SIZE + 1)) }
            sink.toByteArray()
        }
        val raw = BroadcastDescriptorParser.COMPRESSED_PREFIX + Base64.getEncoder().encodeToString(bomb)

        assertEquals(ParseOutcome.Malformed, parser.parseDetailed(raw))
    }

    @Test
    fun shareLinkWithPayloadParameterParses() {
        val encoded = URLEncoder.encode(serializer.serializeCompressed(dto), Charsets.UTF_8.name())

        val parsed = parser.parse("https://example.org/broadcast-import.html#payload=$encoded")

        assertNotNull(parsed)
        assertEquals(dto, parsed)
    }

    @Test
    fun chainOfNestedPayloadParametersIsMalformed() {
        val chain = "?payload=".repeat(NESTED_HOPS)

        assertEquals(ParseOutcome.Malformed, parser.parseDetailed(chain))
    }

    @Test
    fun higherSchemaVersionWithReshapedMemberStaysUnsupportedNotMalformed() {
        val json = """{"schemaVersion":2,"url":"http://h/a","mode":"AUDIO_ONLY","auth":"reshaped"}"""

        assertEquals(ParseOutcome.UnsupportedVersion, parser.parseDetailed(json))
    }

    @Test
    fun urlThatIsNotAStringIsMalformed() {
        val json = """{"schemaVersion":1,"url":123,"mode":"AUDIO_ONLY"}"""

        assertEquals(ParseOutcome.Malformed, parser.parseDetailed(json))
    }

    @Test
    fun authWithoutUserIsMalformed() {
        val json = """{"schemaVersion":1,"url":"http://h/a","mode":"AUDIO_ONLY","auth":{"scheme":"password-v1"}}"""

        assertEquals(ParseOutcome.Malformed, parser.parseDetailed(json))
    }

    @Test
    fun endpointEntryWithoutUrlDoesNotRejectTheDescriptor() {
        val json = """{"schemaVersion":1,"url":"http://h/a","mode":"AUDIO_ONLY","endpoints":[""" +
            """{"transport":"HTTP"},{"url":"http://h/b","transport":"HTTP","mode":"AUDIO_ONLY"}]}"""

        assertNotNull(parser.parse(json))
    }

    @Test
    fun documentNestedPastTheLimitIsMalformed() {
        val depth = "[".repeat(NESTING_PAST_LIMIT) + "]".repeat(NESTING_PAST_LIMIT)
        val json = """{"schemaVersion":1,"url":"http://h/a","mode":"AUDIO_ONLY","x":$depth}"""

        assertEquals(ParseOutcome.Malformed, parser.parseDetailed(json))
    }

    private companion object {
        const val NESTED_HOPS = 5000
        const val NESTING_PAST_LIMIT = 200
    }
}
