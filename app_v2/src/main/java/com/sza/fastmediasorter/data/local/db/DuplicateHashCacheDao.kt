package com.sza.fastmediasorter.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DuplicateHashCacheDao {

    @Query(
        "SELECT * FROM duplicate_hash_cache " +
        "WHERE resourceId = :resourceId AND filePath = :filePath " +
        "AND lastModified = :lastModified AND fileSize = :fileSize " +
        "LIMIT 1"
    )
    suspend fun getByKey(
        resourceId: Long,
        filePath: String,
        lastModified: Long,
        fileSize: Long
    ): DuplicateHashCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DuplicateHashCacheEntity)

    @Query("DELETE FROM duplicate_hash_cache WHERE resourceId = :resourceId")
    suspend fun deleteByResourceId(resourceId: Long)

    @Query(
        "DELETE FROM duplicate_hash_cache " +
        "WHERE resourceId = :resourceId AND filePath = :filePath"
    )
    suspend fun deleteByResourceAndPath(resourceId: Long, filePath: String)
}
