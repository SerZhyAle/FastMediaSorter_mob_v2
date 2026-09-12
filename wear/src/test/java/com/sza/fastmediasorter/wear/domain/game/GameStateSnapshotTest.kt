package com.sza.fastmediasorter.wear.domain.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S2553: the save has carried the game across a restart since S1710 with nothing testing it, so a
 * field that stopped surviving the round trip would first be noticed by a player losing a level.
 */
class GameStateSnapshotTest {

    @Test
    fun `a game in progress survives the round trip through storage`() {
        val original = midGameState()

        val restored = GameStateSnapshot
            .fromStorage(GameStateSnapshot.fromLevelState(original).toStorage())
            ?.toLevelState()

        assertEquals(original, restored)
    }

    @Test
    fun `the seed survives, so the restored board is the one the player left`() {
        val original = midGameState()

        val restored = GameStateSnapshot
            .fromStorage(GameStateSnapshot.fromLevelState(original).toStorage())
            ?.toLevelState()

        assertEquals(original.config.seed, restored?.config?.seed)
        assertEquals(original.board.cells, restored?.board?.cells)
        assertEquals(original.player.position, restored?.player?.position)
        assertEquals(original.enemies, restored?.enemies)
        assertEquals(original.stats, restored?.stats)
    }

    @Test
    fun `an absent save reads as no save at all`() {
        assertNull(GameStateSnapshot.fromStorage(null))
    }

    @Test
    fun `a truncated save is discarded rather than half-read`() {
        val stored = GameStateSnapshot.fromLevelState(midGameState()).toStorage()

        assertNull(GameStateSnapshot.fromStorage(stored.substringBeforeLast(FIELD_SEPARATOR)))
    }

    @Test
    fun `a save written by another schema version is discarded`() {
        val stored = GameStateSnapshot.fromLevelState(midGameState()).toStorage()
        val foreign = stored.replaceFirst(
            GameStateSnapshot.CURRENT_SCHEMA_VERSION.toString(),
            (GameStateSnapshot.CURRENT_SCHEMA_VERSION + 1).toString()
        )

        assertNull(GameStateSnapshot.fromStorage(foreign))
    }

    @Test
    fun `a round board with void cells survives the round trip through storage`() {
        val roundBoard = GameBoard.createRoundTemplate(
            GameBoard.ROUND_STANDARD_WIDTH,
            GameBoard.ROUND_STANDARD_HEIGHT
        )
        val original = GameLevelState(
            board = roundBoard,
            player = GameActor(GamePosition(5, 5)),
            enemies = listOf(
                GameEnemy("k", GameEnemyType.KRYVAVITSA, GamePosition(2, 2))
            ),
            stats = GameStats(turns = 10, score = 150),
            config = GameLevelConfig(
                levelNumber = 1,
                width = GameBoard.ROUND_STANDARD_WIDTH,
                height = GameBoard.ROUND_STANDARD_HEIGHT,
                seed = 9999L
            ),
            status = GameStatus.PLAYING
        )

        val restored = GameStateSnapshot
            .fromStorage(GameStateSnapshot.fromLevelState(original).toStorage())
            ?.toLevelState()

        assertEquals(original, restored)
        assertEquals(GameCell.VOID, restored?.board?.cellAt(GamePosition(0, 0)))
        assertEquals(GameCell.FLOOR, restored?.board?.cellAt(GamePosition(0, 2)))
    }

    /** A level a player could plausibly walk away from: moved player, spent turns, a live score. */
    private fun midGameState(): GameLevelState = GameLevelState(
        board = GameBoard.fromRows(
            ".#...",
            ".....",
            "..#..",
            ".....",
            "....X"
        ),
        player = GameActor(GamePosition(1, 2)),
        enemies = listOf(
            GameEnemy("k", GameEnemyType.KRYVAVITSA, GamePosition(4, 0)),
            GameEnemy("s1", GameEnemyType.SHADOW, GamePosition(0, 4))
        ),
        stats = GameStats(
            turns = 17,
            wallPushes = 3,
            levelsCompleted = 2,
            survivalStreak = 5,
            score = 420,
            highScore = 900
        ),
        config = GameLevelConfig(
            levelNumber = 3,
            difficulty = GameDifficulty.EASY,
            width = 5,
            height = 5,
            shadowCount = 1,
            seed = 1234567890123L
        ),
        status = GameStatus.PLAYING
    )

    private companion object {
        /** The separator `GameStateSnapshot` writes between fields; dropping one truncates a save. */
        const val FIELD_SEPARATOR = "\u001F"
    }
}
