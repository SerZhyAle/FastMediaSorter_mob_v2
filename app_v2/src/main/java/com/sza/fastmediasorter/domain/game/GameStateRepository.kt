package com.sza.fastmediasorter.domain.game

import kotlinx.coroutines.flow.Flow

interface GameStateRepository {
    fun observeSnapshot(): Flow<GameStateSnapshot?>
    suspend fun loadSnapshot(): GameStateSnapshot?
    suspend fun loadOrCreate(config: GameLevelConfig = GameLevelConfig()): GameStateSnapshot
    suspend fun saveSnapshot(snapshot: GameStateSnapshot)
    suspend fun clearSnapshot()

    // S0804: visual mode persisted independently of the level snapshot.
    suspend fun loadMode(): GameMode
    suspend fun saveMode(mode: GameMode)
}