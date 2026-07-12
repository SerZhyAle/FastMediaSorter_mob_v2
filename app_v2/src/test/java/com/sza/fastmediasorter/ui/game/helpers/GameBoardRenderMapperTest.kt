package com.sza.fastmediasorter.ui.game.helpers

import com.sza.fastmediasorter.domain.game.GameActor
import com.sza.fastmediasorter.domain.game.GameBoard
import com.sza.fastmediasorter.domain.game.GameEnemy
import com.sza.fastmediasorter.domain.game.GameEnemyType
import com.sza.fastmediasorter.domain.game.GameLevelState
import com.sza.fastmediasorter.domain.game.GameMode
import com.sza.fastmediasorter.domain.game.GamePosition
import com.sza.fastmediasorter.ui.game.GameUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// S0993: guide-arrow target selection (nearest exit) is pure domain logic - no Android APIs, plain JVM test.
class GameBoardRenderMapperTest {

    private val mapper = GameBoardRenderMapper()

    private val labels = GameBoardAccessibilityLabels(
        floor = "floor",
        wall = "wall",
        exit = "exit",
        player = "player",
        kryvavitsa = "kryvavitsa",
        shadow = "shadow",
        summary = { _, _, _, _, _ -> "summary" }
    )

    private fun render(board: GameBoard, player: GamePosition, enemy: GamePosition): GameBoardRenderState {
        val state = GameLevelState(
            board = board,
            player = GameActor(player),
            enemies = listOf(GameEnemy("k", GameEnemyType.KRYVAVITSA, enemy))
        )
        return mapper.map(GameUiState.Ready(levelState = state, mode = GameMode.CONTRAST), labels)
    }

    @Test
    fun `guide arrow points at the nearest exit`() {
        // Exits at col 0 (distance 5) and col 8 (distance 3) from the player at col 5.
        val render = render(GameBoard.fromRows("E....P..E"), GamePosition(0, 5), GamePosition(0, 1))

        val arrow = requireNotNull(render.guideArrow)
        assertEquals(0, arrow.fromRow)
        assertEquals(5, arrow.fromColumn)
        assertEquals(0, arrow.toRow)
        assertEquals(8, arrow.toColumn)
    }

    @Test
    fun `guide arrow is null when the board has no exit`() {
        val render = render(GameBoard.fromRows("P...."), GamePosition(0, 0), GamePosition(0, 3))

        assertNull(render.guideArrow)
    }

    @Test
    fun `guide arrow degenerates to a point when the player stands on the exit`() {
        // Player already on the only exit - the view guards the zero-length draw, the mapper still returns a target.
        val render = render(GameBoard.fromRows("E...."), GamePosition(0, 0), GamePosition(0, 2))

        val arrow = requireNotNull(render.guideArrow)
        assertEquals(arrow.fromRow, arrow.toRow)
        assertEquals(arrow.fromColumn, arrow.toColumn)
    }
}
