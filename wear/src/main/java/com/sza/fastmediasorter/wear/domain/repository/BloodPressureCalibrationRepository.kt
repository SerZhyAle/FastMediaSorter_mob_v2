package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.bloodpressure.PulseWaveFeatures
import com.sza.fastmediasorter.wear.domain.model.BloodPressureCalibration
import kotlinx.coroutines.flow.Flow

/**
 * S3113: the calibration pairs the blood-pressure estimate is fitted on.
 *
 * Saving a pair also writes the cuff reading into the history as a calibration, in one transaction, so the
 * diary and the model never disagree about which cuff readings exist.
 */
interface BloodPressureCalibrationRepository {

    suspend fun save(systolic: Int, diastolic: Int, features: PulseWaveFeatures, windowFile: String?)

    /** Newest first. */
    fun observeAll(): Flow<List<BloodPressureCalibration>>

    /** Newest first. */
    suspend fun getAll(): List<BloodPressureCalibration>

    /** Removes the pair only; the history row it wrote stays, because the cuff reading itself was real. */
    suspend fun delete(id: Long)
}
