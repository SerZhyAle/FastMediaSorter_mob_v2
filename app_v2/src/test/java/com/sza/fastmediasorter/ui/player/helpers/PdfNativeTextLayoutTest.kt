package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S1276: the point-to-character-range lookup that replaces the OCR pass on API 35+.
 *
 * The layout is constructed directly rather than through the `@RequiresApi(35)` factory - that one
 * takes a platform `PdfPageTextContent`, which Robolectric at sdk 34 does not have, and the factory
 * is straight arithmetic over the same data this suite already pins.
 *
 * The repeated-word case is the reason this class exists at all: the OCR mapper resolves a word by
 * `fullText.indexOf(word)` and cannot pass it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfNativeTextLayoutTest {

    private fun box(left: Float, top: Float, right: Float, bottom: Float) = RectF(left, top, right, bottom)

    @Test
    fun `point inside a box returns that item's range`() {
        val layout = PdfNativeTextLayout(
            text = "alpha beta gamma",
            items = listOf(
                PdfNativeTextLayout.Item(0..4, listOf(box(0f, 0f, 50f, 20f))),
                PdfNativeTextLayout.Item(6..9, listOf(box(60f, 0f, 100f, 20f))),
                PdfNativeTextLayout.Item(11..15, listOf(box(110f, 0f, 160f, 20f))),
            ),
        )

        assertEquals(6..9, layout.charRangeForPoint(80f, 10f))
    }

    @Test
    fun `point in the margin falls to the nearest item, not the first`() {
        val layout = PdfNativeTextLayout(
            text = "alpha beta",
            items = listOf(
                PdfNativeTextLayout.Item(0..4, listOf(box(0f, 0f, 50f, 20f))),
                PdfNativeTextLayout.Item(6..9, listOf(box(200f, 0f, 250f, 20f))),
            ),
        )

        assertEquals(6..9, layout.charRangeForPoint(240f, 60f))
    }

    @Test
    fun `pressing the third occurrence of a repeated word selects the third`() {
        // Three identical strings: an indexOf-based mapper returns 0..2 for all three presses.
        val layout = PdfNativeTextLayout(
            text = "the the the",
            items = listOf(
                PdfNativeTextLayout.Item(0..2, listOf(box(0f, 0f, 30f, 20f))),
                PdfNativeTextLayout.Item(4..6, listOf(box(40f, 0f, 70f, 20f))),
                PdfNativeTextLayout.Item(8..10, listOf(box(80f, 0f, 110f, 20f))),
            ),
        )

        assertEquals(8..10, layout.charRangeForPoint(95f, 10f))
    }

    @Test
    fun `an item spanning two lines matches from either of its boxes`() {
        val wrapped = PdfNativeTextLayout.Item(
            range = 0..20,
            boxes = listOf(box(0f, 0f, 200f, 20f), box(0f, 30f, 120f, 50f)),
        )
        val layout = PdfNativeTextLayout(text = "a sentence that wraps", items = listOf(wrapped))

        assertEquals(0..20, layout.charRangeForPoint(150f, 10f))
        assertEquals(0..20, layout.charRangeForPoint(60f, 40f))
    }

    @Test
    fun `an empty layout resolves nothing`() {
        val layout = PdfNativeTextLayout(text = "", items = emptyList())

        assertNull(layout.charRangeForPoint(10f, 10f))
    }

    // ── wordItems: the split the 2026-07-29 device run proved was missing ──────
    // getTextContents() handed back ONE item spanning a 1884-character page, so selecting whole items
    // selected whole pages. These pin the per-word cut instead.

    @Test
    fun `a one-box run splits into one item per word`() {
        val items = PdfNativeTextLayout.wordItems("alpha beta", listOf(box(0f, 0f, 100f, 20f)), offset = 0)

        assertEquals(2, items.size)
        assertEquals(0..4, items[0].range)
        assertEquals(6..9, items[1].range)
    }

    @Test
    fun `a whole-page run still resolves the pressed occurrence, not the first`() {
        // "the the the" in one item: the old whole-item behaviour returned 0..10 for every press.
        val items = PdfNativeTextLayout.wordItems("the the the", listOf(box(0f, 0f, 110f, 20f)), offset = 0)
        val layout = PdfNativeTextLayout(text = "the the the", items = items)

        assertEquals(8..10, layout.charRangeForPoint(95f, 10f))
        assertEquals(0..2, layout.charRangeForPoint(5f, 10f))
    }

    @Test
    fun `newlines matching the box count map each line onto its own box`() {
        val items = PdfNativeTextLayout.wordItems(
            text = "first line\nsecond line",
            boxes = listOf(box(0f, 0f, 100f, 20f), box(0f, 30f, 110f, 50f)),
            offset = 0,
        )
        val layout = PdfNativeTextLayout(text = "first line\nsecond line", items = items)

        // "second" is offsets 11..16 - only reachable if line two was paired with the lower box.
        assertEquals(11..16, layout.charRangeForPoint(20f, 40f))
        assertEquals(0..4, layout.charRangeForPoint(20f, 10f))
    }

    @Test
    fun `a run without newlines is apportioned across its boxes by width`() {
        // Two equal boxes, 22 characters: the cut lands near the middle and snaps to the space.
        val text = "alpha beta gamma delta"
        val items = PdfNativeTextLayout.wordItems(
            text = text,
            boxes = listOf(box(0f, 0f, 100f, 20f), box(0f, 30f, 100f, 50f)),
            offset = 0,
        )
        val layout = PdfNativeTextLayout(text = text, items = items)

        val pressedOnLowerBox = layout.charRangeForPoint(90f, 40f)
        assertEquals("delta", text.substring(pressedOnLowerBox!!.first, pressedOnLowerBox.last + 1))
        val pressedOnUpperBox = layout.charRangeForPoint(10f, 10f)
        assertEquals("alpha", text.substring(pressedOnUpperBox!!.first, pressedOnUpperBox.last + 1))
    }

    @Test
    fun `ranges carry the run's offset in the page text`() {
        val items = PdfNativeTextLayout.wordItems("beta", listOf(box(0f, 0f, 40f, 20f)), offset = 6)

        assertEquals(6..9, items.single().range)
    }

    @Test
    fun `a run with no words stays selectable as a whole`() {
        val boxes = listOf(box(0f, 0f, 40f, 20f))
        val items = PdfNativeTextLayout.wordItems("   ", boxes, offset = 4)

        assertEquals(4..6, items.single().range)
        assertEquals(boxes, items.single().boxes)
    }
}
