package com.sza.fastmediasorter.ui.game.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class GameScalingManagerTest {

    @Test
    fun `unchanged inputs return the same scale instance`() {
        val manager = GameScalingManager()

        val first = manager.compute(VIEW_WIDTH, VIEW_HEIGHT, BOARD_SIZE, BOARD_SIZE, largeBoard = true)
        val second = manager.compute(VIEW_WIDTH, VIEW_HEIGHT, BOARD_SIZE, BOARD_SIZE, largeBoard = true)

        assertSame(first, second)
    }

    @Test
    fun `zoom change yields a new scale with a larger cell`() {
        val manager = GameScalingManager()
        val before = manager.compute(VIEW_WIDTH, VIEW_HEIGHT, BOARD_SIZE, BOARD_SIZE, largeBoard = true)

        manager.zoomBy(ZOOM_FACTOR)
        val after = manager.compute(VIEW_WIDTH, VIEW_HEIGHT, BOARD_SIZE, BOARD_SIZE, largeBoard = true)

        assertNotSame(before, after)
        assertEquals(before.cellSize * ZOOM_FACTOR, after.cellSize, DELTA)
    }

    @Test
    fun `empty view returns the empty scale`() {
        val manager = GameScalingManager()

        assertSame(GameBoardScale.EMPTY, manager.compute(0, VIEW_HEIGHT, BOARD_SIZE, BOARD_SIZE, largeBoard = false))
    }

    private companion object {
        const val VIEW_WIDTH = 1000
        const val VIEW_HEIGHT = 800
        const val BOARD_SIZE = 10
        const val ZOOM_FACTOR = 2f
        const val DELTA = 0.001f
    }
}
