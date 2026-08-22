package com.sza.fastmediasorter.ui.browse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1968: the first automated coverage this thumbnail path has had.
 *
 * The case that produced the ticket is pinned by name: a source narrow enough that covering a 300 px
 * box upscales its height past two million pixels, which is ~2.4 GB at ARGB_8888 and cannot be
 * allocated. The sweep recorded that exact target 254 times.
 */
class ThumbnailDecodeBudgetTest {

    private val target = 300

    @Test
    fun `the 300 by 1970400 case from the sweep is refused`() {
        // Reconstructed from the logged target: covering 300 px of width upscaled the height to
        // 1970400, so the source is that shape scaled down - any source with this ratio reproduces it.
        assertTrue(ThumbnailDecodeBudget.exceedsBudget(sourceWidth = 1, sourceHeight = 6568, target = target))
    }

    @Test
    fun `an ordinary photo is allowed`() {
        assertFalse(ThumbnailDecodeBudget.exceedsBudget(sourceWidth = 4032, sourceHeight = 3024, target = target))
    }

    @Test
    fun `a tall screenshot is allowed - the budget is not an aspect rule`() {
        // Acceptance criterion 4: tall images well inside the budget still get thumbnails.
        assertFalse(ThumbnailDecodeBudget.exceedsBudget(sourceWidth = 1080, sourceHeight = 19200, target = target))
    }

    @Test
    fun `a square source covers exactly the target box`() {
        assertEquals(
            (target * target).toLong(),
            ThumbnailDecodeBudget.coverPixelsFor(sourceWidth = 1000, sourceHeight = 1000, target = target),
        )
    }

    @Test
    fun `cover scales by the larger factor, so the narrow side fills the box`() {
        // 100x200 covering 300: width factor 3.0 wins over height factor 1.5, so height goes to 600.
        assertEquals(
            300L * 600L,
            ThumbnailDecodeBudget.coverPixelsFor(sourceWidth = 100, sourceHeight = 200, target = target),
        )
    }

    @Test
    fun `an unmeasurable source is not refused`() {
        // A header that failed to parse reports 0 or -1. Nothing can be concluded from it, so the
        // ordinary decode path is still allowed to try rather than the file being rejected on
        // missing metadata.
        assertFalse(ThumbnailDecodeBudget.exceedsBudget(sourceWidth = 0, sourceHeight = 0, target = target))
        assertFalse(ThumbnailDecodeBudget.exceedsBudget(sourceWidth = -1, sourceHeight = 500, target = target))
        assertEquals(0L, ThumbnailDecodeBudget.coverPixelsFor(sourceWidth = 0, sourceHeight = 900, target = target))
    }

    @Test
    fun `the boundary itself is allowed and one pixel past it is not`() {
        val side = 4096 // 4096 x 4096 = exactly MAX_DECODE_PIXELS
        assertEquals(ThumbnailDecodeBudget.MAX_DECODE_PIXELS, side.toLong() * side.toLong())
        assertFalse(ThumbnailDecodeBudget.exceedsBudget(sourceWidth = side, sourceHeight = side, target = side))
        assertTrue(ThumbnailDecodeBudget.exceedsBudget(sourceWidth = side, sourceHeight = side + 1, target = side + 1))
    }
}
