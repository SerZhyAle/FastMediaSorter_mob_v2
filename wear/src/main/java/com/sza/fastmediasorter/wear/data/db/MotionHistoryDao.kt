package com.sza.fastmediasorter.wear.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * S3014: Data access object for physical activity and step history records.
 */
@Dao
interface MotionHistoryDao {

    /** Newest first: the most recent measurement snapshot is displayed first. */
    @Query("SELECT * FROM motion_history ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<MotionHistoryEntity>>

    @Insert
    suspend fun insert(entry: MotionHistoryEntity): Long

    @Query("DELETE FROM motion_history")
    suspend fun deleteAll()
}
