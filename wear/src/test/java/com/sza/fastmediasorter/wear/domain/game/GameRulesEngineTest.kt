package com.sza.fastmediasorter.wear.domain.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1710: the watch copy of the rules is the first place they are pinned down - the phone's engine
 * carries no test, so a later divergence between the two copies has nothing else to fail against.
 */
class GameRulesEngineTest {

    private val engine = GameRulesEngine()

    @Test
    fun `move into an unpushable wall leaves the player in place`() {
        // Two walls in a row: pushing the first one has nowhere to put it.
        val state = stateOf(
            rows = arrayOf(
                ".##..",
                ".....",
                ".....",
                ".....",
                "....."
            ),
            player = GamePosition(0, 0),
            kryvavitsa = GamePosition(4, 4)
        )

        val result = engine.applyMove(state, GameDirection.RIGHT)

        assertEquals(GamePosition(0, 0), result.state.player.position)
        assertTrue(
            result.events.contains(
                GameEvent.PlayerBlocked(GameMoveRejectReason.WALL_BLOCKED_BY_WALL, GamePosition(0, 1))
            )
        )
        // Mirrors the phone: a bump is a spent turn, not a free probe of the board.
        assertEquals(1, result.state.stats.turns)
        assertEquals(0, result.state.stats.wallPushes)
    }

    @Test
    fun `wall push is accepted and pays its bonus back`() {
        val state = stateOf(
            rows = arrayOf(
                ".#...",
                ".....",
                ".....",
                ".....",
                "....."
            ),
            player = GamePosition(0, 0),
            kryvavitsa = GamePosition(4, 4),
            stats = GameStats(score = 100)
        )

        val pushed = engine.applyMove(state, GameDirection.RIGHT)

        assertTrue(pushed.accepted)
        assertEquals(GamePosition(0, 1), pushed.state.player.position)
        assertEquals(1, pushed.state.stats.wallPushes)
        assertTrue(pushed.events.contains(GameEvent.WallPushed(GamePosition(0, 1), GamePosition(0, 2))))
        // The push bonus exactly cancels the turn penalty; a plain step only pays the penalty.
        assertEquals(100, pushed.state.stats.score)
        assertEquals(90, engine.applyMove(state, GameDirection.DOWN).state.stats.score)
    }

    @Test
    fun `stepping next to the kryvavitsa ends the level`() {
        val state = stateOf(
            rows = arrayOf(
                ".....",
                ".....",
                ".....",
                ".....",
                "....."
            ),
            player = GamePosition(0, 0),
            kryvavitsa = GamePosition(2, 0)
        )

        val result = engine.applyMove(state, GameDirection.DOWN)

        assertEquals(GameStatus.GAME_OVER, result.state.status)
        assertTrue(result.events.any { it is GameEvent.PlayerCaptured })
        assertEquals(0, result.state.stats.survivalStreak)
    }

    @Test
    fun `reaching the exit completes the level with its bonus`() {
        val state = stateOf(
            rows = arrayOf(
                ".X...",
                ".....",
                ".....",
                ".....",
                "....."
            ),
            player = GamePosition(0, 0),
            kryvavitsa = GamePosition(4, 4)
        )

        val result = engine.applyMove(state, GameDirection.RIGHT)

        assertEquals(GameStatus.LEVEL_WON, result.state.status)
        assertTrue(result.events.contains(GameEvent.ExitReached))
        assertEquals(1, result.state.stats.levelsCompleted)
        // Turn penalty floors the score at zero, then the completion bonus lands on top.
        assertEquals(1000, result.state.stats.score)
    }

    @Test
    fun `a move after the level ended is rejected`() {
        val ended = stateOf(
            rows = arrayOf(
                ".....",
                ".....",
                ".....",
                ".....",
                "....."
            ),
            player = GamePosition(0, 0),
            kryvavitsa = GamePosition(4, 4)
        ).copy(status = GameStatus.GAME_OVER)

        val result = engine.applyMove(ended, GameDirection.DOWN)

        assertFalse(result.accepted)
        assertEquals(GameMoveRejectReason.NOT_PLAYING, result.rejectReason)
        assertEquals(GamePosition(0, 0), result.state.player.position)
        assertEquals(0, result.state.stats.turns)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `an invalid direction delta is rejected without touching the state`() {
        val state = stateOf(
            rows = arrayOf(
                ".....",
                ".....",
                ".....",
                ".....",
                "....."
            ),
            player = GamePosition(0, 0),
            kryvavitsa = GamePosition(4, 4)
        )

        val result = engine.applyMoveDelta(state, rowDelta = 1, colDelta = 1)

        assertFalse(result.accepted)
        assertEquals(GameMoveRejectReason.INVALID_DIRECTION, result.rejectReason)
        assertEquals(0, result.state.stats.turns)
    }

    @Test
    fun `moving into a VOID cell is rejected as OUT_OF_BOUNDS`() {
        val state = stateOf(
            rows = arrayOf(
                " ..",
                "...",
                "..."
            ),
            player = GamePosition(0, 1),
            kryvavitsa = GamePosition(2, 2)
        )

        val result = engine.applyMove(state, GameDirection.LEFT)

        assertEquals(GamePosition(0, 1), result.state.player.position)
        assertTrue(
            result.events.contains(
                GameEvent.PlayerBlocked(GameMoveRejectReason.OUT_OF_BOUNDS, GamePosition(0, 0))
            )
        )
    }

    @Test
    fun `pushing a wall into a VOID cell is rejected as WALL_AT_BOARD_EDGE`() {
        val state = stateOf(
            rows = arrayOf(
                " #.",
                ".#.",
                "..."
            ),
            player = GamePosition(1, 1),
            kryvavitsa = GamePosition(2, 2)
        )

        val result = engine.applyMove(state, GameDirection.UP)

        assertEquals(GamePosition(1, 1), result.state.player.position)
        assertTrue(
            result.events.contains(
                GameEvent.PlayerBlocked(GameMoveRejectReason.WALL_AT_BOARD_EDGE, GamePosition(0, 1))
            )
        )
    }

    private fun stateOf(
        rows: Array<String>,
        player: GamePosition,
        kryvavitsa: GamePosition,
        stats: GameStats = GameStats()
    ): GameLevelState = GameLevelState(
        board = GameBoard.fromRows(*rows),
        player = GameActor(player),
        enemies = listOf(GameEnemy("kryvavitsa", GameEnemyType.KRYVAVITSA, kryvavitsa)),
        stats = stats,
        config = GameLevelConfig(width = rows.first().length, height = rows.size, seed = 7L)
    )
}
