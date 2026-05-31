package com.sza.fastmediasorter.domain.game

data class GameStateSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val level: Int,
    val difficulty: GameDifficulty,
    val board: GameBoard,
    val player: GameActor,
    val enemies: List<GameEnemy>,
    val stats: GameStats,
    val config: GameLevelConfig,
    val status: GameStatus,
    val customBoard: Boolean = false,
    val updatedAtEpochMillis: Long = 0L
) {
    fun toLevelState(): GameLevelState = GameLevelState(
        board = board,
        player = player,
        enemies = enemies,
        stats = stats,
        config = config,
        status = status
    )

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        fun fromLevelState(
            state: GameLevelState,
            customBoard: Boolean = false,
            updatedAtEpochMillis: Long = System.currentTimeMillis()
        ): GameStateSnapshot = GameStateSnapshot(
            level = state.config.levelNumber,
            difficulty = state.config.difficulty,
            board = state.board,
            player = state.player,
            enemies = state.enemies,
            stats = state.stats,
            config = state.config,
            status = state.status,
            customBoard = customBoard,
            updatedAtEpochMillis = updatedAtEpochMillis
        )
    }
}