package com.sza.fastmediasorter.wear.domain.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2158: the three engine entry points the watch UI reaches for the first time in this ticket.
 *
 * `applySkipTurn` and `restartLevelVoluntarily` were unreachable code until the in-play menu opened
 * a path to them, and `advanceLevel` was reached only by a tap on the outcome chip before the timed
 * advance existed - so the accounting the owner checks on the device had nothing failing against it.
 */
class GameRulesEngineAdvanceTest {

    private val engine = GameRulesEngine()

    @Test
    fun `advancing carries the accumulated stats into the next level`() {
        val completed = stateOf(
            player = GamePosition(0, 0),
            kryvavitsa = GamePosition(4, 4),
            stats = GameStats(
                turns = 12,
                wallPushes = 3,
                levelsCompleted = 1,
                survivalStreak = 1,
                score = 2400,
                highScore = 2400
            )
        ).copy(status = GameStatus.LEVEL_WON)
        val nextLevel = stateOf(player = GamePosition(1, 1), kryvavitsa = GamePosition(3, 3))

        val advanced = engine.advanceLevel(completed, nextLevel)

        assertEquals(GameStatus.PLAYING, advanced.status)
        assertEquals(completed.stats, advanced.stats)
        // The board is the next level's, the score is the run's - that is what makes it one game.
        assertEquals(nextLevel.player.position, advanced.player.position)
    }

    @Test
    fun `restarting a lost level charges the restart penalty and clears the streak`() {
        val lost = stateOf(
            player = GamePosition(0, 0),
            kryvavitsa = GamePosition(4, 4),
            stats = GameStats(turns = 9, survivalStreak = 4, score = 1800, highScore = 1800)
        ).copy(status = GameStatus.GAME_OVER)
        val fresh = stateOf(player = GamePosition(2, 2), kryvavitsa = GamePosition(4, 0))

        val restarted = engine.restartLevel(lost, fresh)

        assertEquals(GameStatus.PLAYING, restarted.status)
        assertEquals(RESTART_PENALTY_APPLIED, restarted.stats.score)
        assertEquals(0, restarted.stats.survivalStreak)
        // The penalty never rewrites the best result already reached.
        assertEquals(1800, restarted.stats.highScore)
    }

    @Test
    fun `restarting a level in progress is priced exactly as a lost one`() {
        val playing = stateOf(
            player = GamePosition(0, 0),
            kryvavitsa = GamePosition(4, 4),
            stats = GameStats(turns = 5, survivalStreak = 2, score = 1800, highScore = 1800)
        )
        val fresh = stateOf(player = GamePosition(2, 2), kryvavitsa = GamePosition(4, 0))

        val restarted = engine.restartLevelVoluntarily(playing, fresh)

        assertEquals(GameStatus.PLAYING, restarted.status)
        assertEquals(RESTART_PENALTY_APPLIED, restarted.stats.score)
        assertEquals(0, restarted.stats.survivalStreak)
    }

    @Test
    fun `a skipped turn is spent and priced as an ordinary turn`() {
        val state = stateOf(
            player = GamePosition(0, 0),
            // Far enough that the enemy's answering step cannot reach the player and end the level.
            kryvavitsa = GamePosition(4, 4),
            stats = GameStats(turns = 3, score = 1000, highScore = 1000)
        )

        val result = engine.applySkipTurn(state)

        assertTrue(result.accepted)
        assertEquals(4, result.state.stats.turns)
        assertEquals(TURN_PENALTY_APPLIED, result.state.stats.score)
        // Skipping buys time, not distance: the player has not moved.
        assertEquals(GamePosition(0, 0), result.state.player.position)
        assertEquals(0, result.state.stats.wallPushes)
    }

    @Test
    fun `a skipped turn is refused once the level is over`() {
        val over = stateOf(player = GamePosition(0, 0), kryvavitsa = GamePosition(4, 4))
            .copy(status = GameStatus.GAME_OVER)

        val result = engine.applySkipTurn(over)

        assertEquals(false, result.accepted)
        assertEquals(GameMoveRejectReason.NOT_PLAYING, result.rejectReason)
        assertEquals(over.stats, result.state.stats)
    }

    private fun stateOf(
        player: GamePosition,
        kryvavitsa: GamePosition,
        stats: GameStats = GameStats()
    ): GameLevelState = GameLevelState(
        // The exit sits away from both actors: an exit under the enemy would make the fixture's
        // geometry, not the rule under test, decide how a turn ends.
        board = GameBoard.fromRows(
            "....X",
            ".....",
            ".....",
            ".....",
            "....."
        ),
        player = GameActor(player),
        enemies = listOf(GameEnemy("kryvavitsa", GameEnemyType.KRYVAVITSA, kryvavitsa)),
        stats = stats,
        config = GameLevelConfig(width = BOARD_SIDE, height = BOARD_SIDE, seed = 7L)
    )

    private companion object {
        const val BOARD_SIDE = 5

        /** 1800 less the engine's 500-point restart penalty. */
        const val RESTART_PENALTY_APPLIED = 1300

        /** 1000 less the engine's 10-point per-turn penalty. */
        const val TURN_PENALTY_APPLIED = 990
    }
}
