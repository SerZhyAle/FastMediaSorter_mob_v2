package com.sza.fastmediasorter.wear.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sza.fastmediasorter.wear.domain.bloodpressure.PulseWaveFeatures
import com.sza.fastmediasorter.wear.domain.model.BloodPressureCalibration

/**
 * S3113: the stored shape of one calibration pair.
 *
 * One column per feature rather than a serialised blob, so a feature can be queried directly and a refit
 * after an algorithm change needs no parser for an old format.
 */
@Entity(tableName = "blood_pressure_calibration")
data class BloodPressureCalibrationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val systolic: Int,
    val diastolic: Int,
    val timestampMillis: Long,
    val windowFile: String?,
    val heartRateBpm: Double,
    val upstrokeSeconds: Double,
    val width50Seconds: Double,
    val perfusionIndex: Double,
    val acceptedBeats: Int
)

fun BloodPressureCalibrationEntity.toDomain(): BloodPressureCalibration = BloodPressureCalibration(
    id = id,
    systolic = systolic,
    diastolic = diastolic,
    timestampMillis = timestampMillis,
    windowFile = windowFile,
    features = PulseWaveFeatures(
        heartRateBpm = heartRateBpm,
        systolicUpstrokeSeconds = upstrokeSeconds,
        pulseWidth50Seconds = width50Seconds,
        perfusionIndex = perfusionIndex,
        acceptedBeats = acceptedBeats
    )
)
