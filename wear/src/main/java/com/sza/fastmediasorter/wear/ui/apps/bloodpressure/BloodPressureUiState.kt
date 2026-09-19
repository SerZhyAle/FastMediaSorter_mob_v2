package com.sza.fastmediasorter.wear.ui.apps.bloodpressure

import com.sza.fastmediasorter.wear.domain.bloodpressure.PulseWaveRejection
import com.sza.fastmediasorter.wear.domain.bodysensor.BodySensorUnavailableReason
import com.sza.fastmediasorter.wear.domain.model.BloodPressureCategory
import com.sza.fastmediasorter.wear.domain.model.BloodPressureHistoryEntry

/**
 * S3113: what the blood-pressure screen is showing at one moment - exactly one of these.
 *
 * Each refusal is its own shape because each has its own sentence (strategic §11 criterion 3).
 */
sealed interface BloodPressureEstimatePhase {

    data object Idle : BloodPressureEstimatePhase

    data class Capturing(val elapsedMillis: Long, val totalMillis: Long) : BloodPressureEstimatePhase

    /** An estimate - never a measurement - with what it rests on, already rounded for display. */
    data class Estimated(
        val systolic: Int,
        val diastolic: Int,
        val pulse: Int,
        val category: BloodPressureCategory,
        val pairCount: Int,
        val newestPairAgeDays: Long,
        val errorSystolic: Int?,
        val errorDiastolic: Int?
    ) : BloodPressureEstimatePhase

    data class NotCalibrated(val pairCount: Int, val requiredPairs: Int) : BloodPressureEstimatePhase

    data class Rejected(val reason: PulseWaveRejection) : BloodPressureEstimatePhase

    data class Unavailable(val reason: BodySensorUnavailableReason) : BloodPressureEstimatePhase
}

/**
 * S3012/S3113: UI state of the blood-pressure screen.
 *
 * @param phase the estimate in progress, its result, or the reason there is none.
 * @param lastReading the newest history row, shown while no estimate is on screen.
 * @param canMeasure whether "measure again" could lead anywhere from [phase].
 */
data class BloodPressureUiState(
    val phase: BloodPressureEstimatePhase = BloodPressureEstimatePhase.Idle,
    val lastReading: BloodPressureHistoryEntry? = null,
    val canMeasure: Boolean = false
)
