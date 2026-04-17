package com.sza.fastmediasorter.ui.player

import android.os.Bundle
import androidx.media3.common.Format
import com.sza.fastmediasorter.domain.model.StereoMode
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [StereoDetector].
 *
 * Tests cover:
 * 1. Aspect-ratio heuristic — typical SBS/OU/mono dimensions
 * 2. Matroska metadata detection — tag values mapped to StereoMode
 * 3. Metadata priority over aspect ratio
 * 4. Edge cases — invalid dimensions, borderline AR values
 * 5. False-positive guard — common ultra-wide mono content
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StereoDetectorTest {

    private lateinit var detector: StereoDetector

    @Before
    fun setUp() {
        detector = StereoDetector()
    }

    // ── Aspect ratio heuristic — SBS detection ────────────────────────────

    @Test
    fun `detectFromDimensions returns SBS_FULL for 3840x1080`() {
        assertEquals(StereoMode.SBS_FULL, detector.detectFromDimensions(3840, 1080))
    }

    @Test
    fun `detectFromDimensions returns SBS_FULL for 1920x540`() {
        // 1080p SBS at half-height
        assertEquals(StereoMode.SBS_FULL, detector.detectFromDimensions(1920, 540))
    }

    @Test
    fun `detectFromDimensions returns SBS_FULL for 2560x720`() {
        assertEquals(StereoMode.SBS_FULL, detector.detectFromDimensions(2560, 720))
    }

    // ── Aspect ratio heuristic — mono detection ──────────────────────────

    @Test
    fun `detectFromDimensions returns MONO for 1920x1080`() {
        assertEquals(StereoMode.MONO, detector.detectFromDimensions(1920, 1080))
    }

    @Test
    fun `detectFromDimensions returns MONO for 1280x720`() {
        assertEquals(StereoMode.MONO, detector.detectFromDimensions(1280, 720))
    }

    @Test
    fun `detectFromDimensions returns MONO for 3840x2160 (4K)`() {
        assertEquals(StereoMode.MONO, detector.detectFromDimensions(3840, 2160))
    }

    @Test
    fun `detectFromDimensions returns MONO for ultrawide 4096x820`() {
        // AR ≈ 4.99 — above SBS_AR_MAX (3.8); must not be a false positive
        assertEquals(StereoMode.MONO, detector.detectFromDimensions(4096, 820))
    }

    @Test
    fun `detectFromDimensions returns MONO for narrow 1280x1280`() {
        // AR = 1.0 — square video, clearly mono
        assertEquals(StereoMode.MONO, detector.detectFromDimensions(1280, 1280))
    }

    // ── Aspect ratio heuristic — OU detection ────────────────────────────

    @Test
    fun `detectFromDimensions returns OU for 1080x1920 (portrait AR)`() {
        // AR ≈ 0.5625 — within OU_AR_MIN..OU_AR_MAX
        assertEquals(StereoMode.OU, detector.detectFromDimensions(1080, 1920))
    }

    @Test
    fun `detectFromDimensions returns OU for 1920x3200 (0_60 AR)`() {
        // AR = 0.60
        assertEquals(StereoMode.OU, detector.detectFromDimensions(1920, 3200))
    }

    // ── Aspect ratio edge cases ───────────────────────────────────────────

    @Test
    fun `detectFromDimensions returns UNKNOWN for zero dimensions`() {
        assertEquals(StereoMode.UNKNOWN, detector.detectFromDimensions(0, 1080))
        assertEquals(StereoMode.UNKNOWN, detector.detectFromDimensions(1920, 0))
        assertEquals(StereoMode.UNKNOWN, detector.detectFromDimensions(0, 0))
    }

    @Test
    fun `detectFromDimensions returns UNKNOWN for negative dimensions`() {
        assertEquals(StereoMode.UNKNOWN, detector.detectFromDimensions(-1920, 1080))
    }

    @Test
    fun `detectFromDimensions returns SBS_FULL at SBS_AR_MIN boundary 3_20`() {
        // AR exactly at lower boundary (3840 / 1200 ≈ 3.2)
        assertEquals(StereoMode.SBS_FULL, detector.detectFromDimensions(3840, 1200))
    }

    @Test
    fun `detectFromDimensions returns SBS_FULL at SBS_AR_MAX boundary 3_80`() {
        // AR exactly at upper boundary (3800 / 1000 = 3.8)
        assertEquals(StereoMode.SBS_FULL, detector.detectFromDimensions(3800, 1000))
    }

    // ── Matroska metadata — mono tag ─────────────────────────────────────

    @Test
    fun `detectFromFormat returns MONO when Matroska tag is 0 (mono)`() {
        val format = buildFormatWithTag("0", 3840, 1080) // AR would suggest SBS_FULL
        // Metadata must take priority
        assertEquals(StereoMode.MONO, detector.detectFromFormat(format))
    }

    // ── Matroska metadata — SBS tags ─────────────────────────────────────

    @Test
    fun `detectFromFormat returns SBS_FULL when Matroska tag is 1 (SBS left-first)`() {
        val format = buildFormatWithTag("1", 1920, 1080) // AR would suggest MONO
        assertEquals(StereoMode.SBS_FULL, detector.detectFromFormat(format))
    }

    @Test
    fun `detectFromFormat returns SBS_FULL when Matroska tag is 11 (SBS right-first)`() {
        val format = buildFormatWithTag("11", 1920, 1080)
        assertEquals(StereoMode.SBS_FULL, detector.detectFromFormat(format))
    }

    // ── Matroska metadata — OU tag ───────────────────────────────────────

    @Test
    fun `detectFromFormat returns OU when Matroska tag is 3 (OU top-first)`() {
        val format = buildFormatWithTag("3", 1920, 1080)
        assertEquals(StereoMode.OU, detector.detectFromFormat(format))
    }

    // ── Matroska metadata priority over AR heuristic ─────────────────────

    @Test
    fun `Matroska mono tag overrides SBS aspect ratio`() {
        // 3840x1080 would be SBS_FULL by AR, but metadata says MONO
        val format = buildFormatWithTag("0", 3840, 1080)
        assertEquals(StereoMode.MONO, detector.detectFromFormat(format))
    }

    @Test
    fun `Matroska SBS tag overrides mono aspect ratio`() {
        // 1920x1080 would be MONO by AR, but metadata says SBS
        val format = buildFormatWithTag("1", 1920, 1080)
        assertEquals(StereoMode.SBS_FULL, detector.detectFromFormat(format))
    }

    // ── Missing / unknown tag — fall back to AR ───────────────────────────

    @Test
    fun `detectFromFormat falls back to AR when tag absent`() {
        val format = buildFormatWithoutTag(3840, 1080)
        assertEquals(StereoMode.SBS_FULL, detector.detectFromFormat(format))
    }

    @Test
    fun `detectFromFormat returns UNKNOWN for unrecognised tag value`() {
        // Unknown tag value — both paths fail → UNKNOWN via AR fallback on MONO dims
        val format = buildFormatWithTag("99", 1920, 1080)
        // Unknown tag → UNKNOWN from metadata path → fall back to AR → MONO for 1920x1080
        assertEquals(StereoMode.MONO, detector.detectFromFormat(format))
    }

    // ── False-positive rate guard ─────────────────────────────────────────

    @Test
    fun `common cinema aspect ratios are not detected as 3D`() {
        // 2.39:1 (Scope) — AR ≈ 2.39, must not be SBS
        assertEquals(StereoMode.MONO, detector.detectFromDimensions(2560, 1072))
        // 2.35:1 (CinemaScope) — AR ≈ 2.35
        assertEquals(StereoMode.MONO, detector.detectFromDimensions(1920, 816))
        // 1.85:1 (Flat) — AR ≈ 1.85
        assertEquals(StereoMode.MONO, detector.detectFromDimensions(1920, 1038))
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Build a minimal [Format] with a [Bundle] carrying a fake Matroska stereo tag.
     * Media3 surfaces the tag as "stereo_mode" string in [Format.customData].
     */
    private fun buildFormatWithTag(tagValue: String, width: Int, height: Int): Format {
        val bundle = Bundle().apply { putString("stereo_mode", tagValue) }
        return Format.Builder()
            .setWidth(width)
            .setHeight(height)
            .setCustomData(bundle)
            .build()
    }

    /**
     * Build a [Format] without any custom data Bundle (simulates MP4/non-Matroska containers).
     */
    private fun buildFormatWithoutTag(width: Int, height: Int): Format {
        return Format.Builder()
            .setWidth(width)
            .setHeight(height)
            .build()
    }
}
