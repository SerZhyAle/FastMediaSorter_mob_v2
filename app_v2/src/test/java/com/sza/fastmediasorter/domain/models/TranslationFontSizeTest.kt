package com.sza.fastmediasorter.domain.models

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [TranslationFontSize.fromMultiplier] and [TranslationFontFamily.fromTypefaceName].
 */
class TranslationFontSizeTest {

    @Test
    fun `fromMultiplier returns the closest size by multiplier distance`() {
        assertEquals(TranslationFontSize.MINIMUM, TranslationFontSize.fromMultiplier(0.7f))
        assertEquals(TranslationFontSize.SMALL, TranslationFontSize.fromMultiplier(0.8f))
        assertEquals(TranslationFontSize.LARGE, TranslationFontSize.fromMultiplier(1.5f))
        assertEquals(TranslationFontSize.HUGE, TranslationFontSize.fromMultiplier(2.5f))
    }

    @Test
    fun `fromMultiplier snaps an off-grid value to the nearest size`() {
        // 1.45 is closest to LARGE (1.5) rather than MEDIUM/AUTO (1.0).
        assertEquals(TranslationFontSize.LARGE, TranslationFontSize.fromMultiplier(1.45f))
        // 0.75 is equidistant-ish but closest to SMALL (0.8) over MINIMUM (0.7).
        assertEquals(TranslationFontSize.SMALL, TranslationFontSize.fromMultiplier(0.76f))
    }

    @Test
    fun `fromMultiplier resolves a one-point-zero multiplier to a unit-multiplier size`() {
        // AUTO and MEDIUM both have multiplier 1.0; minByOrNull keeps the first enum match (AUTO).
        assertEquals(TranslationFontSize.AUTO, TranslationFontSize.fromMultiplier(1.0f))
    }

    @Test
    fun `fromTypefaceName resolves known families`() {
        assertEquals(TranslationFontFamily.DEFAULT, TranslationFontFamily.fromTypefaceName("sans-serif"))
        assertEquals(TranslationFontFamily.SERIF, TranslationFontFamily.fromTypefaceName("serif"))
        assertEquals(TranslationFontFamily.MONOSPACE, TranslationFontFamily.fromTypefaceName("monospace"))
    }

    @Test
    fun `fromTypefaceName falls back to DEFAULT for an unknown name`() {
        assertEquals(TranslationFontFamily.DEFAULT, TranslationFontFamily.fromTypefaceName("comic-sans"))
        assertEquals(TranslationFontFamily.DEFAULT, TranslationFontFamily.fromTypefaceName(""))
    }
}
