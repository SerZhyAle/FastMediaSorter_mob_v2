package com.sza.fastmediasorter.ui.streams

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S1154: the pure index->rect geometry of [ChannelPreviewAtlasSlicer] on the 240x135 tile / 34-column
 * contract. Geometry only - no bitmap decode here (Robolectric supplies a real [Rect]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelPreviewAtlasSlicerTest {

    private val slicer = ChannelPreviewAtlasSlicer({ null })

    @Test
    fun `rectFor matches the 240x135 34-col contract`() {
        assertEquals(Rect(0, 0, 240, 135), slicer.rectFor(0))
        assertEquals(Rect(240, 0, 480, 135), slicer.rectFor(1))
        // index COLS (34) wraps to the second row (34 % 34 = 0, 34 / 34 = 1, top = 135).
        assertEquals(Rect(0, 135, 240, 270), slicer.rectFor(ChannelPreviewAtlasSlicer.COLS))
        // one before the wrap is the last column of the first row.
        assertEquals(Rect(33 * 240, 0, 34 * 240, 135), slicer.rectFor(ChannelPreviewAtlasSlicer.COLS - 1))
    }

    @Test
    fun `rectFor column wraps at COLS`() {
        val cols = ChannelPreviewAtlasSlicer.COLS
        // index cols+2 -> row 1, col 2.
        val rect = slicer.rectFor(cols + 2)
        assertEquals(2 * 240, rect.left)
        assertEquals(135, rect.top)
    }

    @Test
    fun `isInBounds rejects negative and over-range indices on an 8192 sheet`() {
        val w = 8192
        val h = 8192
        assertFalse("negative index is out of bounds", slicer.isInBounds(-1, w, h))
        // Last full row of a 34-col / 8192-tall sheet: 8192 / 135 = 60 rows (0..59); row 60 overflows.
        val overflowIndex = 60 * ChannelPreviewAtlasSlicer.COLS
        assertFalse("a rect past the sheet bottom is out of bounds", slicer.isInBounds(overflowIndex, w, h))
        assertTrue("an in-range index fits", slicer.isInBounds(0, w, h))
        assertTrue("last column of a valid row fits", slicer.isInBounds(ChannelPreviewAtlasSlicer.COLS - 1, w, h))
    }
}
