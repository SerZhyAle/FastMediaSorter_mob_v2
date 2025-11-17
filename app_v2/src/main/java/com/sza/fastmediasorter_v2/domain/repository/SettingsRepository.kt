package com.sza.fastmediasorter_v2.domain.repository

import com.sza.fastmediasorter_v2.domain.model.AppSettings
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
}
