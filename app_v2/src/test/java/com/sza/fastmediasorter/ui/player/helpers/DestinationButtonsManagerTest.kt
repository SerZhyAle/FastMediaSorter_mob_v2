package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.ui.player.DestinationButtonsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests for the pure helper functions introduced in S0227:
 * [DestinationButtonsManager.computeMaxPerRow] and [DestinationButtonsManager.computeFontSizeSp].
 *
 * These functions contain all the business logic for adaptive layout - they are deliberately
 * extracted to companion object so they can be tested without Android framework dependencies.
 */
class DestinationButtonsManagerTest {

    // --- computeMaxPerRow ---

    @Test
    fun `computeMaxPerRow - 360dp screen - returns 4`() {
        // availableWidthDp = 360 - 4 (PANEL_PADDING_DP) = 356
        // maxPerRow = floor((356 - 44 - 4) / (58 + 4)) = floor(308 / 62) = 4
        val result = DestinationButtonsManager.computeMaxPerRow(356f)
        assertEquals("360 dp phone: expect 4 buttons max per row", 4, result)
    }

    @Test
    fun `computeMaxPerRow - 412dp screen - returns 5`() {
        // availableWidthDp = 412 - 4 = 408
        // maxPerRow = floor((408 - 44 - 4) / 62) = floor(360 / 62) = 5
        val result = DestinationButtonsManager.computeMaxPerRow(408f)
        assertEquals("412 dp phone: expect 5 buttons max per row", 5, result)
    }

    @Test
    fun `computeMaxPerRow - 600dp tablet - returns 8`() {
        // availableWidthDp = 600 - 4 = 596
        // maxPerRow = floor((596 - 44 - 4) / 62) = floor(548 / 62) = 8
        val result = DestinationButtonsManager.computeMaxPerRow(596f)
        assertEquals("600 dp tablet: expect 8 buttons max per row", 8, result)
    }

    @Test
    fun `computeMaxPerRow - 768dp tablet - returns 11`() {
        // availableWidthDp = 768 - 4 = 764
        // maxPerRow = floor((764 - 44 - 4) / 62) = floor(716 / 62) = 11
        val result = DestinationButtonsManager.computeMaxPerRow(764f)
        assertEquals("768 dp tablet: expect 11 buttons max per row", 11, result)
    }

    @Test
    fun `computeMaxPerRow - very narrow - returns 0 or positive int`() {
        val result = DestinationButtonsManager.computeMaxPerRow(40f)
        assertTrue("Very narrow screen should return non-negative value", result >= 0)
    }

    // --- computeButtonWidthDp ---

    @Test
    fun `computeButtonWidthDp - single row reserves custom path width`() {
        val result = DestinationButtonsManager.computeButtonWidthDp(
            availableWidthDp = 356f,
            buttonsInRow = 5,
            reserveCustomPathButtonWidth = true
        )
        assertEquals("Single row keeps room for '..' button", 57.6f, result, 0.1f)
    }

    @Test
    fun `computeButtonWidthDp - multi row uses densest destination row only`() {
        val result = DestinationButtonsManager.computeButtonWidthDp(
            availableWidthDp = 356f,
            buttonsInRow = 3,
            reserveCustomPathButtonWidth = false
        )
        assertEquals("Multi-row font cap uses the densest destination row", 114.67f, result, 0.1f)
    }

    // --- computeFontSizeSp ---

    @Test
    fun `computeFontSizeSp - min button width - returns SP_MIN`() {
        // buttonWidthDp == MIN_BUTTON_WIDTH_DP(58f) → t = 0 → SP_MIN(10f)
        val result = DestinationButtonsManager.computeFontSizeSp(58f)
        assertEquals("Min width → SP_MIN", 10f, result, 0.01f)
    }

    @Test
    fun `computeFontSizeSp - max width - returns SP_MAX`() {
        // buttonWidthDp == FONT_WIDTH_MAX_DP(200f) → t = 1 → SP_MAX(16f)
        val result = DestinationButtonsManager.computeFontSizeSp(200f)
        assertEquals("Max width → SP_MAX", 16f, result, 0.01f)
    }

    @Test
    fun `computeFontSizeSp - mid range - returns interpolated value`() {
        // buttonWidthDp = 106f → t = (106 - 58) / (200 - 58) = 48/142 ≈ 0.338 → 10 + 0.338*6 ≈ 12.03 sp
        val result = DestinationButtonsManager.computeFontSizeSp(106f)
        assertTrue("106 dp → ~12 sp (within [10,16])", result in 10f..16f)
        assertTrue("106 dp → approximately 12 sp", abs(result - 12.03f) < 0.5f)
    }

    @Test
    fun `computeFontSizeSp - below minimum - clamps to SP_MIN`() {
        val result = DestinationButtonsManager.computeFontSizeSp(10f)
        assertEquals("Below min → clamp to SP_MIN", 10f, result, 0.01f)
    }

    @Test
    fun `computeFontSizeSp - above maximum - clamps to SP_MAX`() {
        val result = DestinationButtonsManager.computeFontSizeSp(500f)
        assertEquals("Above max → clamp to SP_MAX", 16f, result, 0.01f)
    }

    // --- Distribution logic: adaptive single row vs lookup table ---

    @Test
    fun `distribution - 360dp + 5 buttons - single row via lookup`() {
        // maxPerRow = 4; count=5 > 4 → lookup table → listOf(5) (1 row)
        val availableWidthDp = 356f
        val maxPerRow = DestinationButtonsManager.computeMaxPerRow(availableWidthDp)
        val count = 5
        val usesAdaptive = count > 0 && count <= maxPerRow
        // Adaptive path NOT taken; lookup returns listOf(5) - 1 row, no regression
        assertEquals("360dp + 5 buttons: maxPerRow=4 < 5, adaptive NOT triggered", 4, maxPerRow)
        assertTrue("360dp + 5: lookup path taken (count > maxPerRow)", !usesAdaptive)
    }

    @Test
    fun `distribution - 360dp + 6 buttons - lookup gives 2 rows`() {
        val maxPerRow = DestinationButtonsManager.computeMaxPerRow(356f)
        val count = 6
        val usesAdaptive = count > 0 && count <= maxPerRow
        assertTrue("360dp + 6 buttons: should NOT use adaptive single row", !usesAdaptive)
        // lookup → listOf(3,3) - 2 rows, no regression
    }

    @Test
    fun `distribution - 600dp + 6 buttons - adaptive single row`() {
        val maxPerRow = DestinationButtonsManager.computeMaxPerRow(596f)
        val count = 6
        val usesAdaptive = count > 0 && count <= maxPerRow
        assertTrue("600dp + 6 buttons: should use adaptive single row", usesAdaptive)
        assertEquals("600dp maxPerRow = 8", 8, maxPerRow)
    }

    @Test
    fun `distribution - 768dp + 10 buttons - adaptive single row`() {
        val maxPerRow = DestinationButtonsManager.computeMaxPerRow(764f)
        val count = 10
        val usesAdaptive = count > 0 && count <= maxPerRow
        assertTrue("768dp + 10 buttons: should use adaptive single row", usesAdaptive)
        assertEquals("768dp maxPerRow = 11", 11, maxPerRow)
    }
}
