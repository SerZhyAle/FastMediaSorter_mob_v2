package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2462: proves the three version-skew outcomes on the phone side.
 *
 * Every fixture is hand-written JSON rather than a serialized [WearSettingsPayload]. That is the point:
 * this build's own payload class cannot express an omitted key or a key of the wrong type, so a fixture
 * built by serializing it could not reproduce the situation the decoder exists to survive.
 */
class WearSettingsPayloadDecoderTest {

    private val decoder = WearSettingsPayloadDecoder()

    @Test
    fun `full payload reports every key present and no divergence`() {
        val result = decoder.decode(fullPayloadJson())

        assertNotNull(result.payload)
        assertEquals(WearSettingsPayloadDecoder.CONTRACT_FIELDS, result.presentFields)
        assertTrue(result.divergences.toString(), result.divergences.isEmpty())
    }

    @Test
    fun `omitted key is reported missing and excluded from present fields`() {
        val result = decoder.decode(fullPayloadJson(omit = setOf("audioEnabled")))

        assertFalse(result.presentFields.contains("audioEnabled"))
        assertTrue(result.presentFields.contains("videoEnabled"))
        assertEquals(
            listOf(WearSettingsDivergence("audioEnabled", WearSettingsFieldIssue.MISSING)),
            result.divergences
        )
    }

    @Test
    fun `explicit json null counts as not sent rather than as a value`() {
        val result = decoder.decode(fullPayloadJson(nulled = setOf("downloadAlbumArt")))

        assertFalse(result.presentFields.contains("downloadAlbumArt"))
        assertEquals(
            listOf(WearSettingsDivergence("downloadAlbumArt", WearSettingsFieldIssue.MISSING)),
            result.divergences
        )
    }

    @Test
    fun `wrong typed key costs only itself and the rest still decodes`() {
        val result = decoder.decode(fullPayloadJson(overrides = mapOf("slideshowIntervalSeconds" to "\"30\"")))

        assertNotNull(result.payload)
        assertFalse(result.presentFields.contains("slideshowIntervalSeconds"))
        assertTrue(result.presentFields.contains("audioEnabled"))
        assertTrue(result.presentFields.contains("capabilities"))
        assertEquals(
            listOf(WearSettingsDivergence("slideshowIntervalSeconds", WearSettingsFieldIssue.WRONG_TYPE)),
            result.divergences
        )
    }

    @Test
    fun `key from a newer peer is reported unknown and the rest still decodes`() {
        val result = decoder.decode(fullPayloadJson(extra = mapOf("hapticsEnabled" to "true")))

        assertNotNull(result.payload)
        assertEquals(WearSettingsPayloadDecoder.CONTRACT_FIELDS, result.presentFields)
        assertEquals(
            listOf(WearSettingsDivergence("hapticsEnabled", WearSettingsFieldIssue.UNKNOWN_KEY)),
            result.divergences
        )
    }

    @Test
    fun `text that is not a json object yields nothing to apply`() {
        val result = decoder.decode("not json at all")

        assertNull(result.payload)
        assertTrue(result.presentFields.isEmpty())
        assertEquals(WearSettingsPayloadDecoder.ROOT, result.divergences.single().field)
    }

    /**
     * Builds the wire form of a complete payload, with hooks to omit a key, null it, retype it or add
     * one this build does not know - the four shapes a differently versioned peer can produce.
     */
    private fun fullPayloadJson(
        omit: Set<String> = emptySet(),
        nulled: Set<String> = emptySet(),
        overrides: Map<String, String> = emptyMap(),
        extra: Map<String, String> = emptyMap()
    ): String {
        val base = linkedMapOf(
            "audioEnabled" to "true",
            "videoEnabled" to "true",
            "imagesEnabled" to "true",
            "slideshowEnabled" to "true",
            "slideshowIntervalSeconds" to "30",
            "downloadAlbumArt" to "true",
            "viewMode" to "\"LIST\"",
            "keepScreenAwakeOutsidePlayers" to "true",
            "fileListViewMode" to "\"GRID\"",
            "appLanguage" to "\"en\"",
            "backgroundMode" to "\"BRANDED_ANIMATION\"",
            "streamsSectionEnabled" to "true",
            "documentsEnabled" to "true",
            "disableAnimations" to "false",
            "backgroundPlaybackEnabled" to "true",
            "appVersionName" to "\"2.60.8250.134\"",
            "fieldTimestamps" to "{\"audioEnabled\":1}",
            "capabilities" to "{\"autoRotationSensor\":true}"
        )
        base.putAll(overrides)
        nulled.forEach { base[it] = "null" }
        omit.forEach { base.remove(it) }
        base.putAll(extra)
        return base.entries.joinToString(prefix = "{", postfix = "}") { "\"${it.key}\":${it.value}" }
    }
}
