package com.sza.fastmediasorter.ui.main.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class BoundedGridPopupManagerTest {

    @Test
    fun `tall portrait screen - bound is the space below the anchor`() {
        val height = BoundedGridPopupManager.resolveMaxHeight(
            screenHeight = 2340,
            anchorBottom = 360,
            topInset = 100,
            bottomInset = 120,
        )
        assertEquals(2340 - 360 - 120 - 16, height)
    }

    @Test
    fun `low anchor - bound falls back to the minimum`() {
        val height = BoundedGridPopupManager.resolveMaxHeight(
            screenHeight = 2340,
            anchorBottom = 2100,
            topInset = 100,
            bottomInset = 120,
        )
        assertEquals(300, height)
    }

    @Test
    fun `short landscape screen - minimum never exceeds the safe area`() {
        val height = BoundedGridPopupManager.resolveMaxHeight(
            screenHeight = 280,
            anchorBottom = 200,
            topInset = 20,
            bottomInset = 10,
        )
        assertEquals(280 - 20 - 10 - 16, height)
    }

    @Test
    fun `grid fits - width is columns times widest cell plus padding`() {
        assertEquals(2 * 400 + 32, BoundedGridPopupManager.resolveGridWidth(400, 2, 32, 1000))
    }

    @Test
    fun `grid too wide - width is capped at the safe width`() {
        assertEquals(1000, BoundedGridPopupManager.resolveGridWidth(600, 2, 32, 1000))
    }

    @Test
    fun `two columns - a cell may take half the safe width minus the padding`() {
        assertEquals((1000 - 32) / 2, BoundedGridPopupManager.resolveCellMaxWidth(1000, 2, 32))
    }

    @Test
    fun `one column - a cell may take the whole safe width minus the padding`() {
        assertEquals(1000 - 32, BoundedGridPopupManager.resolveCellMaxWidth(1000, 1, 32))
    }

    @Test
    fun `no columns - the share is the whole width rather than a division by zero`() {
        assertEquals(1000 - 32, BoundedGridPopupManager.resolveCellMaxWidth(1000, 0, 32))
    }

    @Test
    fun `cells reach past the safe width - the overflow is the excess including the end padding`() {
        assertEquals(754 - 704, BoundedGridPopupManager.resolveOverflow(746, 8, 704))
    }

    @Test
    fun `cells end exactly at the safe width - no overflow`() {
        assertEquals(0, BoundedGridPopupManager.resolveOverflow(696, 8, 704))
    }

    @Test
    fun `cells well inside the safe width - overflow never goes negative`() {
        assertEquals(0, BoundedGridPopupManager.resolveOverflow(300, 8, 704))
    }
}
