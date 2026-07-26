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
 * S1201: the pure index->rect geometry of [StreamLogoAtlasSlicer] on the 135x135 tile / 60-column
 * contract. Geometry only - no bitmap decode here (Robolectric supplies a real [Rect]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamLogoAtlasSlicerTest {

    private val slicer = StreamLogoAtlasSlicer { null }

    @Test
    fun `rectFor matches the 135x135 60-col contract`() {
        assertEquals(Rect(0, 0, 135, 135), slicer.rectFor(0))
        assertEquals(Rect(135, 0, 270, 135), slicer.rectFor(1))
        // index COLS (60) wraps to the second row (60 % 60 = 0, 60 / 60 = 1, top = 135).
        assertEquals(Rect(0, 135, 135, 270), slicer.rectFor(StreamLogoAtlasSlicer.COLS))
        // one before the wrap is the last column of the first row.
        assertEquals(Rect(59 * 135, 0, 60 * 135, 135), slicer.rectFor(StreamLogoAtlasSlicer.COLS - 1))
    }

    @Test
    fun `rectFor column wraps at COLS`() {
        val cols = StreamLogoAtlasSlicer.COLS
        // index cols+2 -> row 1, col 2.
        val rect = slicer.rectFor(cols + 2)
        assertEquals(2 * 135, rect.left)
        assertEquals(135, rect.top)
    }

    @Test
    fun `tile is square unlike the preview atlas`() {
        // The two atlases share a slicer shape but not a geometry; a copy-paste that dragged the
        // preview's 240x135 across would silently mis-crop every logo.
        assertEquals(StreamLogoAtlasSlicer.TILE_W, StreamLogoAtlasSlicer.TILE_H)
    }

    @Test
    fun `isInBounds rejects negative and over-range indices on the published sheet`() {
        // The published sheet is 8100x4185: 60 columns of 135 px, 31 rows.
        val w = 60 * 135
        val h = 31 * 135
        assertFalse("negative index is out of bounds", slicer.isInBounds(-1, w, h))
        val overflowIndex = 31 * StreamLogoAtlasSlicer.COLS
        assertFalse("a rect past the sheet bottom is out of bounds", slicer.isInBounds(overflowIndex, w, h))
        assertTrue("an in-range index fits", slicer.isInBounds(0, w, h))
        assertTrue("last tile of the last row fits", slicer.isInBounds(overflowIndex - 1, w, h))
    }
}
