package com.sza.fastmediasorter.domain.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rung 1 of contract ANYWHERE-ACCESS: the descriptor v2 golden vectors, identical to
 * `anywhere-access/vectors/descriptor-v2.json` in the contracts catalog. Every valid code must decode
 * to its fields and re-encode byte for byte; a reader on the other side checks the same file.
 */
class SftpPairingPayloadVectorsTest {

    private val vectors: JsonObject = checkNotNull(
        javaClass.classLoader?.getResourceAsStream("anywhere/descriptor_v2_vectors.json")
    ) { "descriptor_v2_vectors.json test resource missing" }
        .use { JsonParser.parseString(it.readBytes().toString(Charsets.UTF_8)).asJsonObject }

    @Test
    fun `every valid vector decodes to its fields and re-encodes byte for byte`() {
        val valid = vectors.getAsJsonArray("valid").map(JsonElement::getAsJsonObject)
        assertTrue(valid.isNotEmpty())
        valid.forEach { vector ->
            val code = vector.get("code").asString
            val expected = SftpPairingPayload(
                hosts = vector.getAsJsonArray("hosts").map(JsonElement::getAsString),
                port = vector.get("port").asInt,
                username = vector.get("username").asString,
                password = vector.stringOrNull("password"),
                hostKeyFingerprint = vector.get("fingerprint").asString,
                exchangeEndpoint = vector.stringOrNull("exchange"),
                shareId = vector.stringOrNull("shareId"),
                driveChannel = vector.get("drive").asBoolean,
            )
            assertEquals(vector.get("name").asString, expected, SftpPairingPayload.decode(code))
            assertEquals(vector.get("name").asString, code, expected.encode())
        }
    }

    @Test
    fun `a v1 code ignores the v2 keys`() {
        vectors.getAsJsonArray("v1IgnoresV2Keys").map(JsonElement::getAsJsonObject).forEach { vector ->
            val decoded = checkNotNull(SftpPairingPayload.decode(vector.get("code").asString))
            assertEquals(false, decoded.isAnywhere)
            assertEquals(vector.get("reencoded").asString, decoded.encode())
        }
    }

    @Test
    fun `every refused vector decodes to nothing`() {
        vectors.getAsJsonArray("refused").map(JsonElement::getAsJsonObject).forEach { vector ->
            assertNull(vector.get("reason").asString, SftpPairingPayload.decode(vector.get("code").asString))
        }
    }

    private fun JsonObject.stringOrNull(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.asString
}
