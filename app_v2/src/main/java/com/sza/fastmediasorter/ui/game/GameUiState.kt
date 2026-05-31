package com.sza.fastmediasorter.ui.game

import com.sza.fastmediasorter.domain.game.GameBoardValidationError
import com.sza.fastmediasorter.domain.game.GameEnemyType
import com.sza.fastmediasorter.domain.game.GameLevelState
import com.sza.fastmediasorter.domain.game.GameMoveRejectReason
import com.sza.fastmediasorter.domain.game.GamePosition
import com.sza.fastmediasorter.domain.game.GameStatus

sealed interface GameUiState {
    data object Loading : GameUiState

    data class Ready(
        val levelState: GameLevelState,
        val customBoard: Boolean = false,
        val storageReset: Boolean = false,
        val unreadableBoardWarning: Boolean = false,
        val defeatConnection: GameDefeatConnection? = null,
        val lastRejectReason: GameMoveRejectReason? = null
    ) : GameUiState {
        val canAcceptMoves: Boolean
            get() = levelState.status == GameStatus.PLAYING
        val levelNumber: Int
            get() = levelState.config.levelNumber
        val score: Int
            get() = levelState.stats.score
        val turns: Int
            get() = levelState.stats.turns
    }

    data class InvalidBoard(
        val errors: Set<GameBoardValidationError>
    ) : GameUiState

    data class Error(
        val message: String? = null
    ) : GameUiState
}

data class GameDefeatConnection(
    val playerPosition: GamePosition,
    val enemyPosition: GamePosition,
    val enemyType: GameEnemyType
)