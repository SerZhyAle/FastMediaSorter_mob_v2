package com.sza.fastmediasorter.domain.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameBoardGeneratorTest {

    private val generator = GameBoardGenerator()

    @Test
    fun generatedBoardIsDeterministicForSameSeed() {
        val config = GameLevelConfig(width = 10, height = 10, shadowCount = 2, seed = 123L)

        val first = generator.createInitialState(config)
        val second = generator.createInitialState(config)

        assertEquals(first.board, second.board)
        assertEquals(first.player, second.player)
        assertEquals(first.enemies, second.enemies)
    }

    @Test
    fun generatedBoardPassesCustomValidation() {
        val state = generator.createInitialState(
            GameLevelConfig(width = 10, height = 10, shadowCount = 3, seed = 99L)
        )

        val result = generator.validateCustomBoard(state.board, state.player, state.enemies)

        assertTrue(result.isValid)
    }

    @Test
    fun generatedBoardDoesNotUseBoundaryWalls() {
        val state = generator.createInitialState(
            GameLevelConfig(width = 10, height = 10, shadowCount = 3, seed = 99L)
        )

        val topWallCount = (0 until state.board.width).count { col ->
            state.board.cellAt(GamePosition(0, col)) == GameCell.WALL
        }
        assertTrue(topWallCount < state.board.width)
    }

    @Test
    fun generatedBoardUsesLevelAsShadowCountAndReferenceWallDensity() {
        val state = generator.createInitialState(
            GameLevelConfig(width = 10, height = 10, shadowCount = 4, seed = 101L)
        )

        val shadowCount = state.enemies.count { it.type == GameEnemyType.SHADOW }
        val wallDensity = state.board.cells.count { it == GameCell.WALL }.toDouble() / state.board.cells.size.toDouble()

        assertEquals(4, shadowCount)
        assertTrue(wallDensity >= 0.30)
        assertTrue(wallDensity <= 0.40)
    }

    @Test
    fun customValidationRequiresPlayerKryvavitsaAndExit() {
        val board = GameBoard.fromRows(
            "#####",
            "#...#",
            "#...#",
            "#...#",
            "#####"
        )

        val result = generator.validateCustomBoard(board, null, emptyList())

        assertFalse(result.isValid)
        assertTrue(result.errors.contains(GameBoardValidationError.MISSING_PLAYER))
        assertTrue(result.errors.contains(GameBoardValidationError.MISSING_KRYVAVITSA))
        assertTrue(result.errors.contains(GameBoardValidationError.MISSING_EXIT))
    }

    @Test
    fun customValidationRejectsUnreachableExit() {
        val board = GameBoard.fromRows(
            "#######",
            "#P#K..#",
            "###...#",
            "#.#.E.#",
            "#.....#",
            "#######"
        )
        val player = GameActor(GamePosition(1, 1))
        val enemies = listOf(GameEnemy("kryvavitsa", GameEnemyType.KRYVAVITSA, GamePosition(1, 3)))

        val result = generator.validateCustomBoard(board, player, enemies)

        assertFalse(result.isValid)
        assertTrue(result.errors.contains(GameBoardValidationError.EXIT_NOT_REACHABLE))
    }

    @Test
    fun customValidationRejectsUnreachableKryvavitsa() {
        // Column 3 is a solid wall: player/exit live on the left, Kryvavitsa is sealed on the right.
        val board = GameBoard.fromRows(
            "#######",
            "#P.#.K#",
            "#..#..#",
            "#.E#..#",
            "#..#..#",
            "#######"
        )
        val player = GameActor(GamePosition(1, 1))
        val enemies = listOf(GameEnemy("kryvavitsa", GameEnemyType.KRYVAVITSA, GamePosition(1, 5)))

        val result = generator.validateCustomBoard(board, player, enemies)

        assertFalse(result.isValid)
        assertTrue(result.errors.contains(GameBoardValidationError.KRYVAVITSA_NOT_REACHABLE))
        assertFalse(result.errors.contains(GameBoardValidationError.EXIT_NOT_REACHABLE))
    }

    @Test
    fun generatedBoardKeepsKryvavitsaReachableFromPlayer() {
        for (seed in 0L until 25L) {
            val state = generator.createInitialState(
                GameLevelConfig(width = 12, height = 12, shadowCount = 3, seed = seed)
            )
            val kryvavitsa = state.enemies.single { it.type == GameEnemyType.KRYVAVITSA }
            val result = generator.validateCustomBoard(state.board, state.player, state.enemies)
            assertFalse(
                "seed $seed left Kryvavitsa at ${kryvavitsa.position} unreachable",
                result.errors.contains(GameBoardValidationError.KRYVAVITSA_NOT_REACHABLE)
            )
        }
    }

    @Test
    fun customValidationRejectsEnemyAdjacentStart() {
        val board = GameBoard.fromRows(
            "#####",
            "#PE.#",
            "#K..#",
            "#...#",
            "#####"
        )
        val player = GameActor(GamePosition(1, 1))
        val enemies = listOf(GameEnemy("kryvavitsa", GameEnemyType.KRYVAVITSA, GamePosition(2, 1)))

        val result = generator.validateCustomBoard(board, player, enemies)

        assertFalse(result.isValid)
        assertTrue(result.errors.contains(GameBoardValidationError.PLAYER_START_ADJACENT_TO_ENEMY))
    }
}