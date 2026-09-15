package com.sza.fastmediasorter.domain.ocr

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The gap instrument feeds the threshold bracket, so a sign or weighting error here would bracket the wrong
 * quantity without any visible symptom on the overlay.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OcrLineGapTest {

    @Test
    fun `left-to-right gap is the space between the boxes`() {
        assertEquals(15, OcrLineGap.gapPx(Rect(0, 0, 40, 10), Rect(55, 0, 90, 10)))
    }

    @Test
    fun `right-to-left order yields the same positive gap`() {
        assertEquals(15, OcrLineGap.gapPx(Rect(55, 0, 90, 10), Rect(0, 0, 40, 10)))
    }

    @Test
    fun `overlapping boxes yield a negative gap`() {
        assertTrue(OcrLineGap.gapPx(Rect(0, 0, 50, 10), Rect(40, 0, 90, 10)) < 0)
    }

    @Test
    fun `ratio is the widest gap over the median word height`() {
        val block = line(
            word(left = 0, right = 40, height = 20),
            word(left = 50, right = 90, height = 20),
            word(left = 170, right = 210, height = 20),
        )

        val measurement = OcrLineGap.measure(block)

        assertEquals(OcrLineGap.Measurement(maxGapPx = 80, medianWordHeightPx = 20, ratio = 4.0f), measurement)
    }

    @Test
    fun `a tall artifact does not change the weight`() {
        val block = line(
            word(left = 0, right = 40, height = 20),
            word(left = 60, right = 64, height = 90),
            word(left = 84, right = 120, height = 20),
        )

        assertEquals(20, OcrLineGap.measure(block)?.medianWordHeightPx)
    }

    @Test
    fun `a single word has no gap to measure`() {
        assertNull(OcrLineGap.measure(line(word(left = 0, right = 40, height = 20))))
    }

    @Test
    fun `no word level has no gap to measure`() {
        assertNull(OcrLineGap.measure(OcrTextBlock("text", Rect(0, 0, 100, 20), 90f, words = null)))
    }

    private fun word(left: Int, right: Int, height: Int) = OcrWord("w", Rect(left, 0, right, height), 90f)

    private fun line(vararg words: OcrWord): OcrTextBlock {
        val box = Rect(words.minOf { it.boundingBox.left }, 0, words.maxOf { it.boundingBox.right }, 20)
        return OcrTextBlock("line", box, 90f, words.toList())
    }
}
