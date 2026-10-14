package com.sza.fastmediasorter.domain.tourist

import com.sza.fastmediasorter.domain.model.tourist.TripMotionPolicy
import kotlin.math.pow

/**
 * S4145: altitude from the barometer, anchored so it does not drift by hundreds of metres.
 *
 * The provider's sea-level pressure is the best anchor; a fix with a tight vertical accuracy is the
 * next one and is remembered, so the reading stays anchored indoors after the fix is lost. With
 * neither, the standard atmosphere is used and the value is flagged approximate.
 */
class BarometricAltitudeCalibrator {

    private var gpsOffsetMeters: Double? = null

    fun altitude(
        pressureHpa: Float,
        seaLevelHpa: Double?,
        gpsAltitudeM: Double?,
        gpsVerticalAccuracyM: Float?,
    ): BaroAltitude {
        if (seaLevelHpa != null && seaLevelHpa > 0.0) {
            return BaroAltitude(altitudeFor(pressureHpa.toDouble(), seaLevelHpa), approximate = false)
        }
        val standard = altitudeFor(pressureHpa.toDouble(), STANDARD_SEA_LEVEL_HPA)
        val trustedFix = gpsAltitudeM != null &&
            gpsVerticalAccuracyM != null &&
            gpsVerticalAccuracyM <= TripMotionPolicy.TRUSTED_VERTICAL_ACCURACY_M
        if (trustedFix) {
            gpsOffsetMeters = requireNotNull(gpsAltitudeM) - standard
        }
        val offset = gpsOffsetMeters
        return if (offset != null) {
            BaroAltitude(standard + offset, approximate = false)
        } else {
            BaroAltitude(standard, approximate = true)
        }
    }

    private fun altitudeFor(pressureHpa: Double, seaLevelHpa: Double): Double =
        FORMULA_SCALE_M * (1.0 - (pressureHpa / seaLevelHpa).pow(1.0 / FORMULA_EXPONENT))

    data class BaroAltitude(
        val metres: Double,
        val approximate: Boolean,
    )

    private companion object {
        const val STANDARD_SEA_LEVEL_HPA = 1013.25
        const val FORMULA_SCALE_M = 44_330.0
        const val FORMULA_EXPONENT = 5.255
    }
}
