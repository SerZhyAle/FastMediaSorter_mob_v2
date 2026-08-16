package com.sza.fastmediasorter.domain.ocr

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S1711: the type size and the tightened box are the only part of the translation overlay measurable
 * without a device (strategic ADR-1), so these cases are the ticket's whole regression net.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OcrLineGeometryTest {

    @Test
    fun `median over an odd number of words is the middle height`() {
        val block = line(word("one", height = 10), word("two", height = 30), word("six", height = 20))

        assertEquals(20, OcrLineGeometry.typeSizePx(block))
    }

    @Test
    fun `median over an even number of words is the lower middle height`() {
        val block = line(
            word("a", height = 10),
            word("b", height = 20),
            word("c", height = 30),
            word("d", height = 40)
        )

        assertEquals(20, OcrLineGeometry.typeSizePx(block))
    }

    @Test
    fun `a single word gives its own height`() {
        assertEquals(17, OcrLineGeometry.typeSizePx(line(word("solo", height = 17))))
    }

    @Test
    fun `no word level falls back to the box height`() {
        val block = OcrTextBlock("text", Rect(0, 0, 100, 42), 90f, words = null)

        assertEquals(42, OcrLineGeometry.typeSizePx(block))
        assertEquals(block.boundingBox, OcrLineGeometry.tightenedBounds(block))
    }

    @Test
    fun `an empty word list falls back to the box height`() {
        val block = OcrTextBlock("text", Rect(0, 0, 100, 42), 90f, words = emptyList())

        assertEquals(42, OcrLineGeometry.typeSizePx(block))
        assertEquals(block.boundingBox, OcrLineGeometry.tightenedBounds(block))
    }

    @Test
    fun `a tall punctuation-only word is dropped from the box`() {
        val block = line(
            word("Hello", left = 0, right = 50, height = 20),
            word("world", left = 60, right = 110, height = 20),
            word("|", left = 200, right = 206, height = 90)
        )

        assertEquals(Rect(0, 0, 110, 20), OcrLineGeometry.tightenedBounds(block))
    }

    @Test
    fun `a comma survives because it is not tall`() {
        val comma = word(",", left = 60, right = 66, height = 20)

        assertFalse(OcrLineGeometry.isArtifactWord(comma, medianHeightPx = 20, maxHeightRatio = 2.0f))
        val block = line(word("Hi", left = 0, right = 50, height = 20), comma)
        assertEquals(Rect(0, 0, 66, 20), OcrLineGeometry.tightenedBounds(block))
    }

    @Test
    fun `a tall real word survives because it carries letters`() {
        val capital = word("ROME", left = 0, right = 80, height = 90)

        assertFalse(OcrLineGeometry.isArtifactWord(capital, medianHeightPx = 20, maxHeightRatio = 2.0f))
    }

    @Test
    fun `a tall digit-only word survives because digits are text`() {
        val year = word("1984", left = 0, right = 80, height = 90)

        assertFalse(OcrLineGeometry.isArtifactWord(year, medianHeightPx = 20, maxHeightRatio = 2.0f))
    }

    @Test
    fun `a tall stroke is an artifact by both conditions`() {
        val stroke = word("|", left = 0, right = 6, height = 90)

        assertTrue(OcrLineGeometry.isArtifactWord(stroke, medianHeightPx = 20, maxHeightRatio = 2.0f))
    }

    @Test
    fun `a line where every word is dropped keeps its original box`() {
        val block = OcrTextBlock(
            text = "| |",
            boundingBox = Rect(0, 0, 300, 90),
            confidence = 40f,
            words = listOf(
                word("|", left = 0, right = 6, height = 90),
                word("|", left = 200, right = 206, height = 90)
            )
        )

        assertEquals(Rect(0, 0, 300, 90), OcrLineGeometry.tightenedBounds(block, maxHeightRatio = 0.5f))
    }

    private fun word(text: String, left: Int = 0, right: Int = 10, height: Int): OcrWord =
        OcrWord(text, Rect(left, 0, right, height), 90f)

    private fun line(vararg words: OcrWord): OcrTextBlock {
        val union = Rect(
            words.minOf { it.boundingBox.left },
            words.minOf { it.boundingBox.top },
            words.maxOf { it.boundingBox.right },
            words.maxOf { it.boundingBox.bottom }
        )
        return OcrTextBlock(
            text = words.joinToString(" ") { it.text },
            boundingBox = union,
            confidence = 90f,
            words = words.toList()
        )
    }
}
