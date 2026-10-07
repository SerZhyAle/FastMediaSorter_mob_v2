package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

/**
 * Rung 2 joint vectors of contract ANYWHERE-ACCESS section 6: the golden frames of the FMS_W
 * reference exchange server (`exchange-server/internal/wire/testdata/vectors.json` of that repository),
 * copied unchanged. The phone must produce and read exactly these bytes.
 */
class ExchangeWireVectorsTest {

    private val vectors = checkNotNull(
        javaClass.classLoader?.getResourceAsStream("anywhere/exchange_wire_vectors.json")
    ) { "exchange_wire_vectors.json test resource missing" }
        .use { JsonParser.parseString(it.readBytes().toString(Charsets.UTF_8)).asJsonArray }
        .map(JsonElement::getAsJsonObject)

    @Test
    fun `every body decodes and re-encodes byte for byte`() {
        assertTrue(vectors.size >= 11)
        vectors.forEach { vector ->
            val json = vector.get("json").asString
            val envelope = ExchangeEnvelope.decode(json)
            assertTrue(vector.get("name").asString, envelope.isKnown)
            assertEquals(vector.get("name").asString, json, envelope.encode())
        }
    }

    @Test
    fun `every frame matches the codec and reads back`() {
        vectors.forEach { vector ->
            val name = vector.get("name").asString
            val frame = vector.get("hex").asString.hexToBytes()
            val envelope = ExchangeEnvelope.decode(vector.get("json").asString)
            assertEquals(name, vector.get("hex").asString, ExchangeFrameCodec.frame(envelope).toHex())
            val input = ByteArrayInputStream(frame)
            assertEquals(name, envelope, ExchangeFrameCodec.read(input))
            assertNull(name, ExchangeFrameCodec.read(input))
        }
    }

    @Test
    fun `the register vector carries its members`() {
        val registerJson = vectors.first { it.get("name").asString == "register" }.get("json").asString
        val register = ExchangeEnvelope.decode(registerJson)
        assertEquals("AAAAAAAAAAAAAAAAAAAAAA", register.shareId)
        assertEquals(30, register.keepaliveSeconds)
        assertEquals(0, register.port)
        assertEquals("192.0.2.1:2222", register.claim?.getAsJsonArray("endpoints")?.get(0)?.asString)
        assertFalse(register.toString().contains("Passw0rdTest"))
    }

    @Test
    fun `an unknown type decodes and reports itself unknown`() {
        val envelope = ExchangeEnvelope.decode("{\"schemaVersion\":1,\"type\":\"ping\",\"extra\":true}")
        assertEquals("ping", envelope.type)
        assertFalse(envelope.isKnown)
    }

    @Test
    fun `stream-ending envelopes are refused`() {
        listOf(
            "[]",
            "not json",
            "{\"type\":\"keepalive\"}",
            "{\"schemaVersion\":2,\"type\":\"keepalive\"}",
            "{\"schemaVersion\":\"1\",\"type\":\"keepalive\"}",
            "{\"schemaVersion\":1}",
            "{\"schemaVersion\":1,\"type\":\"\"}",
        ).forEach { body ->
            assertThrows(body, ExchangeWireException::class.java) { ExchangeEnvelope.decode(body) }
        }
    }

    @Test
    fun `bad frames are refused`() {
        val zero = ByteBuffer.allocate(4).putInt(0).array()
        val oversized = ByteBuffer.allocate(4).putInt(ExchangeFrameCodec.MAX_FRAME_BODY + 1).array()
        val truncated = ByteBuffer.allocate(6).putInt(10).put(byteArrayOf(0x7b, 0x22)).array()
        val shortLength = byteArrayOf(0, 0)
        listOf(zero, oversized, truncated, shortLength).forEach { bytes ->
            assertThrows(ExchangeWireException::class.java) { ExchangeFrameCodec.read(ByteArrayInputStream(bytes)) }
        }
        val hugePassword = "x".repeat(ExchangeFrameCodec.MAX_FRAME_BODY)
        val huge = ExchangeEnvelope(ExchangeEnvelope.TYPE_REGISTER, password = hugePassword)
        assertThrows(ExchangeWireException::class.java) { ExchangeFrameCodec.frame(huge) }
    }

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
