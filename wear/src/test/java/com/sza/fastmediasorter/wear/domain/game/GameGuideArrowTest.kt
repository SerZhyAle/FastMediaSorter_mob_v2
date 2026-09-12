package com.sza.fastmediasorter.wear.domain.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S2494: the hint must point at the exit the player can reach in the fewest moves, and must stay
 * silent rather than crash on a board with no exit at all.
 */
class GameGuideArrowTest {

    @Test
    fun `nearest of two exits is chosen`() {
        val state = stateOf(
            "E...",
            ".P..",
            "....",
            "...E"
        )

        assertEquals(GamePosition(0, 0), GameGuideArrow.targetFor(state))
    }

    @Test
    fun `board without an exit yields no target`() {
        val state = stateOf(
            "....",
            ".P..",
            "....",
            "...."
        )

        assertNull(GameGuideArrow.targetFor(state))
    }

    private fun stateOf(vararg rows: String): GameLevelState = GameLevelState(
        board = GameBoard.fromRows(*rows),
        player = GameActor(GamePosition(1, 1)),
        enemies = listOf(
            GameEnemy(id = "k", type = GameEnemyType.KRYVAVITSA, position = GamePosition(3, 0))
        )
    )
}
