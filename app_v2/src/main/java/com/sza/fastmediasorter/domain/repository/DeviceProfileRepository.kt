package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.data.model.DeviceProfile
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.data.model.DetectorSignal
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for device profile persistence and state management.
 * Implementations read/write to Room database and expose reactive Flows.
 */
interface DeviceProfileRepository {

    /**
     * Observe current device profile as a Flow.
     * Emits the active profile whenever it changes in the database.
     */
    fun getCurrentProfile(): Flow<DeviceProfile>

    /**
     * Save or update the device profile in persistent storage.
     * Returns Result.success on commit, Result.failure on error.
     */
    suspend fun saveProfile(profile: DeviceProfile): Result<Unit>

    /**
     * Mark a preset as applied and update the preset version.
     * Called after profile selection and batch settings apply in Welcome or Settings.
     */
    suspend fun updatePresetApplied(presetVersion: Int): Result<Unit>

    /**
     * Reset profile to a fallback (e.g., when detection is low-confidence).
     * Clears applied flag and sets new type.
     */
    suspend fun resetToDefault(fallbackProfile: DeviceProfileType): Result<Unit>

    /**
     * Retrieve detection history signals (for audit/diagnostics).
     * Optional - implementations may return empty list.
     */
    fun getDetectionHistory(): Flow<List<DetectorSignal>>

    /**
     * Check if active profile was created via migration of an existing install.
     */
    suspend fun isMigrationExisting(): Boolean
}
