package com.sza.fastmediasorter.wear.domain.model

import com.sza.fastmediasorter.wear.domain.bloodpressure.PulseWaveFeatures

/**
 * S3113: one calibration pair - a cuff reading and the pulse wave captured on the watch at the same time.
 *
 * [windowFile] names the archived raw window the features were extracted from, so an improved algorithm
 * can refit on the owner's existing cuff readings; null when the archive write failed.
 */
data class BloodPressureCalibration(
    val id: Long,
    val systolic: Int,
    val diastolic: Int,
    val timestampMillis: Long,
    val windowFile: String?,
    val features: PulseWaveFeatures
)
