package com.sza.fastmediasorter.wear.ui.apps.game

import com.sza.fastmediasorter.wear.domain.game.GameLevelState
import com.sza.fastmediasorter.wear.domain.game.GameStats
import com.sza.fastmediasorter.wear.domain.game.GameStatus

/**
 * What the game screen draws.
 *
 * [level] is null only while the first level is still being restored or generated; the score and the
 * status are carried separately so the header keeps its last known values through that moment
 * instead of blinking back to zero.
 */
data class GameUiState(
    val level: GameLevelState? = null,
    val stats: GameStats = GameStats(),
    val status: GameStatus = GameStatus.PLAYING
)
