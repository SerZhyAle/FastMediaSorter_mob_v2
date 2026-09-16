package com.sza.fastmediasorter.wear.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sza.fastmediasorter.wear.domain.model.BloodPressureHistoryEntry
import com.sza.fastmediasorter.wear.domain.model.BloodPressureSource

/**
 * S2809: the stored shape of one completed blood pressure measurement.
 *
 * One row per measurement: systolic and diastolic values in mmHg, with the wall-clock
 * timestamp of the save. No enum or type converter is needed because both stored values
 * are integers.
 *
 * S3113: [source] is stored as the [BloodPressureSource] name. Its default matches the one the version 2
 * migration gives every existing row, which Room checks when it validates the migrated schema. [pulse] is
 * the heart rate of the same reading (version 3) - a cuff shows three numbers, and a row typed before the
 * watch measured pulse has none.
 */
@Entity(tableName = "blood_pressure_history")
data class BloodPressureHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val systolic: Int,
    val diastolic: Int,
    val timestampMillis: Long,
    @ColumnInfo(defaultValue = "MANUAL")
    val source: String = BloodPressureSource.MANUAL.name,
    val pulse: Int? = null
)

fun BloodPressureHistoryEntity.toDomain(): BloodPressureHistoryEntry = BloodPressureHistoryEntry(
    id = id,
    systolic = systolic,
    diastolic = diastolic,
    timestampMillis = timestampMillis,
    source = BloodPressureSource.fromStored(source),
    pulse = pulse
)
