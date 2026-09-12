package com.sza.fastmediasorter.wear.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sza.fastmediasorter.wear.domain.model.BloodPressureHistoryEntry

/**
 * S2809: the stored shape of one completed blood pressure measurement.
 *
 * One row per measurement: systolic and diastolic values in mmHg, with the wall-clock
 * timestamp of the save. No enum or type converter is needed because both stored values
 * are integers.
 */
@Entity(tableName = "blood_pressure_history")
data class BloodPressureHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val systolic: Int,
    val diastolic: Int,
    val timestampMillis: Long
)

fun BloodPressureHistoryEntity.toDomain(): BloodPressureHistoryEntry = BloodPressureHistoryEntry(
    id = id,
    systolic = systolic,
    diastolic = diastolic,
    timestampMillis = timestampMillis
)
