package com.sza.fastmediasorter.ui.game

import com.sza.fastmediasorter.domain.game.GameActor
import com.sza.fastmediasorter.domain.game.GameBoard
import com.sza.fastmediasorter.domain.game.GameBoardGenerator
import com.sza.fastmediasorter.domain.game.GameDirection
import com.sza.fastmediasorter.domain.game.GameEnemy
import com.sza.fastmediasorter.domain.game.GameEnemyType
import com.sza.fastmediasorter.domain.game.GameLevelConfig
import com.sza.fastmediasorter.domain.game.GameLevelState
import com.sza.fastmediasorter.domain.game.GamePosition
import com.sza.fastmediasorter.domain.game.GameStats
import com.sza.fastmediasorter.domain.game.GameStateRepository
import com.sza.fastmediasorter.domain.game.GameStateSnapshot
import com.sza.fastmediasorter.domain.game.GameStatus
import com.sza.fastmediasorter.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val boardGenerator = GameBoardGenerator()

    @Test
    fun `init resumes saved game`() = runTest(dispatcherRule.testDispatcher) {
        val snapshot = GameStateSnapshot.fromLevelState(
            boardGenerator.createInitialState(GameLevelConfig(width = 9, height = 9, seed = 11L))
        )
        val repository = FakeGameStateRepository(snapshot, boardGenerator)

        val viewModel = GameViewModel(repository, boardGenerator)
        advanceUntilIdle()

        val ready = viewModel.state.value as GameUiState.Ready
        assertEquals(snapshot.level, ready.levelNumber)
        assertEquals(snapshot.stats.score, ready.score)
    }

    @Test
    fun `move persists accepted turn`() = runTest(dispatcherRule.testDispatcher) {
        val snapshot = GameStateSnapshot.fromLevelState(
            boardGenerator.createInitialState(GameLevelConfig(width = 9, height = 9, seed = 12L))
        )
        val repository = FakeGameStateRepository(snapshot, boardGenerator)
        val viewModel = GameViewModel(repository, boardGenerator)
        advanceUntilIdle()

        for (direction in GameDirection.entries) {
            viewModel.move(direction)
            advanceUntilIdle()
            if (repository.savedSnapshots.isNotEmpty()) break
        }

        val saved = repository.savedSnapshots.last()
        assertTrue(saved.stats.turns > snapshot.stats.turns)
    }

    @Test
    fun `startNewGame uses fresh seed when provider repeats`() = runTest(dispatcherRule.testDispatcher) {
        val snapshot = GameStateSnapshot.fromLevelState(
            boardGenerator.createInitialState(GameLevelConfig(width = 9, height = 9, seed = 13L))
        )
        val repository = FakeGameStateRepository(snapshot, boardGenerator)
        val viewModel = GameViewModel(repository, boardGenerator)
        viewModel.seedProvider = { 500L }
        advanceUntilIdle()

        viewModel.startNewGame()
        advanceUntilIdle()
        viewModel.startNewGame()
        advanceUntilIdle()

        val savedSeeds = repository.savedSnapshots.takeLast(2).map { it.config.seed }
        assertEquals(2, savedSeeds.toSet().size)
    }

    @Test
    fun `startNewGame uses reference board size and level shadow count`() = runTest(dispatcherRule.testDispatcher) {
        val repository = FakeGameStateRepository(null, boardGenerator)
        val viewModel = GameViewModel(repository, boardGenerator)
        viewModel.seedProvider = { 700L }
        advanceUntilIdle()

        viewModel.startNewGame()
        advanceUntilIdle()

        val ready = viewModel.state.value as GameUiState.Ready
        assertEquals(8, ready.levelState.board.width)
        assertEquals(8, ready.levelState.board.height)
        assertEquals(1, ready.levelState.enemies.count { it.type == GameEnemyType.SHADOW })
    }

    @Test
    fun `move into exit wins level and persists`() = runTest(dispatcherRule.testDispatcher) {
        val snapshot = GameStateSnapshot.fromLevelState(exitAdjacentState())
        val repository = FakeGameStateRepository(snapshot, boardGenerator)
        val viewModel = GameViewModel(repository, boardGenerator)
        advanceUntilIdle()

        viewModel.move(GameDirection.RIGHT)
        advanceUntilIdle()

        val ready = viewModel.state.value as GameUiState.Ready
        assertEquals(GameStatus.LEVEL_WON, ready.levelState.status)
        assertEquals(GameStatus.LEVEL_WON, repository.savedSnapshots.single().status)
    }

    @Test
    fun `accepted move exposes player displacement for animation`() = runTest(dispatcherRule.testDispatcher) {
        val snapshot = GameStateSnapshot.fromLevelState(exitAdjacentState())
        val repository = FakeGameStateRepository(snapshot, boardGenerator)
        val viewModel = GameViewModel(repository, boardGenerator)
        advanceUntilIdle()

        viewModel.move(GameDirection.LEFT)
        advanceUntilIdle()

        val ready = viewModel.state.value as GameUiState.Ready
        val playerMove = ready.turnMoves.firstOrNull { it.actor == GameMoveActor.PLAYER }
        assertTrue(ready.turnMoves.isNotEmpty())
        assertEquals(GamePosition(1, 2), playerMove?.from)
        assertEquals(GamePosition(1, 1), playerMove?.to)
    }

    @Test
    fun `advanceLevel clears stale turn moves`() = runTest(dispatcherRule.testDispatcher) {
        val snapshot = GameStateSnapshot.fromLevelState(exitAdjacentState())
        val repository = FakeGameStateRepository(snapshot, boardGenerator)
        val viewModel = GameViewModel(repository, boardGenerator)
        advanceUntilIdle()

        viewModel.move(GameDirection.RIGHT)
        advanceUntilIdle()
        assertTrue((viewModel.state.value as GameUiState.Ready).turnMoves.isNotEmpty())

        viewModel.advanceLevel()
        advanceUntilIdle()

        val ready = viewModel.state.value as GameUiState.Ready
        assertEquals(GameStatus.PLAYING, ready.levelState.status)
        assertTrue(ready.turnMoves.isEmpty())
    }

    @Test
    fun `advanceLevel starts next generated level`() = runTest(dispatcherRule.testDispatcher) {
        val snapshot = GameStateSnapshot.fromLevelState(exitAdjacentState().copy(status = GameStatus.LEVEL_WON))
        val repository = FakeGameStateRepository(snapshot, boardGenerator)
        val viewModel = GameViewModel(repository, boardGenerator)
        advanceUntilIdle()

        viewModel.advanceLevel()
        advanceUntilIdle()

        val ready = viewModel.state.value as GameUiState.Ready
        assertEquals(GameStatus.PLAYING, ready.levelState.status)
        assertEquals(2, ready.levelNumber)
        assertEquals(1, repository.savedSnapshots.size)
    }

    @Test
    fun `restartLevel after game over keeps level and applies penalty`() = runTest(dispatcherRule.testDispatcher) {
        val lostState = exitAdjacentState().copy(
            status = GameStatus.GAME_OVER,
            stats = GameStats(score = 900, highScore = 900),
            config = GameLevelConfig(levelNumber = 3, width = 9, height = 9, shadowCount = 1, seed = 30L)
        )
        val snapshot = GameStateSnapshot.fromLevelState(lostState)
        val repository = FakeGameStateRepository(snapshot, boardGenerator)
        val viewModel = GameViewModel(repository, boardGenerator)
        viewModel.seedProvider = { 100L }
        advanceUntilIdle()

        viewModel.restartLevel()
        advanceUntilIdle()

        val ready = viewModel.state.value as GameUiState.Ready
        assertEquals(GameStatus.PLAYING, ready.levelState.status)
        assertEquals(3, ready.levelNumber)
        assertEquals(400, ready.score)
        assertEquals(900, ready.levelState.stats.highScore)
    }

    private fun exitAdjacentState(): GameLevelState {
        val board = GameBoard.fromRows(
            "#####",
            "#.PE#",
            "#...#",
            "#K..#",
            "#####"
        )
        return GameLevelState(
            board = board,
            player = GameActor(GamePosition(1, 2)),
            enemies = listOf(GameEnemy("kryvavitsa", GameEnemyType.KRYVAVITSA, GamePosition(3, 1))),
            config = GameLevelConfig(levelNumber = 1, width = board.width, height = board.height, shadowCount = 0, seed = 1L)
        )
    }

    private class FakeGameStateRepository(
        initialSnapshot: GameStateSnapshot?,
        private val boardGenerator: GameBoardGenerator
    ) : GameStateRepository {
        private val snapshots = MutableStateFlow(initialSnapshot)
        val savedSnapshots = mutableListOf<GameStateSnapshot>()

        override fun observeSnapshot(): Flow<GameStateSnapshot?> = snapshots

        override suspend fun loadSnapshot(): GameStateSnapshot? = snapshots.value

        override suspend fun loadOrCreate(config: GameLevelConfig): GameStateSnapshot {
            val existing = snapshots.value
            if (existing != null) return existing
            val created = GameStateSnapshot.fromLevelState(boardGenerator.createInitialState(config))
            saveSnapshot(created)
            return created
        }

        override suspend fun saveSnapshot(snapshot: GameStateSnapshot) {
            snapshots.value = snapshot
            savedSnapshots += snapshot
        }

        override suspend fun clearSnapshot() {
            snapshots.value = null
        }
    }
}