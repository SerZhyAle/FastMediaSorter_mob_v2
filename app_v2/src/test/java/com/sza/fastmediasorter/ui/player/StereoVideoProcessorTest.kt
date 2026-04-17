package com.sza.fastmediasorter.ui.player

import com.sza.fastmediasorter.domain.model.StereoMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [StereoVideoProcessor].
 *
 * Tests cover:
 * 1. Default state after construction
 * 2. Mode transitions — valid resolved modes (MONO, SBS_FULL, SBS_HALF, OU)
 * 3. isStereoActive flag logic
 * 4. setStereoMode precondition — rejects AUTO and UNKNOWN
 * 5. No-op on same-mode set (idempotency)
 * 6. release() resets state to MONO
 * 7. GL effect contract — SBS/OU are preserved as pass-through, not cropped to one eye
 */
class StereoVideoProcessorTest {

    private lateinit var processor: StereoVideoProcessor

    @Before
    fun setUp() {
        processor = StereoVideoProcessor()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. Default state
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `initial mode is MONO`() {
        assertEquals(StereoMode.MONO, processor.getCurrentMode())
    }

    @Test
    fun `isStereoActive is false by default`() {
        assertFalse(processor.isStereoActive)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. Mode transitions
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `setStereoMode SBS_FULL sets mode correctly`() {
        processor.setStereoMode(StereoMode.SBS_FULL)
        assertEquals(StereoMode.SBS_FULL, processor.getCurrentMode())
    }

    @Test
    fun `setStereoMode SBS_HALF sets mode correctly`() {
        processor.setStereoMode(StereoMode.SBS_HALF)
        assertEquals(StereoMode.SBS_HALF, processor.getCurrentMode())
    }

    @Test
    fun `setStereoMode OU sets mode correctly`() {
        processor.setStereoMode(StereoMode.OU)
        assertEquals(StereoMode.OU, processor.getCurrentMode())
    }

    @Test
    fun `setStereoMode MONO sets mode correctly`() {
        processor.setStereoMode(StereoMode.SBS_FULL) // set to something else first
        processor.setStereoMode(StereoMode.MONO)
        assertEquals(StereoMode.MONO, processor.getCurrentMode())
    }

    @Test
    fun `can cycle through all resolved modes`() {
        val resolvedModes = listOf(StereoMode.MONO, StereoMode.SBS_FULL, StereoMode.SBS_HALF, StereoMode.OU)
        for (mode in resolvedModes) {
            processor.setStereoMode(mode)
            assertEquals(mode, processor.getCurrentMode())
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. isStereoActive
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `isStereoActive is true for SBS_FULL`() {
        processor.setStereoMode(StereoMode.SBS_FULL)
        assertTrue(processor.isStereoActive)
    }

    @Test
    fun `isStereoActive is true for SBS_HALF`() {
        processor.setStereoMode(StereoMode.SBS_HALF)
        assertTrue(processor.isStereoActive)
    }

    @Test
    fun `isStereoActive is false for OU`() {
        // OU is reserved for Phase 2; not yet an active stereo mode in GL pipeline
        processor.setStereoMode(StereoMode.OU)
        assertFalse(processor.isStereoActive)
    }

    @Test
    fun `isStereoActive is false for MONO`() {
        processor.setStereoMode(StereoMode.MONO)
        assertFalse(processor.isStereoActive)
    }

    @Test
    fun `isStereoActive transitions correctly from SBS to MONO`() {
        processor.setStereoMode(StereoMode.SBS_FULL)
        assertTrue(processor.isStereoActive)
        processor.setStereoMode(StereoMode.MONO)
        assertFalse(processor.isStereoActive)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. Precondition — rejects unresolved modes
    // ──────────────────────────────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `setStereoMode AUTO throws IllegalArgumentException`() {
        processor.setStereoMode(StereoMode.AUTO)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setStereoMode UNKNOWN throws IllegalArgumentException`() {
        processor.setStereoMode(StereoMode.UNKNOWN)
    }

    @Test
    fun `mode stays unchanged after rejected AUTO call`() {
        processor.setStereoMode(StereoMode.SBS_FULL)
        try {
            processor.setStereoMode(StereoMode.AUTO)
        } catch (_: IllegalArgumentException) {}
        // Mode must remain SBS_FULL — no partial state mutation
        assertEquals(StereoMode.SBS_FULL, processor.getCurrentMode())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 5. Idempotency — no-op on same mode
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `setting same mode twice is idempotent`() {
        processor.setStereoMode(StereoMode.SBS_FULL)
        processor.setStereoMode(StereoMode.SBS_FULL) // should not throw or corrupt state
        assertEquals(StereoMode.SBS_FULL, processor.getCurrentMode())
    }

    @Test
    fun `setting MONO twice is idempotent`() {
        processor.setStereoMode(StereoMode.MONO)
        processor.setStereoMode(StereoMode.MONO)
        assertEquals(StereoMode.MONO, processor.getCurrentMode())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 6. release()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `release resets mode to MONO`() {
        processor.setStereoMode(StereoMode.SBS_FULL)
        processor.release()
        assertEquals(StereoMode.MONO, processor.getCurrentMode())
    }

    @Test
    fun `release clears isStereoActive`() {
        processor.setStereoMode(StereoMode.SBS_HALF)
        assertTrue(processor.isStereoActive)
        processor.release()
        assertFalse(processor.isStereoActive)
    }

    @Test
    fun `processor is reusable after release`() {
        processor.release()
        processor.setStereoMode(StereoMode.SBS_FULL)
        assertEquals(StereoMode.SBS_FULL, processor.getCurrentMode())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7. GL effect contract
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `buildGlEffect returns null for SBS_FULL to preserve stereo frame`() {
        assertNull(processor.buildGlEffect(StereoMode.SBS_FULL))
    }

    @Test
    fun `buildGlEffect returns null for SBS_HALF to preserve stereo frame`() {
        assertNull(processor.buildGlEffect(StereoMode.SBS_HALF))
    }

    @Test
    fun `buildGlEffect returns null for OU until dedicated renderer exists`() {
        assertNull(processor.buildGlEffect(StereoMode.OU))
    }

    @Test
    fun `buildGlEffect returns null for MONO`() {
        assertNull(processor.buildGlEffect(StereoMode.MONO))
    }
}
