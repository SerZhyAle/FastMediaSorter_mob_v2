package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.fastmediasorter.domain.exchange.StandardSettingsSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Compares the phone with the vendored files of `vectors/settings-standard-v1/` (DEVICE-EXCHANGE 1.12 items EN to
 * ER) rather than with inline copies: the files are pretty-printed, so each is compared as parsed JSON, and the
 * bytes are held to the catalog's PROVENANCE so a hand-edited copy cannot pass.
 */
class ExchangeSettingsVectorFilesTest {

    @Test
    fun `every vendored vector keeps the SHA-256 the catalog adopted`() {
        val adopted = text("PROVENANCE.txt").lines()
            .mapNotNull { HASH_LINE.matchEntire(it.trim()) }
            .associate { it.groupValues[2] to it.groupValues[1] }
        assertEquals(VECTOR_FILES.toSet(), adopted.keys)
        adopted.forEach { (file, hash) -> assertEquals(file, hash, sha256(bytes(file))) }
    }

    @Test
    fun `get request encodes as the file of vector 03`() {
        val encoded = ExchangeSettingsEnvelopes.buildGetStandardEnvelope(VECTOR_APP_ID).encode()

        assertEquals(json(VECTOR_03), JsonParser.parseString(encoded))
    }

    @Test
    fun `payload response file of vector 04 parses the stored settings`() {
        val file = json(VECTOR_04).asJsonObject
        val parsed = ExchangeSettingsEnvelopes.parseStandardPayload(DeviceExchangeEnvelope.decode(file.toString()))

        assertEquals(
            ExchangeSettingsEnvelopes.SettingsStandardPayload(
                appId = file.get("appId").asString,
                formatVersion = file.get("formatVersion").asInt,
                settingsPayload = file.get("settingsPayload").asString,
                updatedAt = file.get("updatedAt").asString,
            ),
            parsed,
        )
    }

    @Test
    fun `local exclusions and groups hold the manifest of vector 07`() {
        val manifest = json(VECTOR_07).asJsonObject
        val exclusions = strings(manifest, "mandatoryLocalExclusions")
        val groups = strings(manifest, "exchangeableSettingGroups")

        val missing = exclusions - StandardSettingsSerializer.MANDATORY_LOCAL_EXCLUSIONS
        assertTrue("exclusions not held: $missing", missing.isEmpty())
        exclusions.forEach { assertTrue(it, StandardSettingsSerializer.isMandatoryExcluded(it)) }
        assertEquals(groups, StandardSettingsSerializer.ALL_GROUPS)
    }

    private fun strings(json: JsonObject, key: String): Set<String> =
        json.getAsJsonArray(key).map(JsonElement::getAsString).toSet()

    private fun json(file: String): JsonElement = JsonParser.parseString(text(file))

    private fun text(file: String): String = String(bytes(file), Charsets.UTF_8)

    private fun bytes(file: String): ByteArray =
        requireNotNull(javaClass.classLoader?.getResource("$DIRECTORY/$file")) { "missing test resource $file" }
            .readBytes()

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val DIRECTORY = "device-exchange/settings-standard-v1"
        const val VECTOR_APP_ID = "fms_win"
        const val VECTOR_03 = "03-get-standard-request.json"
        const val VECTOR_04 = "04-get-standard-payload-response.json"
        const val VECTOR_07 = "07-local-exclusion-manifest.json"
        val HASH_LINE = Regex("([0-9a-f]{64})\\s+(\\S+\\.json)")
        val VECTOR_FILES = listOf(
            "01-save-standard-request.json",
            "02-save-standard-response.json",
            VECTOR_03,
            VECTOR_04,
            "05-refused-not-found.json",
            "06-refused-incompatible.json",
            VECTOR_07,
        )
    }
}
