package com.sza.fastmediasorter.ui.game

import com.sza.fastmediasorter.domain.game.GameBoardValidationError
import com.sza.fastmediasorter.domain.game.GameEnemyType
import com.sza.fastmediasorter.domain.game.GameLevelState
import com.sza.fastmediasorter.domain.game.GameMode
import com.sza.fastmediasorter.domain.game.GameMoveRejectReason
import com.sza.fastmediasorter.domain.game.GamePosition
import com.sza.fastmediasorter.domain.game.GameStatus

sealed interface GameUiState {
    data object Loading : GameUiState

    data class Ready(
        val levelState: GameLevelState,
        // S0804: active visual skin; carried through every state copy so re-skins survive turns/levels.
        val mode: GameMode = GameMode.CLASSIC,
        val customBoard: Boolean = false,
        val storageReset: Boolean = false,
        val unreadableBoardWarning: Boolean = false,
        val defeatConnection: GameDefeatConnection? = null,
        val lastRejectReason: GameMoveRejectReason? = null,
        // Actor displacements produced by the most recent accepted turn; drives the board move animation.
        // Empty for any non-move state update (resume, restart, advance) so nothing animates spuriously.
        val turnMoves: List<GameActorMove> = emptyList()
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

// One actor's displacement during a single turn, used to drive the board's primitive move animation.
data class GameActorMove(
    val actor: GameMoveActor,
    val from: GamePosition,
    val to: GamePosition
)

enum class GameMoveActor {
    PLAYER,
    KRYVAVITSA,
    SHADOW
}