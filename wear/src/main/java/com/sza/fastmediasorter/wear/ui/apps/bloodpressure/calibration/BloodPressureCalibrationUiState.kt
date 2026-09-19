package com.sza.fastmediasorter.wear.ui.apps.bloodpressure.calibration

import androidx.annotation.StringRes
import com.sza.fastmediasorter.wear.domain.bloodpressure.PulseWaveFeatures
import com.sza.fastmediasorter.wear.domain.bloodpressure.PulseWaveRejection
import com.sza.fastmediasorter.wear.domain.bodysensor.BodySensorUnavailableReason
import com.sza.fastmediasorter.wear.domain.model.BloodPressureCalibration

private const val DEFAULT_SYSTOLIC = 120
private const val DEFAULT_DIASTOLIC = 80

/** S3113: the pulse-wave window running under the cuff reading. */
sealed interface CalibrationWindowState {

    data class Capturing(val elapsedMillis: Long, val totalMillis: Long) : CalibrationWindowState

    /** Accepted by the quality rules and archived as [fileName] - null when the archive write failed. */
    data class Ready(val fileName: String?, val features: PulseWaveFeatures) : CalibrationWindowState

    data class Rejected(val reason: PulseWaveRejection) : CalibrationWindowState

    data class Unavailable(val reason: BodySensorUnavailableReason) : CalibrationWindowState
}

/**
 * S3113: UI state of the calibration screen.
 *
 * @param window the window under the cuff reading, or null before one was started and after a save.
 * @param canRecord whether starting a new window could lead anywhere from [window].
 * @param canSave the typed values are valid and an accepted window is waiting for them.
 * @param justSaved a pair was stored since the last window started.
 */
data class BloodPressureCalibrationUiState(
    val systolicInput: String = DEFAULT_SYSTOLIC.toString(),
    val diastolicInput: String = DEFAULT_DIASTOLIC.toString(),
    @StringRes val errorMessageRes: Int? = null,
    val window: CalibrationWindowState? = null,
    val canRecord: Boolean = false,
    val canSave: Boolean = false,
    val justSaved: Boolean = false,
    val pairs: List<BloodPressureCalibration> = emptyList()
)
