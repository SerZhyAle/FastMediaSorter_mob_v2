package com.sza.fastmediasorter.wear.data.repository

import androidx.room.withTransaction
import com.sza.fastmediasorter.wear.data.db.BloodPressureCalibrationEntity
import com.sza.fastmediasorter.wear.data.db.BloodPressureHistoryEntity
import com.sza.fastmediasorter.wear.data.db.WearBloodPressureDatabase
import com.sza.fastmediasorter.wear.data.db.toDomain
import com.sza.fastmediasorter.wear.domain.bloodpressure.PulseWaveFeatures
import com.sza.fastmediasorter.wear.domain.model.BloodPressureCalibration
import com.sza.fastmediasorter.wear.domain.model.BloodPressureSource
import com.sza.fastmediasorter.wear.domain.repository.BloodPressureCalibrationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.math.roundToInt

/** S3113: Room-backed [BloodPressureCalibrationRepository]. */
class BloodPressureCalibrationRepositoryImpl @Inject constructor(
    private val database: WearBloodPressureDatabase
) : BloodPressureCalibrationRepository {

    private val calibrationDao = database.bloodPressureCalibrationDao()
    private val historyDao = database.bloodPressureHistoryDao()

    override suspend fun save(systolic: Int, diastolic: Int, features: PulseWaveFeatures, windowFile: String?) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            calibrationDao.insert(
                BloodPressureCalibrationEntity(
                    systolic = systolic,
                    diastolic = diastolic,
                    timestampMillis = now,
                    windowFile = windowFile,
                    heartRateBpm = features.heartRateBpm,
                    upstrokeSeconds = features.systolicUpstrokeSeconds,
                    width50Seconds = features.pulseWidth50Seconds,
                    perfusionIndex = features.perfusionIndex,
                    acceptedBeats = features.acceptedBeats
                )
            )
            historyDao.insert(
                BloodPressureHistoryEntity(
                    systolic = systolic,
                    diastolic = diastolic,
                    timestampMillis = now,
                    source = BloodPressureSource.CALIBRATION.name,
                    pulse = features.heartRateBpm.roundToInt()
                )
            )
        }
    }

    override fun observeAll(): Flow<List<BloodPressureCalibration>> =
        calibrationDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getAll(): List<BloodPressureCalibration> = calibrationDao.getAll().map { it.toDomain() }

    override suspend fun delete(id: Long) {
        calibrationDao.deleteById(id)
    }
}
