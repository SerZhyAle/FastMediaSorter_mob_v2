package com.sza.fastmediasorter.wear.ui.apps.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.game.GameBoard
import com.sza.fastmediasorter.wear.domain.game.GameBoardGenerator
import com.sza.fastmediasorter.wear.domain.game.GameDifficulty
import com.sza.fastmediasorter.wear.domain.game.GameDirection
import com.sza.fastmediasorter.wear.domain.game.GameLevelConfig
import com.sza.fastmediasorter.wear.domain.game.GameLevelState
import com.sza.fastmediasorter.wear.domain.game.GameRulesEngine
import com.sza.fastmediasorter.wear.domain.game.GameSeedSource
import com.sza.fastmediasorter.wear.domain.game.GameStateSnapshot
import com.sza.fastmediasorter.wear.domain.game.GameStatus
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Owns the game between the rules engine and the watch's own settings store.
 *
 * The save is written after every accepted turn rather than when the screen closes: a watch app is
 * killed without warning, and a game the player has to abandon mid-level is the failure this ticket
 * set out to avoid (strategic §11 criterion 5).
 */
@HiltViewModel
class GameViewModel @Inject constructor(
    private val preferencesRepository: WearPreferencesRepository,
    private val generator: GameBoardGenerator
) : ViewModel() {

    private val engine = GameRulesEngine()

    private val seedSource = GameSeedSource()

    private var boardWidth = GameBoard.ROUND_STANDARD_WIDTH
    private var boardHeight = GameBoard.ROUND_STANDARD_HEIGHT

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { restoreOrStart() }
    }

    /**
     * S2558: configure board geometry based on screen shape and dimensions.
     */
    fun configureScreen(isRound: Boolean, screenShorterEdgeDp: Int) {
        val (w, h) = when {
            !isRound -> SQUARE_BOARD_SIDE to SQUARE_BOARD_SIDE
            screenShorterEdgeDp <= COMPACT_SCREEN_THRESHOLD_DP -> {
                GameBoard.ROUND_COMPACT_WIDTH to GameBoard.ROUND_COMPACT_HEIGHT
            }
            else -> {
                GameBoard.ROUND_STANDARD_WIDTH to GameBoard.ROUND_STANDARD_HEIGHT
            }
        }
        boardWidth = w
        boardHeight = h
    }

    fun move(direction: GameDirection) {
        val current = _uiState.value.level ?: return
        val result = engine.applyMove(current, direction)
        if (!result.accepted) {
            return
        }
        publish(result.state)
    }

    /**
     * The single action offered once a level ends, and the only way out of a level in progress.
     *
     * A won level advances to the next one and a lost level is played again from its start, because
     * both are the same request from the player's side - "get me to a board I can move on" - and the
     * engine already prices each of them (a completion bonus, a restart penalty). The screen only
     * changes the label.
     */
    fun restart() {
        val current = _uiState.value.level ?: run {
            viewModelScope.launch { restoreOrStart() }
            return
        }
        val nextLevelNumber = when (current.status) {
            GameStatus.LEVEL_WON -> current.config.levelNumber + 1
            else -> current.config.levelNumber
        }
        val generated = generate(nextLevelNumber) ?: return
        val next = when (current.status) {
            GameStatus.LEVEL_WON -> engine.advanceLevel(current, generated)
            GameStatus.GAME_OVER -> engine.restartLevel(current, generated)
            GameStatus.PLAYING -> engine.restartLevelVoluntarily(current, generated)
        }
        publish(next)
    }

    /**
     * Spend a turn without moving, so the enemies step and the player does not.
     *
     * The engine already prices this exactly as the phone does; the watch simply had no way to ask
     * for it until the in-play menu existed (S2158).
     */
    fun skipTurn() {
        val current = _uiState.value.level ?: return
        Timber.d("S2158: skip turn requested at turn %d", current.stats.turns)
        val result = engine.applySkipTurn(current)
        if (!result.accepted) {
            return
        }
        publish(result.state)
    }

    /**
     * Start the current level again from a level still being played, at the engine's restart price.
     *
     * Guarded on [GameStatus.PLAYING] rather than on the menu being open: a level that has already
     * ended is restarted or advanced by [restart], and letting both paths reach a finished level
     * would charge the restart penalty on top of an outcome the player already paid for.
     */
    fun restartLevelNow() {
        val current = _uiState.value.level
        if (current == null || current.status != GameStatus.PLAYING) {
            return
        }
        Timber.d("S2158: voluntary restart of level %d at score %d", current.config.levelNumber, current.stats.score)
        val generated = generate(current.config.levelNumber)
        if (generated != null) {
            publish(engine.restartLevelVoluntarily(current, generated))
        }
    }

    /**
     * S2350: reset the game to level 1 and clear statistics in one tap from the in-game menu.
     */
    fun startNewGame() {
        val currentLevel = _uiState.value.level?.config?.levelNumber ?: FIRST_LEVEL_NUMBER
        Timber.d("S2350: starting new game from level %d", currentLevel)
        val generated = generate(FIRST_LEVEL_NUMBER) ?: return
        publish(generated)
    }

    private suspend fun restoreOrStart() {
        val stored = preferencesRepository.gameState.first()
        val restored = GameStateSnapshot.fromStorage(stored)?.toLevelState()
        if (restored != null) {
            Timber.d("S2553: resumed saved game at level %d turn %d", restored.config.levelNumber, restored.stats.turns)
            _uiState.value = GameUiState(restored, restored.stats, restored.status)
            return
        }
        Timber.d("S2553: no readable save, starting a fresh game")
        // An absent or unreadable save is a first run, never an error the player has to see.
        val generated = generate(FIRST_LEVEL_NUMBER) ?: return
        publish(generated)
    }

    private fun publish(state: GameLevelState) {
        _uiState.value = GameUiState(state, state.stats, state.status)
        viewModelScope.launch {
            preferencesRepository.setGameState(GameStateSnapshot.fromLevelState(state).toStorage())
        }
    }

    /**
     * A seed is drawn fresh for every generated board, so no two boards repeat (S2494).
     *
     * The seed used to be derived from the level number, which made every entry into level 1 the same
     * board and every restart the same second try. The phone draws a new seed both on advancing and
     * on either restart, and the watch is brought to that behaviour. A restored save does not pass
     * through here at all - it carries the seed it was written with, so returning to an interrupted
     * game returns the board the player left.
     */
    private fun generate(levelNumber: Int): GameLevelState? {
        val config = GameLevelConfig(
            levelNumber = levelNumber,
            difficulty = difficultyFor(levelNumber),
            width = boardWidth,
            height = boardHeight,
            shadowCount = shadowCountFor(levelNumber),
            seed = seedSource.nextSeed(levelNumber)
        )
        Timber.d(
            "S2494: level %d generated with seed %d (size %dx%d)",
            levelNumber,
            config.seed,
            boardWidth,
            boardHeight
        )
        val generated = generator.createInitialState(config)
        if (generated == null) {
            Timber.w("game: level %d could not be generated, board left unchanged", levelNumber)
        }
        return generated
    }

    /**
     * The one lever that makes a later level harder.
     *
     * The board side is deliberately not the lever: the square the board is drawn into is capped by
     * the glass, so every added row only shrinks a cell that is already about 16 dp across, and
     * S1965 settled that a watch control is not made smaller to buy a fit (strategic ADR-3). More
     * shadows cost no legibility at all - the same cells, more things moving in them. Capped well
     * short of the density at which the generator runs out of placements satisfying its own distance
     * minimums and returns null.
     */
    private fun shadowCountFor(levelNumber: Int): Int =
        minOf(SHADOW_COUNT_MAX, SHADOW_COUNT_BASE + (levelNumber - 1) / LEVELS_PER_SHADOW)

    /**
     * The band the level falls into, carried in the config and in the save.
     *
     * The generator does not read it today and this ticket does not teach it to: it is a line-by-line
     * mirror of the phone's, kept so one config and one seed yield one board on both devices
     * (strategic ADR-2). The band is recorded so the two sides agree about what a level was, while
     * [shadowCountFor] is what actually changes the game.
     */
    private fun difficultyFor(levelNumber: Int): GameDifficulty = when {
        levelNumber < NORMAL_FROM_LEVEL -> GameDifficulty.EASY
        levelNumber < HARD_FROM_LEVEL -> GameDifficulty.NORMAL
        else -> GameDifficulty.HARD
    }

    private companion object {
        const val FIRST_LEVEL_NUMBER = 1

        const val SQUARE_BOARD_SIDE = 9
        const val COMPACT_SCREEN_THRESHOLD_DP = 192

        const val SHADOW_COUNT_BASE = 2
        const val SHADOW_COUNT_MAX = 5
        const val LEVELS_PER_SHADOW = 3
        const val NORMAL_FROM_LEVEL = 4
        const val HARD_FROM_LEVEL = 10
    }
}
