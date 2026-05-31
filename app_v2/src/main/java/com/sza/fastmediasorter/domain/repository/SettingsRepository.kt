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
    suspend fun resetToDefaults()
    suspend fun setPlayerFirstRun(isFirstRun: Boolean)
    suspend fun isPlayerFirstRun(): Boolean
    suspend fun saveLastUsedResourceId(resourceId: Long)
    suspend fun getLastUsedResourceId(): Long
    suspend fun setResourceGridMode(isGridMode: Boolean)
    suspend fun updateEmbeddedGameEnabled(enabled: Boolean)
    suspend fun isTouchZoneHintShown(type: TouchZoneHintType): Boolean
    suspend fun setTouchZoneHintShown(type: TouchZoneHintType, shown: Boolean)
    suspend fun resetAllTouchZoneHints()
}
