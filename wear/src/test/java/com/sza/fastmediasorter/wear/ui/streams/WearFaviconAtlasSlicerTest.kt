package com.sza.fastmediasorter.wear.ui.streams

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearFaviconAtlasSlicerTest {

    private val slicer = WearFaviconAtlasSlicer { null }

    @Test
    fun `rectFor computes correct coordinates in 16-col grid`() {
        val rect0 = slicer.rectFor(0)
        assertEquals(0, rect0.left)
        assertEquals(0, rect0.top)
        assertEquals(32, rect0.right)
        assertEquals(32, rect0.bottom)

        val rect1 = slicer.rectFor(1)
        assertEquals(32, rect1.left)
        assertEquals(0, rect1.top)
        assertEquals(64, rect1.right)
        assertEquals(32, rect1.bottom)

        val rect16 = slicer.rectFor(16)
        assertEquals(0, rect16.left)
        assertEquals(32, rect16.top)
        assertEquals(32, rect16.right)
        assertEquals(64, rect16.bottom)
    }

    @Test
    fun `isInBounds rejects negative or oversized index`() {
        assertFalse(slicer.isInBounds(-1, 512, 512))
        assertTrue(slicer.isInBounds(0, 512, 512))
        // 512 width / 32 = 16 cols. 512 height / 32 = 16 rows. 16 * 16 = 256 tiles (0..255).
        assertTrue(slicer.isInBounds(255, 512, 512))
        assertFalse(slicer.isInBounds(256, 512, 512))
    }
}
