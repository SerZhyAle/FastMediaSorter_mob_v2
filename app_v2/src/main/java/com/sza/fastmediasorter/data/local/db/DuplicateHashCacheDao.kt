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

    /**
     * S1305: TTL cleanup. Rows keyed by (path, lastModified, fileSize) are never revisited once a
     * file is edited or deleted, so without this the table only ever grew - one dead row per edited
     * or removed file, for the life of the install. Mirrors [FileMetadataCacheDao.deleteExpired].
     */
    @Query("DELETE FROM duplicate_hash_cache WHERE cachedAt < :olderThanMs")
    suspend fun deleteExpired(olderThanMs: Long): Int

    /** S1305: drop rows whose parent resource is gone (FK cascade safety net). */
    @Query("DELETE FROM duplicate_hash_cache WHERE resourceId NOT IN (SELECT id FROM resources)")
    suspend fun deleteOrphaned(): Int
}
