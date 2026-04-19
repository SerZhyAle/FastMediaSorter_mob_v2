package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [StereoMode] enum.
 *
 * Tests:
 * - All expected values are present (flat + spherical)
 * - [StereoMode.fromKey] round-trips enum constants
 * - [StereoMode.fromKey] handles null and unknown keys gracefully
 * - Serialization is stable (key == name)
 * - [StereoMode.isSpherical], [StereoMode.isStereoscopic], [StereoMode.is180Only] predicates
 */
class StereoModeTest {

    // ── Enum completeness ─────────────────────────────────────────────────

    @Test
    fun `all expected flat StereoMode values are declared`() {
        val names = StereoMode.entries.map { it.name }.toSet()
        listOf("AUTO", "SBS_FULL", "SBS_HALF", "OU", "MONO", "UNKNOWN").forEach { expected ->
            assert(expected in names) { "Missing StereoMode.$expected" }
        }
    }

    @Test
    fun `all expected spherical StereoMode values are declared`() {
        val names = StereoMode.entries.map { it.name }.toSet()
        listOf(
            "EQUIRECT_360_MONO",
            "EQUIRECT_360_SBS",
            "EQUIRECT_360_OU",
            "EQUIRECT_180_MONO",
            "EQUIRECT_180_SBS",
            "VR180_FISHEYE_SBS",
            "CYLINDER_180"
        ).forEach { expected ->
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

    @Test
    fun `fromKey round-trips new spherical modes`() {
        listOf(
            StereoMode.EQUIRECT_360_MONO,
            StereoMode.EQUIRECT_360_SBS,
            StereoMode.EQUIRECT_360_OU,
            StereoMode.EQUIRECT_180_MONO,
            StereoMode.EQUIRECT_180_SBS,
            StereoMode.VR180_FISHEYE_SBS,
            StereoMode.CYLINDER_180
        ).forEach { mode ->
            assertEquals(mode, StereoMode.fromKey(mode.name))
        }
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

    // ── Predicate: isSpherical ─────────────────────────────────────────────

    @Test
    fun `isSpherical is true for all spherical modes`() {
        listOf(
            StereoMode.EQUIRECT_360_MONO,
            StereoMode.EQUIRECT_360_SBS,
            StereoMode.EQUIRECT_360_OU,
            StereoMode.EQUIRECT_180_MONO,
            StereoMode.EQUIRECT_180_SBS,
            StereoMode.VR180_FISHEYE_SBS,
            StereoMode.CYLINDER_180
        ).forEach { mode ->
            assertTrue("$mode must be spherical", mode.isSpherical())
        }
    }

    @Test
    fun `isSpherical is false for flat and sentinel modes`() {
        listOf(
            StereoMode.AUTO,
            StereoMode.SBS_FULL,
            StereoMode.SBS_HALF,
            StereoMode.OU,
            StereoMode.MONO,
            StereoMode.UNKNOWN
        ).forEach { mode ->
            assertFalse("$mode must NOT be spherical", mode.isSpherical())
        }
    }

    // ── Predicate: isStereoscopic ─────────────────────────────────────────

    @Test
    fun `isStereoscopic is true for flat SBS and OU`() {
        assertTrue(StereoMode.SBS_FULL.isStereoscopic())
        assertTrue(StereoMode.SBS_HALF.isStereoscopic())
        assertTrue(StereoMode.OU.isStereoscopic())
    }

    @Test
    fun `isStereoscopic is true for spherical SBS OU fisheye variants`() {
        assertTrue(StereoMode.EQUIRECT_360_SBS.isStereoscopic())
        assertTrue(StereoMode.EQUIRECT_360_OU.isStereoscopic())
        assertTrue(StereoMode.EQUIRECT_180_SBS.isStereoscopic())
        assertTrue(StereoMode.VR180_FISHEYE_SBS.isStereoscopic())
    }

    @Test
    fun `isStereoscopic is false for mono and non-content modes`() {
        assertFalse(StereoMode.MONO.isStereoscopic())
        assertFalse(StereoMode.EQUIRECT_360_MONO.isStereoscopic())
        assertFalse(StereoMode.EQUIRECT_180_MONO.isStereoscopic())
        assertFalse(StereoMode.CYLINDER_180.isStereoscopic())
        assertFalse(StereoMode.AUTO.isStereoscopic())
        assertFalse(StereoMode.UNKNOWN.isStereoscopic())
    }

    // ── Predicate: is180Only ──────────────────────────────────────────────

    @Test
    fun `is180Only is true for all 180-degree modes`() {
        assertTrue(StereoMode.EQUIRECT_180_MONO.is180Only())
        assertTrue(StereoMode.EQUIRECT_180_SBS.is180Only())
        assertTrue(StereoMode.VR180_FISHEYE_SBS.is180Only())
        assertTrue(StereoMode.CYLINDER_180.is180Only())
    }

    @Test
    fun `is180Only is false for 360-degree and flat modes`() {
        assertFalse(StereoMode.EQUIRECT_360_MONO.is180Only())
        assertFalse(StereoMode.EQUIRECT_360_SBS.is180Only())
        assertFalse(StereoMode.EQUIRECT_360_OU.is180Only())
        assertFalse(StereoMode.SBS_FULL.is180Only())
        assertFalse(StereoMode.MONO.is180Only())
        assertFalse(StereoMode.AUTO.is180Only())
        assertFalse(StereoMode.UNKNOWN.is180Only())
    }
}
