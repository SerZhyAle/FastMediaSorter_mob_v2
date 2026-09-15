package com.sza.fastmediasorter.domain.ocr

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fixtures are the geometry of lines measured in the 2026-09-13 dump (`docs/OCR_OVERLAY_ACCURACY.md` section 16),
 * not invented shapes: the two bracket edges decide the constant, so they are pinned exactly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OcrLineSplitterTest {

    @Test
    fun `two bubbles stitched across the figure are cut into their own sentences`() {
        val block = line(
            word("just", 181, 215), word("not", 222, 248), word("working", 255, 330), word("for", 337, 360),
            word("me.", 367, 400),
            word("Oh,", 1606, 1636), word("come", 1643, 1690), word("on,", 1697, 1717), word("Em.", 1724, 1750),
            word("It's", 1757, 1774),
        )

        val pieces = OcrLineSplitter.splitLine(block)

        assertEquals(listOf("just not working for me.", "Oh, come on, Em. It's"), pieces.map { it.text })
        assertEquals(Rect(181, 0, 400, HEIGHT), pieces[0].boundingBox)
        assertEquals(Rect(1606, 0, 1774, HEIGHT), pieces[1].boundingBox)
    }

    @Test
    fun `the widest honest gap of the dump is not cut`() {
        val block = line(
            word("CAEBY", 0, 30, height = 6),
            word("HAYES", 36, 60, height = 6),
            word("WESTERN.", 80, 130, height = 6)
        )

        assertSame(block, OcrLineSplitter.splitLine(block).single())
    }

    @Test
    fun `the narrowest stitch of the dump is cut`() {
        val block = line(
            word("THE", 389, 420, height = 9),
            word("ISLE", 426, 460, height = 9),
            word("IN", 493, 510, height = 9),
            word("THEIR", 516, 560, height = 9)
        )

        assertEquals(listOf("THE ISLE", "IN THEIR"), OcrLineSplitter.splitLine(block).map { it.text })
    }

    @Test
    fun `a right-to-left line is cut at its wide gap`() {
        val block = line(word("c", 900, 940), word("b", 850, 890), word("a", 100, 140))

        assertEquals(listOf("c b", "a"), OcrLineSplitter.splitLine(block).map { it.text })
    }

    @Test
    fun `overlapping boxes are never cut`() {
        val block = line(word("x", 0, 50), word("y", 40, 90))

        assertSame(block, OcrLineSplitter.splitLine(block).single())
    }

    @Test
    fun `a line without words is returned unchanged`() {
        val block = OcrTextBlock("text", Rect(0, 0, 100, HEIGHT), 90f, words = null)

        assertSame(block, OcrLineSplitter.splitLine(block).single())
    }

    @Test
    fun `each piece carries the mean confidence of its own words`() {
        val block = line(word("a", 0, 40, conf = 90f), word("b", 50, 90, conf = 70f), word("c", 400, 440, conf = 20f))

        val pieces = OcrLineSplitter.splitLine(block)

        assertEquals(80f, pieces[0].confidence, 0.001f)
        assertEquals(20f, pieces[1].confidence, 0.001f)
    }

    @Test
    fun `split keeps reading order across blocks`() {
        val stitched = line(word("a", 0, 40), word("b", 400, 440))
        val plain = line(word("c", 0, 40), word("d", 50, 90))

        assertEquals(listOf("a", "b", "c d"), OcrLineSplitter.split(listOf(stitched, plain)).map { it.text })
    }

    private fun word(text: String, left: Int, right: Int, height: Int = HEIGHT, conf: Float = 90f) =
        OcrWord(text, Rect(left, 0, right, height), conf)

    private fun line(vararg words: OcrWord): OcrTextBlock {
        val box = Rect(
            words.minOf { minOf(it.boundingBox.left, it.boundingBox.right) },
            0,
            words.maxOf { maxOf(it.boundingBox.left, it.boundingBox.right) },
            HEIGHT
        )
        return OcrTextBlock(words.joinToString(" ") { it.text }, box, 90f, words.toList())
    }

    private companion object {
        const val HEIGHT = 14
    }
}
