package com.sza.fastmediasorter.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room entity for device profile persistence.
 * Single row per device (id = 1 by convention).
 */
@Entity(tableName = "device_profile")
data class DeviceProfileEntity(
    @PrimaryKey
    val id: Int = 1, // Single row per device

    val type: String,                 // DeviceProfileType enum name (e.g., "PERSONAL_SMARTPHONE")
    val source: String,               // DeviceProfileSource enum name
    val confidence: String,           // DetectionConfidence enum name
    val presetVersion: Int,           // Version of preset applied (0 if none)
    val appliedAtInstallTime: Boolean,// Whether preset was applied
    val lastModified: Long            // Unix milliseconds UTC
)

/**
 * Room DAO for device profile table operations.
 */
@Dao
interface DeviceProfileDao {

    /**
     * Get the device profile (single row).
     */
    @Query("SELECT * FROM device_profile WHERE id = 1")
    suspend fun getProfile(): DeviceProfileEntity?

    /**
     * Observe device profile as a Flow.
     */
    @Query("SELECT * FROM device_profile WHERE id = 1")
    fun observeProfile(): Flow<DeviceProfileEntity?>

    /**
     * Insert or replace the device profile.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(entity: DeviceProfileEntity)

    /**
     * Delete the device profile (rarely used - prefer reset with defaults).
     */
    @Delete
    suspend fun deleteProfile(entity: DeviceProfileEntity)
}
