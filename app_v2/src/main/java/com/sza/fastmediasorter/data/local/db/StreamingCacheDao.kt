package com.sza.fastmediasorter.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [StreamingCacheEntry].
 *
 * All writes go through [upsert] so that re-offloading the same source URI (user starts
 * a second local playback) replaces the old row instead of orphaning a stale file path.
 */
@Dao
interface StreamingCacheDao {

    @Query("SELECT * FROM streaming_cache_entries WHERE resourceHash = :resourceHash LIMIT 1")
    suspend fun findByHash(resourceHash: String): StreamingCacheEntry?

    @Query("SELECT * FROM streaming_cache_entries ORDER BY lastPlayedAt DESC")
    suspend fun getAll(): List<StreamingCacheEntry>

    @Query("SELECT * FROM streaming_cache_entries ORDER BY lastPlayedAt DESC")
    fun observeAll(): Flow<List<StreamingCacheEntry>>

    @Query("SELECT * FROM streaming_cache_entries WHERE lastPlayedAt < :threshold")
    suspend fun findOlderThan(threshold: Long): List<StreamingCacheEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: StreamingCacheEntry)

    @Query("UPDATE streaming_cache_entries SET lastPlayedAt = :now WHERE resourceHash = :resourceHash")
    suspend fun touchLastPlayedAt(resourceHash: String, now: Long)

    @Query("DELETE FROM streaming_cache_entries WHERE resourceHash = :resourceHash")
    suspend fun deleteByHash(resourceHash: String)

    @Query("DELETE FROM streaming_cache_entries")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM streaming_cache_entries")
    suspend fun totalSizeBytes(): Long
}
