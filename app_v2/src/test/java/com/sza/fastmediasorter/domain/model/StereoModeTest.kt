package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for [StereoMode] enum.
 *
 * Tests:
 * - All expected values are present
 * - [StereoMode.fromKey] round-trips enum constants
 * - [StereoMode.fromKey] handles null and unknown keys gracefully
 * - Serialization is stable (key == name)
 */
class StereoModeTest {

    // ── Enum completeness ─────────────────────────────────────────────────

    @Test
    fun `all expected StereoMode values are declared`() {
        val names = StereoMode.entries.map { it.name }.toSet()
        listOf("AUTO", "SBS_FULL", "SBS_HALF", "OU", "MONO", "UNKNOWN").forEach { expected ->
            assert(expected in names) { "Missing StereoMode.$expected" }
        }
    }

    // ── fromKey round-trip ────────────────────────────────────────────────

    @Test
    fun `fromKey returns AUTO for key AUTO`() {
        assertEquals(StereoMode.AUTO, StereoMode.fromKey("AUTO"))
    }

    @Test
    fun `fromKey returns SBS_FULL for key SBS_FULL`() {
        assertEquals(StereoMode.SBS_FULL, StereoMode.fromKey("SBS_FULL"))
    }

    @Test
    fun `fromKey returns SBS_HALF for key SBS_HALF`() {
        assertEquals(StereoMode.SBS_HALF, StereoMode.fromKey("SBS_HALF"))
    }

    @Test
    fun `fromKey returns OU for key OU`() {
        assertEquals(StereoMode.OU, StereoMode.fromKey("OU"))
    }

    @Test
    fun `fromKey returns MONO for key MONO`() {
        assertEquals(StereoMode.MONO, StereoMode.fromKey("MONO"))
    }

    @Test
    fun `fromKey returns UNKNOWN for key UNKNOWN`() {
        assertEquals(StereoMode.UNKNOWN, StereoMode.fromKey("UNKNOWN"))
    }

    // ── fromKey null / unknown handling ────────────────────────────────────

    @Test
    fun `fromKey returns AUTO for null input (safe default for missing SharedPrefs entry)`() {
        assertEquals(StereoMode.AUTO, StereoMode.fromKey(null))
    }

    @Test
    fun `fromKey returns AUTO for empty string`() {
        assertEquals(StereoMode.AUTO, StereoMode.fromKey(""))
    }

    @Test
    fun `fromKey returns AUTO for completely unknown string`() {
        assertEquals(StereoMode.AUTO, StereoMode.fromKey("SOMETHING_UNKNOWN_9999"))
    }

    @Test
    fun `fromKey is case-sensitive — lowercase key does not match`() {
        // Enum names are uppercase; lowercase must not crash — fallback to AUTO
        assertEquals(StereoMode.AUTO, StereoMode.fromKey("auto"))
        assertEquals(StereoMode.AUTO, StereoMode.fromKey("sbs_full"))
    }

    // ── Serialization stability ────────────────────────────────────────────

    @Test
    fun `name property is stable key for SharedPreferences serialization`() {
        // Guarantees that persisted prefs using enum.name survive renames
        StereoMode.entries.forEach { mode ->
            assertNotNull("fromKey(${mode.name}) must not return null",
                StereoMode.fromKey(mode.name))
            assertEquals("fromKey(${mode.name}) must return same mode",
                mode, StereoMode.fromKey(mode.name))
        }
    }
}
