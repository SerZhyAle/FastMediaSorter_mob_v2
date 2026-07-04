package com.sza.fastmediasorter.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.game.GameBoardGenerator
import com.sza.fastmediasorter.domain.game.GameDifficulty
import com.sza.fastmediasorter.domain.game.GameDirection
import com.sza.fastmediasorter.domain.game.GameEnemyType
import com.sza.fastmediasorter.domain.game.GameEvent
import com.sza.fastmediasorter.domain.game.GameLevelConfig
import com.sza.fastmediasorter.domain.game.GameLevelState
import com.sza.fastmediasorter.domain.game.GameMode
import com.sza.fastmediasorter.domain.game.GameRulesEngine
import com.sza.fastmediasorter.domain.game.GameStateRepository
import com.sza.fastmediasorter.domain.game.GameStateSnapshot
import com.sza.fastmediasorter.domain.game.GameStatus
import com.sza.fastmediasorter.domain.game.GameTurnResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class GameViewModel @Inject constructor(
    private val gameStateRepository: GameStateRepository,
    private val boardGenerator: GameBoardGenerator
) : ViewModel() {

    private val rulesEngine = GameRulesEngine()
    private val _state = MutableStateFlow<GameUiState>(GameUiState.Loading)
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    internal var seedProvider: () -> Long = { System.nanoTime() xor System.currentTimeMillis() }
    private var seedNonce = 0L
    private var currentMode = GameMode.CLASSIC

    init {
        resumeGame()
    }

    fun resumeGame() {
        viewModelScope.launch {
            _state.value = GameUiState.Loading
            try {
                currentMode = gameStateRepository.loadMode()
                val snapshot = gameStateRepository.loadOrCreate(defaultConfig())
                _state.value = ensurePlayableOnResume(readyFromSnapshot(snapshot))
            } catch (exception: RuntimeException) {
                Timber.e(exception, "GameViewModel: failed to load game")
                _state.value = GameUiState.Error(exception.message)
            }
        }
    }

    fun startNewGame() {
        viewModelScope.launch {
            val levelState = boardGenerator.createInitialState(defaultConfig())
            publishAndPersist(GameUiState.Ready(levelState = levelState, mode = currentMode))
        }
    }

    // S0804: mode is presentational, so switching re-skins the live board without touching the snapshot.
    fun setMode(mode: GameMode) {
        viewModelScope.launch {
            if (mode == currentMode) return@launch
            currentMode = mode
            gameStateRepository.saveMode(mode)
            val ready = _state.value as? GameUiState.Ready ?: return@launch
            _state.value = ready.copy(mode = mode)
        }
    }

    fun restartLevel() {
        viewModelScope.launch {
            val ready = _state.value as? GameUiState.Ready ?: return@launch
            if (ready.levelState.status != GameStatus.GAME_OVER) return@launch

            val currentConfig = ready.levelState.config
            val restartConfig = currentConfig.copy(seed = nextSeed(currentConfig.levelNumber))
            val generatedLevel = boardGenerator.createInitialState(restartConfig)
            val restartedState = rulesEngine.restartLevel(ready.levelState, generatedLevel)
            publishAndPersist(
                ready.copy(
                    levelState = restartedState,
                    unreadableBoardWarning = shouldWarnForUnreadableBoard(restartedState.config, ready.customBoard),
                    defeatConnection = null,
                    lastRejectReason = null,
                    turnMoves = emptyList()
                )
            )
        }
    }

    fun move(direction: GameDirection) {
        viewModelScope.launch {
            val ready = _state.value as? GameUiState.Ready ?: return@launch
            if (!ready.canAcceptMoves) return@launch

            val result = rulesEngine.applyMove(ready.levelState, direction)
            if (!result.accepted) {
                _state.value = ready.copy(lastRejectReason = result.rejectReason, turnMoves = emptyList())
                return@launch
            }

            publishAndPersist(
                ready.copy(
                    levelState = result.state,
                    unreadableBoardWarning = shouldWarnForUnreadableBoard(result.state.config, ready.customBoard),
                    defeatConnection = result.defeatConnection(),
                    lastRejectReason = null,
                    turnMoves = result.toActorMoves()
                )
            )
        }
    }

    // S0928: player forgoes movement; the rules engine still ticks the turn and moves the enemies.
    fun skipTurn() {
        viewModelScope.launch {
            val ready = _state.value as? GameUiState.Ready ?: return@launch
            if (!ready.canAcceptMoves) return@launch
            Timber.d("S0928: skipTurn turns=${ready.turns}")

            val result = rulesEngine.applySkipTurn(ready.levelState)
            if (!result.accepted) {
                _state.value = ready.copy(lastRejectReason = result.rejectReason, turnMoves = emptyList())
                return@launch
            }

            publishAndPersist(
                ready.copy(
                    levelState = result.state,
                    unreadableBoardWarning = shouldWarnForUnreadableBoard(result.state.config, ready.customBoard),
                    defeatConnection = result.defeatConnection(),
                    lastRejectReason = null,
                    turnMoves = result.toActorMoves()
                )
            )
        }
    }

    fun advanceLevel() {
        viewModelScope.launch {
            val ready = _state.value as? GameUiState.Ready ?: return@launch
            if (ready.levelState.status != GameStatus.LEVEL_WON) return@launch

            val nextConfig = defaultConfig(
                levelNumber = ready.levelNumber + 1,
                difficulty = ready.levelState.config.difficulty
            )
            val generatedNextLevel = boardGenerator.createInitialState(nextConfig)
            val advancedState = rulesEngine.advanceLevel(ready.levelState, generatedNextLevel)
            publishAndPersist(
                ready.copy(
                    levelState = advancedState,
                    unreadableBoardWarning = shouldWarnForUnreadableBoard(advancedState.config, ready.customBoard),
                    defeatConnection = null,
                    lastRejectReason = null,
                    turnMoves = emptyList()
                )
            )
        }
    }

    private suspend fun publishAndPersist(ready: GameUiState.Ready) {
        _state.value = ready
        gameStateRepository.saveSnapshot(
            GameStateSnapshot.fromLevelState(
                ready.levelState,
                customBoard = ready.customBoard
            )
        )
    }

    // A persisted terminal level (won or lost) restored as-is would keep the controls locked until a manual
    // reset, since moves are only accepted while PLAYING. Regenerate a playable level so launch is interactive.
    private suspend fun ensurePlayableOnResume(ready: GameUiState.Ready): GameUiState.Ready =
        when (ready.levelState.status) {
            GameStatus.PLAYING -> ready
            GameStatus.GAME_OVER -> {
                val restartConfig = ready.levelState.config.copy(seed = nextSeed(ready.levelNumber))
                val generated = boardGenerator.createInitialState(restartConfig)
                persistResumeHeal(ready, rulesEngine.restartLevel(ready.levelState, generated))
            }
            GameStatus.LEVEL_WON -> {
                val nextConfig = defaultConfig(
                    levelNumber = ready.levelNumber + 1,
                    difficulty = ready.levelState.config.difficulty
                )
                val generated = boardGenerator.createInitialState(nextConfig)
                persistResumeHeal(ready, rulesEngine.advanceLevel(ready.levelState, generated))
            }
        }

    private suspend fun persistResumeHeal(ready: GameUiState.Ready, healed: GameLevelState): GameUiState.Ready {
        val next = ready.copy(
            levelState = healed,
            unreadableBoardWarning = shouldWarnForUnreadableBoard(healed.config, ready.customBoard),
            defeatConnection = null,
            lastRejectReason = null,
            turnMoves = emptyList()
        )
        gameStateRepository.saveSnapshot(
            GameStateSnapshot.fromLevelState(next.levelState, customBoard = next.customBoard)
        )
        return next
    }

    private fun readyFromSnapshot(snapshot: GameStateSnapshot): GameUiState.Ready {
        val levelState = snapshot.toLevelState()
        return GameUiState.Ready(
            levelState = levelState,
            mode = currentMode,
            customBoard = snapshot.customBoard,
            unreadableBoardWarning = shouldWarnForUnreadableBoard(levelState.config, snapshot.customBoard)
        )
    }

    private fun defaultConfig(
        levelNumber: Int = 1,
        difficulty: GameDifficulty = GameDifficulty.NORMAL
    ): GameLevelConfig {
        val boardSize = boardSizeForLevel(levelNumber)
        return GameLevelConfig(
            levelNumber = levelNumber,
            difficulty = difficulty,
            width = boardSize,
            height = boardSize,
            shadowCount = levelNumber.coerceAtLeast(1),
            seed = nextSeed(levelNumber)
        )
    }

    // S0803: field size = BASE + level/divisor with integer division (level 1..2 -> 8, 3..5 -> 9, ..),
    // capped at MAX so cells stay readable. Wall count stays proportional to cell count in the generator.
    private fun boardSizeForLevel(levelNumber: Int): Int {
        val size = (BOARD_SIZE_BASE + levelNumber / BOARD_SIZE_GROWTH_DIVISOR).coerceAtMost(BOARD_SIZE_MAX)
        return size
    }

    private fun nextSeed(levelNumber: Int): Long {
        // The nonce prevents identical boards when several games start inside one clock tick.
        seedNonce += 1L
        return seedProvider() + (seedNonce * SEED_NONCE_STEP) + levelNumber
    }

    // Translate the turn's movement events into per-actor displacements the board view animates.
    private fun GameTurnResult.toActorMoves(): List<GameActorMove> = events.mapNotNull { event ->
        when (event) {
            is GameEvent.PlayerMoved -> GameActorMove(GameMoveActor.PLAYER, event.from, event.to)
            is GameEvent.EnemyMoved -> GameActorMove(event.type.toMoveActor(), event.from, event.to)
            else -> null
        }
    }

    private fun GameEnemyType.toMoveActor(): GameMoveActor = when (this) {
        GameEnemyType.KRYVAVITSA -> GameMoveActor.KRYVAVITSA
        GameEnemyType.SHADOW -> GameMoveActor.SHADOW
    }

    private fun GameTurnResult.defeatConnection(): GameDefeatConnection? {
        if (state.status != GameStatus.GAME_OVER) return null
        val capture = events.filterIsInstance<GameEvent.PlayerCaptured>().lastOrNull() ?: return null
        val enemy = state.enemies.firstOrNull { it.id == capture.enemyId } ?: return null
        return GameDefeatConnection(
            playerPosition = state.player.position,
            enemyPosition = enemy.position,
            enemyType = enemy.type
        )
    }

    private fun shouldWarnForUnreadableBoard(config: GameLevelConfig, customBoard: Boolean): Boolean {
        if (!customBoard) return false
        return config.width > LARGE_CUSTOM_BOARD_SIZE || config.height > LARGE_CUSTOM_BOARD_SIZE
    }

    companion object {
        private const val BOARD_SIZE_BASE = 8
        private const val BOARD_SIZE_GROWTH_DIVISOR = 3
        private const val BOARD_SIZE_MAX = 20
        private const val LARGE_CUSTOM_BOARD_SIZE = 18
        private const val SEED_NONCE_STEP = 104729L
    }
}