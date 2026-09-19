package com.sza.fastmediasorter.wear.ui.apps.bloodpressure

import androidx.annotation.StringRes
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.bloodpressure.PulseWaveRejection
import com.sza.fastmediasorter.wear.domain.bodysensor.BodySensorUnavailableReason
import com.sza.fastmediasorter.wear.domain.model.BloodPressureSource

/**
 * S3113: the sentence for a pulse-wave source refusal, shared by the estimate and calibration screens.
 *
 * Reuses the heart-rate sentences where they are true for the pulse wave as well - the permission is
 * literally the heart-rate one - and says something of its own only for the missing sensor, because a
 * watch without the raw type still has a heart-rate sensor and "no heart-rate sensor" would be false.
 */
@StringRes
internal fun captureReasonText(reason: BodySensorUnavailableReason): Int = when (reason) {
    BodySensorUnavailableReason.NOT_OFFERED_IN_THIS_BUILD -> R.string.body_sensor_reason_not_offered_in_this_build
    BodySensorUnavailableReason.PLATFORM_TOO_OLD -> R.string.body_sensor_reason_platform_too_old
    BodySensorUnavailableReason.NO_HARDWARE -> R.string.blood_pressure_reason_no_raw_sensor
    BodySensorUnavailableReason.PERMISSION_DENIED -> R.string.body_sensor_reason_permission_denied
    BodySensorUnavailableReason.SENSOR_OFF_BODY -> R.string.body_sensor_reason_sensor_off_body
    BodySensorUnavailableReason.MEASUREMENT_TIMED_OUT -> R.string.body_sensor_reason_measurement_timed_out
    BodySensorUnavailableReason.MEASUREMENT_FAILED -> R.string.body_sensor_reason_measurement_failed
}

/** S3113: the sentence for a window the quality rules refused. */
@StringRes
internal fun rejectionText(reason: PulseWaveRejection): Int = when (reason) {
    PulseWaveRejection.MOTION -> R.string.blood_pressure_reject_motion
    PulseWaveRejection.WEAK_SIGNAL -> R.string.blood_pressure_reject_weak_signal
    PulseWaveRejection.TOO_FEW_BEATS -> R.string.blood_pressure_reject_too_few_beats
}

/** S3113: the text label of a history row's origin, so the source never rests on colour alone. */
@StringRes
internal fun sourceText(source: BloodPressureSource): Int = when (source) {
    BloodPressureSource.MANUAL -> R.string.blood_pressure_source_manual
    BloodPressureSource.CALIBRATION -> R.string.blood_pressure_source_calibration
    BloodPressureSource.ESTIMATE -> R.string.blood_pressure_estimate_label
}
