package com.sza.fastmediasorter.data.datasource

import com.sza.fastmediasorter.data.local.db.DeviceProfileDao
import com.sza.fastmediasorter.data.local.db.DeviceProfileEntity
import com.sza.fastmediasorter.data.model.DeviceProfile
import com.sza.fastmediasorter.data.model.DeviceProfileSource
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.data.model.DetectionConfidence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local data source for device profile persistence.
 * Wraps DeviceProfileDao and handles entity ↔ domain model mapping.
 */
@Singleton
class DeviceProfileLocalDataSource @Inject constructor(
    private val dao: DeviceProfileDao
) {

    /**
     * Observe device profile as a Flow.
     * Returns null if no profile exists (fresh install).
     */
    fun observeProfile(): Flow<DeviceProfile?> {
        return dao.observeProfile().map { entity ->
            entity?.toDomain()
        }
    }

    /**
     * Save or replace device profile.
     */
    suspend fun saveProfile(profile: DeviceProfile) {
        val entity = profile.toEntity()
        dao.upsertProfile(entity)
    }

    /**
     * Delete device profile (rarely used).
     */
    suspend fun deleteProfile() {
        val entity = DeviceProfileEntity(
            type = DeviceProfileType.OTHER.name,
            source = DeviceProfileSource.MIGRATION_EXISTING.name,
            confidence = DetectionConfidence.NONE.name,
            presetVersion = 0,
            appliedAtInstallTime = false,
            lastModified = System.currentTimeMillis()
        )
        dao.deleteProfile(entity)
    }

    /**
     * Entity → Domain mapping.
     */
    private fun DeviceProfileEntity.toDomain(): DeviceProfile {
        return DeviceProfile(
            type = DeviceProfileType.valueOf(type),
            source = DeviceProfileSource.valueOf(source),
            confidence = DetectionConfidence.valueOf(confidence),
            presetVersion = presetVersion,
            appliedAtInstallTime = appliedAtInstallTime,
            lastModified = lastModified
        )
    }

    /**
     * Domain → Entity mapping.
     */
    private fun DeviceProfile.toEntity(): DeviceProfileEntity {
        return DeviceProfileEntity(
            id = 1,  // Single row by convention
            type = type.name,
            source = source.name,
            confidence = confidence.name,
            presetVersion = presetVersion,
            appliedAtInstallTime = appliedAtInstallTime,
            lastModified = lastModified
        )
    }
}
