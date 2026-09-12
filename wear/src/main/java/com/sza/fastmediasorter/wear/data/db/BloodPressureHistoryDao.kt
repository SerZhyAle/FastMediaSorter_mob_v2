package com.sza.fastmediasorter.wear.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * S2809: the blood pressure history store. Newest first, matching the screen ordering.
 *
 * No DAO method carries a Kotlin default argument - Room generates the override and the
 * defaulted call would route through a synthetic bridge for nothing, matching HeartRateHistoryDao.
 */
@Dao
interface BloodPressureHistoryDao {

    /** Newest first: the measurement that was just taken belongs at the top of the list. */
    @Query("SELECT * FROM blood_pressure_history ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<BloodPressureHistoryEntity>>

    @Insert
    suspend fun insert(entry: BloodPressureHistoryEntity): Long

    @Query("DELETE FROM blood_pressure_history")
    suspend fun deleteAll()
}
