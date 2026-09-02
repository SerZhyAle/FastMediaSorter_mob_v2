package com.sza.fastmediasorter.wear.ui.apps.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.game.GameBoardGenerator
import com.sza.fastmediasorter.wear.domain.game.GameDifficulty
import com.sza.fastmediasorter.wear.domain.game.GameDirection
import com.sza.fastmediasorter.wear.domain.game.GameLevelConfig
import com.sza.fastmediasorter.wear.domain.game.GameLevelState
import com.sza.fastmediasorter.wear.domain.game.GameRulesEngine
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

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { restoreOrStart() }
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

    private suspend fun restoreOrStart() {
        val stored = preferencesRepository.gameState.first()
        val restored = GameStateSnapshot.fromStorage(stored)?.toLevelState()
        if (restored != null) {
            _uiState.value = GameUiState(restored, restored.stats, restored.status)
            return
        }
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
     * A level's seed is derived from its number, so the same level is always the same board - that is
     * what makes replaying a lost level a second try rather than a different game.
     */
    private fun generate(levelNumber: Int): GameLevelState? {
        val config = GameLevelConfig(
            levelNumber = levelNumber,
            difficulty = difficultyFor(levelNumber),
            width = BOARD_SIDE,
            height = BOARD_SIDE,
            shadowCount = shadowCountFor(levelNumber),
            seed = levelNumber.toLong() * LEVEL_SEED_STEP
        )
        Timber.d("S2008: level $levelNumber shadows ${config.shadowCount} band ${config.difficulty}")
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

        /** Small enough that every cell stays readable on a watch, above the generator's minimum. */
        const val BOARD_SIDE = 9
        const val SHADOW_COUNT_BASE = 2
        const val SHADOW_COUNT_MAX = 5
        const val LEVELS_PER_SHADOW = 3
        const val NORMAL_FROM_LEVEL = 4
        const val HARD_FROM_LEVEL = 10
        const val LEVEL_SEED_STEP = 7919L
    }
}
