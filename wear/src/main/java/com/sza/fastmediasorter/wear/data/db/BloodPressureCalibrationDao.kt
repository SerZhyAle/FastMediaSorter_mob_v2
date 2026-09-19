package com.sza.fastmediasorter.wear.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** S3113: the calibration pairs. Newest first, matching the calibration screen's list. */
@Dao
interface BloodPressureCalibrationDao {

    @Query("SELECT * FROM blood_pressure_calibration ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<BloodPressureCalibrationEntity>>

    @Query("SELECT * FROM blood_pressure_calibration ORDER BY timestampMillis DESC")
    suspend fun getAll(): List<BloodPressureCalibrationEntity>

    @Insert
    suspend fun insert(entry: BloodPressureCalibrationEntity): Long

    @Query("DELETE FROM blood_pressure_calibration WHERE id = :id")
    suspend fun deleteById(id: Long)
}
