package com.sza.fastmediasorter.data.local.db

import androidx.room.*

@Dao
interface CachedFileListDao {

    /** Returns the single cached snapshot for a resource, or null if none. */
    @Query("SELECT * FROM cached_file_lists WHERE resourceId = :resourceId LIMIT 1")
    suspend fun getByResourceId(resourceId: Long): CachedFileListEntity?

    /**
     * Insert or replace the cached snapshot for a resource.
     * REPLACE strategy handles the uniqueness constraint on resourceId.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: CachedFileListEntity)

    @Query("DELETE FROM cached_file_lists WHERE resourceId = :resourceId")
    suspend fun deleteByResourceId(resourceId: Long)

    @Query("DELETE FROM cached_file_lists")
    suspend fun deleteAll()

    @Query("SELECT file_count FROM cached_file_lists WHERE resourceId = :resourceId LIMIT 1")
    suspend fun getFileCount(resourceId: Long): Int?
}

