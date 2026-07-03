package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.ui.player.model.TouchZoneHintType
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for application settings
 */
interface SettingsRepository {
    fun getSettings(): Flow<AppSettings>
    suspend fun updateSettings(settings: AppSettings)
    /**
     * S0876: serialized read-modify-write. All callers competing for the same window (e.g. the
     * Welcome enable-all fan-out's concurrent deliverable-install writers) must go through this
     * overload instead of their own `getSettings().first()` + `updateSettings(...)` pair - the
     * implementation guards the whole read+transform+write with a single mutex so a later writer
     * always folds onto the previous writer's committed result instead of a stale pre-read snapshot.
     */
    suspend fun updateSettings(transform: suspend (AppSettings) -> AppSettings)
    suspend fun resetToDefaults()
    suspend fun setPlayerFirstRun(isFirstRun: Boolean)
    suspend fun isPlayerFirstRun(): Boolean
    suspend fun saveLastUsedResourceId(resourceId: Long)
    suspend fun getLastUsedResourceId(): Long
    suspend fun setResourceGridMode(isGridMode: Boolean)
    suspend fun updateEmbeddedGameEnabled(enabled: Boolean)
    suspend fun updateScheduledOperationsPaused(paused: Boolean)
    /** S0473: persists the opt-in statistics flag (also surfaced in [getSettings]). */
    suspend fun setStatisticsEnabled(enabled: Boolean)
    suspend fun isTouchZoneHintShown(type: TouchZoneHintType): Boolean
    suspend fun setTouchZoneHintShown(type: TouchZoneHintType, shown: Boolean)
    suspend fun resetAllTouchZoneHints()
}
