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
}
