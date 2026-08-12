package com.sza.fastmediasorter.domain.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRulesEngineTest {

    private val engine = GameRulesEngine()

    @Test
    fun rejectsDiagonalAndNoOpDelta() {
        val state = testState(player = GamePosition(3, 2), kryvavitsa = GamePosition(1, 1))

        val diagonal = engine.applyMoveDelta(state, -1, 1)
        val noOp = engine.applyMoveDelta(state, 0, 0)

        assertFalse(diagonal.accepted)
        assertEquals(GameMoveRejectReason.INVALID_DIRECTION, diagonal.rejectReason)
        assertFalse(noOp.accepted)
        assertEquals(GameMoveRejectReason.INVALID_DIRECTION, noOp.rejectReason)
    }

    @Test
    fun pushesWallWhenDestinationIsFloor() {
        val board = GameBoard.fromRows(
            "#####",
            "#...#",
            "#..E#",
            "#...#",
            "#####"
        ).withCell(GamePosition(3, 2), GameCell.WALL)
        val state = testState(board = board, player = GamePosition(3, 1), kryvavitsa = GamePosition(1, 1))

        val result = engine.applyMove(state, GameDirection.RIGHT, shadowOrderSeed = 1L)

        assertTrue(result.accepted)
        assertEquals(GamePosition(3, 2), result.state.player.position)
        assertEquals(GameCell.FLOOR, result.state.board.cellAt(GamePosition(3, 2)))
        assertEquals(GameCell.WALL, result.state.board.cellAt(GamePosition(3, 3)))
        assertTrue(result.events.any { it is GameEvent.WallPushed })
        assertEquals(1, result.state.stats.wallPushes)
    }

    @Test
    fun pushesWallIntoExit() {
        val board = GameBoard.fromRows(
            "#####",
            "#K..#",
            "#PWE#",
            "#...#",
            "#####"
        )
        val state = testState(board = board, player = GamePosition(2, 1), kryvavitsa = GamePosition(1, 1))

        val result = engine.applyMove(state, GameDirection.RIGHT)

        assertTrue(result.accepted)
        assertEquals(GamePosition(2, 2), result.state.player.position)
        assertEquals(GameCell.FLOOR, result.state.board.cellAt(GamePosition(2, 2)))
        assertEquals(GameCell.EXIT, result.state.board.cellAt(GamePosition(2, 3)))
        assertTrue(result.events.any { it is GameEvent.WallDestroyed })
        assertEquals(0, result.state.stats.wallPushes)
    }

    @Test
    fun blockedMoveCountsTurnAndRunsEnemyCycle() {
        val board = GameBoard.fromRows(
            ".....",
            ".....",
            "..E..",
            ".....",
            "....."
        )
        val state = testState(board = board, player = GamePosition(0, 0), kryvavitsa = GamePosition(4, 4))

        val result = engine.applyMove(state, GameDirection.LEFT, shadowOrderSeed = 1L)

        assertTrue(result.accepted)
        assertEquals(GamePosition(0, 0), result.state.player.position)
        assertEquals(1, result.state.stats.turns)
        assertTrue(result.events.any { it is GameEvent.PlayerBlocked })
        assertTrue(result.events.any { it is GameEvent.EnemyMoved || it is GameEvent.EnemyStayed })
    }

    @Test
    fun pushingWallIntoShadowCrushesShadowAndScoresBonus() {
        val board = GameBoard.fromRows(
            "########",
            "#K.....#",
            "#......#",
            "#PWS.E.#",
            "#......#",
            "########"
        )
        val state = testState(
            board = board,
            player = GamePosition(3, 1),
            kryvavitsa = GamePosition(1, 1),
            shadows = listOf(GamePosition(3, 3))
        )

        val result = engine.applyMove(state, GameDirection.RIGHT, shadowOrderSeed = 3L)

        assertTrue(result.accepted)
        assertTrue(result.events.any { it is GameEvent.ShadowCrushed })
        assertTrue(result.state.enemies.none { it.type == GameEnemyType.SHADOW })
        assertEquals(50, result.state.stats.score)
    }

    @Test
    fun movingIntoEnemyAcceptsTurnAndEndsGame() {
        val board = GameBoard.fromRows(
            "#####",
            "#PK.#",
            "#..E#",
            "#...#",
            "#####"
        )
        val state = testState(board = board, player = GamePosition(1, 1), kryvavitsa = GamePosition(1, 2))

        val result = engine.applyMove(state, GameDirection.RIGHT)

        assertTrue(result.accepted)
        assertEquals(GameStatus.GAME_OVER, result.state.status)
        assertTrue(result.events.any { it is GameEvent.PlayerCaptured })
        assertEquals(0, result.state.stats.score)
    }

    @Test
    fun movesPlayerThenKryvavitsaBeforeShadows() {
        val state = testState(
            player = GamePosition(3, 2),
            kryvavitsa = GamePosition(1, 1),
            shadows = listOf(GamePosition(1, 3), GamePosition(3, 1))
        )

        val result = engine.applyMove(state, GameDirection.RIGHT, shadowOrderSeed = 42L)

        val movedEvents = result.events.filterIsInstance<GameEvent.EnemyMoved>()
        assertEquals("kryvavitsa", movedEvents.first().enemyId)
        assertTrue(result.events.first() is GameEvent.PlayerMoved)
    }

    @Test
    fun kryvavitsaUsesHorizontalGreedyPriorityBeforeVertical() {
        val board = GameBoard.fromRows(
            ".......",
            ".......",
            ".......",
            "......E",
            ".......",
            ".......",
            "......."
        )
        val state = testState(board = board, player = GamePosition(1, 1), kryvavitsa = GamePosition(2, 4))

        val result = engine.applyMove(state, GameDirection.UP, shadowOrderSeed = 1L)

        assertTrue(
            result.events.any {
                it == GameEvent.EnemyMoved(
                    enemyId = "kryvavitsa",
                    type = GameEnemyType.KRYVAVITSA,
                    from = GamePosition(2, 4),
                    to = GamePosition(2, 3)
                )
            }
        )
    }

    @Test
    fun kryvavitsaCapturesWhenPlayerEndsMoveAdjacentInsteadOfEscapingTowardExit() {
        val board = GameBoard.fromRows(
            ".....",
            ".....",
            ".P.K.",
            ".....",
            "...E."
        )
        val state = testState(board = board, player = GamePosition(2, 1), kryvavitsa = GamePosition(2, 3))

        val result = engine.applyMove(state, GameDirection.RIGHT, shadowOrderSeed = 1L)

        assertTrue(result.accepted)
        assertEquals(GameStatus.GAME_OVER, result.state.status)
        assertEquals(GamePosition(2, 3), result.state.enemies.first { it.type == GameEnemyType.KRYVAVITSA }.position)
        assertTrue(result.events.any { it is GameEvent.EnemyStayed })
        assertTrue(result.events.any { it is GameEvent.PlayerCaptured })
        assertTrue(result.events.none { it is GameEvent.EnemyMoved && it.enemyId == "kryvavitsa" })
    }

    @Test
    fun shadowsCanWanderAwayFromPlayer() {
        val board = GameBoard.fromRows(
            "#######",
            "#K....#",
            "#.....#",
            "#..S..#",
            "#..P.E#",
            "#.....#",
            "#######"
        )
        val state = testState(
            board = board,
            player = GamePosition(4, 3),
            kryvavitsa = GamePosition(1, 1),
            shadows = listOf(GamePosition(3, 3))
        )

        val awayMoveFound = (1L..50L).any { seed ->
            val result = engine.applyMove(state, GameDirection.RIGHT, shadowOrderSeed = seed)
            val player = result.state.player.position
            val shadow = result.state.enemies.first { it.type == GameEnemyType.SHADOW }
            shadow.position.manhattanDistanceTo(player) > 2
        }

        assertTrue(awayMoveFound)
    }

    @Test
    fun enemyCapturesOnlyAfterEnemyActionByOrthogonalAdjacency() {
        val board = GameBoard.fromRows(
            "#######",
            "#.....#",
            "#....E#",
            "#.....#",
            "#######"
        )
        val state = testState(board = board, player = GamePosition(3, 1), kryvavitsa = GamePosition(3, 4))

        val result = engine.applyMove(state, GameDirection.RIGHT, shadowOrderSeed = 7L)

        assertTrue(result.accepted)
        assertEquals(GameStatus.GAME_OVER, result.state.status)
        assertTrue(result.events.any { it is GameEvent.PlayerCaptured })
    }

    @Test
    fun steppingOntoExitWinsBeforeEnemyTurn() {
        val board = GameBoard.fromRows(
            "#####",
            "#K..#",
            "#..E#",
            "#...#",
            "#####"
        )
        val state = testState(board = board, player = GamePosition(2, 2), kryvavitsa = GamePosition(1, 1))

        val result = engine.applyMove(state, GameDirection.RIGHT)

        assertTrue(result.accepted)
        assertEquals(GameStatus.LEVEL_WON, result.state.status)
        assertTrue(result.events.any { it == GameEvent.ExitReached })
        assertTrue(result.events.none { it is GameEvent.EnemyMoved })
        assertEquals(1, result.state.stats.levelsCompleted)
        assertEquals(1000, result.state.stats.score)
    }

    @Test
    fun scoringAppliesTurnPenaltyAndWallBonusWithZeroFloor() {
        val board = GameBoard.fromRows(
            "######",
            "#K...#",
            "#..W.#",
            "#...E#",
            "#....#",
            "#....#",
            "######"
        )
        val state = testState(board = board, player = GamePosition(2, 2), kryvavitsa = GamePosition(1, 1))

        val push = engine.applyMove(state, GameDirection.RIGHT, shadowOrderSeed = 5L)

        assertEquals(0, push.state.stats.score)
        assertEquals(1, push.state.stats.turns)
        assertEquals(1, push.state.stats.wallPushes)
    }

    @Test
    fun restartLevelKeepsCurrentProgressWithPenalty() {
        val lost = testState(
            player = GamePosition(3, 2),
            kryvavitsa = GamePosition(1, 1)
        ).copy(
            status = GameStatus.GAME_OVER,
            stats = GameStats(score = 900, highScore = 900)
        )
        val restarted = testState(player = GamePosition(3, 1), kryvavitsa = GamePosition(1, 1))

        val result = engine.restartLevel(lost, restarted)

        assertEquals(GameStatus.PLAYING, result.status)
        assertEquals(400, result.stats.score)
        assertEquals(900, result.stats.highScore)
    }

    @Test
    fun voluntaryRestartCostsTheSameAsDying() {
        val live = testState(
            player = GamePosition(3, 2),
            kryvavitsa = GamePosition(1, 1)
        ).copy(
            status = GameStatus.PLAYING,
            stats = GameStats(score = 900, highScore = 900)
        )
        val lost = live.copy(status = GameStatus.GAME_OVER)
        val restarted = testState(player = GamePosition(3, 1), kryvavitsa = GamePosition(1, 1))

        val voluntary = engine.restartLevelVoluntarily(live, restarted)
        val afterDeath = engine.restartLevel(lost, restarted)

        assertEquals(GameStatus.PLAYING, voluntary.status)
        assertEquals(400, voluntary.stats.score)
        assertEquals(afterDeath.stats.score, voluntary.stats.score)
        assertEquals(afterDeath.stats.highScore, voluntary.stats.highScore)
        assertEquals(afterDeath.stats.survivalStreak, voluntary.stats.survivalStreak)
    }

    @Test(expected = IllegalArgumentException::class)
    fun voluntaryRestartRejectsALostLevel() {
        val lost = testState(
            player = GamePosition(3, 2),
            kryvavitsa = GamePosition(1, 1)
        ).copy(status = GameStatus.GAME_OVER)
        val restarted = testState(player = GamePosition(3, 1), kryvavitsa = GamePosition(1, 1))

        engine.restartLevelVoluntarily(lost, restarted)
    }

    private fun testState(
        board: GameBoard = GameBoard.fromRows(
            "#####",
            "#...#",
            "#..E#",
            "#...#",
            "#####"
        ),
        player: GamePosition,
        kryvavitsa: GamePosition,
        shadows: List<GamePosition> = emptyList()
    ): GameLevelState = GameLevelState(
        board = board,
        player = GameActor(player),
        enemies = buildList {
            add(GameEnemy("kryvavitsa", GameEnemyType.KRYVAVITSA, kryvavitsa))
            shadows.forEachIndexed { index, position ->
                add(GameEnemy("shadow_${index + 1}", GameEnemyType.SHADOW, position))
            }
        },
        config = GameLevelConfig(seed = 11L)
    )
}