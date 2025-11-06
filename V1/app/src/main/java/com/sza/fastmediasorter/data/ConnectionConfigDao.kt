package com.sza.fastmediasorter.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionConfigDao {
    @Query("SELECT * FROM connection_configs ORDER BY lastUsed DESC")
    fun getAllConfigs(): Flow<List<ConnectionConfig>>

    @Query("SELECT * FROM connection_configs WHERE id = :id")
    suspend fun getConfigById(id: Long): ConnectionConfig?

    @Query("SELECT * FROM connection_configs WHERE name = :name LIMIT 1")
    suspend fun getConfigByName(name: String): ConnectionConfig?

    @Query("SELECT * FROM connection_configs WHERE serverAddress = :server AND folderPath = :folder LIMIT 1")
    suspend fun getConfigByFolderAddress(
        server: String,
        folder: String,
    ): ConnectionConfig?

    @Query("SELECT * FROM connection_configs ORDER BY lastUsed DESC LIMIT 1")
    suspend fun getLastUsedConfig(): ConnectionConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: ConnectionConfig): Long

    @Update
    suspend fun updateConfig(config: ConnectionConfig)

    @Delete
    suspend fun deleteConfig(config: ConnectionConfig)

    @Query("DELETE FROM connection_configs WHERE id = :id")
    suspend fun deleteConfigById(id: Long)

    @Query("UPDATE connection_configs SET lastUsed = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(
        id: Long,
        timestamp: Long,
    )

    @Query("UPDATE connection_configs SET interval = :interval WHERE id = :id")
    suspend fun updateConfigInterval(
        id: Long,
        interval: Int,
    )

    @Query("UPDATE connection_configs SET lastSlideshowIndex = :index WHERE id = :id")
    suspend fun updateLastSlideshowIndex(
        id: Long,
        index: Int,
    )

    @Query("UPDATE connection_configs SET writePermission = :hasWritePermission WHERE id = :id")
    suspend fun updateWritePermission(
        id: Long,
        hasWritePermission: Boolean,
    )

    @Query("UPDATE connection_configs SET writePermission = 1 WHERE type = 'SMB' AND writePermission = 0")
    suspend fun fixSmbWritePermissions()

    // Sort destinations queries
    @Query("SELECT * FROM connection_configs WHERE sortOrder IS NOT NULL ORDER BY sortOrder ASC")
    fun getSortDestinations(): Flow<List<ConnectionConfig>>

    @Query("UPDATE connection_configs SET sortOrder = :order, sortName = :name WHERE id = :id")
    suspend fun updateSortDestination(
        id: Long,
        order: Int?,
        name: String?,
    )

    @Query("UPDATE connection_configs SET sortOrder = NULL, sortName = NULL WHERE id = :id")
    suspend fun removeSortDestination(id: Long)

    @Query("SELECT MAX(sortOrder) FROM connection_configs WHERE sortOrder IS NOT NULL")
    suspend fun getMaxSortOrder(): Int?

    // Local folders queries
    @Query("SELECT * FROM connection_configs WHERE type = 'LOCAL_CUSTOM' ORDER BY localDisplayName ASC")
    fun getLocalCustomFolders(): Flow<List<ConnectionConfig>>

    @Query("SELECT * FROM connection_configs WHERE type = 'LOCAL_CUSTOM' AND localDisplayName = :name LIMIT 1")
    suspend fun getLocalFolderByName(name: String): ConnectionConfig?
}
