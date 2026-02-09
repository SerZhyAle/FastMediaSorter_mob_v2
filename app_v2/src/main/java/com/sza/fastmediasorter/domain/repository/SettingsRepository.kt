package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.AppSettings
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
}
