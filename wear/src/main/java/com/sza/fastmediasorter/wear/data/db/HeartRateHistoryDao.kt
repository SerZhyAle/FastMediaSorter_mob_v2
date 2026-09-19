package com.sza.fastmediasorter.wear.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * S2808: the heart-rate history store. Newest first, matching the screen ordering.
 *
 * No DAO method carries a Kotlin default argument - Room generates the override and the
 * defaulted call would route through a synthetic bridge for nothing, matching VoiceNoteDao.
 */
@Dao
interface HeartRateHistoryDao {

    /** Newest first: the measurement that was just taken belongs at the top of the list. */
    @Query("SELECT * FROM heart_rate_history ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<HeartRateHistoryEntity>>

    @Insert
    suspend fun insert(entry: HeartRateHistoryEntity): Long

    /**
     * S3112: keeps the [limit] newest rows and deletes the rest. A live session appends a row every
     * minute, so without a cap the table would grow for as long as the watch keeps the app installed.
     */
    @Query(
        "DELETE FROM heart_rate_history WHERE id NOT IN (" +
            "SELECT id FROM heart_rate_history ORDER BY timestampMillis DESC LIMIT :limit)"
    )
    suspend fun trimToNewest(limit: Int)

    @Query("DELETE FROM heart_rate_history")
    suspend fun deleteAll()
}
