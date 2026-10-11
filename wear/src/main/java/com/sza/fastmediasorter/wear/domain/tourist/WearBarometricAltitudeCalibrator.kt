package com.sza.fastmediasorter.wear.domain.tourist

import kotlin.math.pow

/**
 * S4146: altitude from the barometer, anchored so it does not drift by hundreds of metres. The twin of
 * `BarometricAltitudeCalibrator` in `app_v2` (S4145).
 *
 * Sea-level pressure is the best anchor; a fix with a tight vertical accuracy is the next one and is
 * remembered, so the reading stays anchored after the fix is lost. A height over the WGS84 ellipsoid
 * never anchors: below API 34 the watch reports only that one, and it differs from height above sea
 * level by tens of metres. With no anchor the standard atmosphere is used and the value is approximate.
 */
class WearBarometricAltitudeCalibrator {

    private var gpsOffsetMeters: Double? = null

    fun altitude(
        pressureHpa: Float,
        seaLevelHpa: Double?,
        gpsAltitudeM: Double?,
        gpsVerticalAccuracyM: Float?,
        gpsAltitudeIsEllipsoid: Boolean,
    ): WearBaroAltitude {
        if (seaLevelHpa != null && seaLevelHpa > 0.0) {
            return WearBaroAltitude(altitudeFor(pressureHpa.toDouble(), seaLevelHpa), approximate = false)
        }
        val standard = altitudeFor(pressureHpa.toDouble(), STANDARD_SEA_LEVEL_HPA)
        val trustedFix = !gpsAltitudeIsEllipsoid &&
            gpsAltitudeM != null &&
            gpsVerticalAccuracyM != null &&
            gpsVerticalAccuracyM <= WearTripMotionPolicy.TRUSTED_VERTICAL_ACCURACY_M
        if (trustedFix) {
            gpsOffsetMeters = requireNotNull(gpsAltitudeM) - standard
        }
        val offset = gpsOffsetMeters
        return if (offset != null) {
            WearBaroAltitude(standard + offset, approximate = false)
        } else {
            WearBaroAltitude(standard, approximate = true)
        }
    }

    private fun altitudeFor(pressureHpa: Double, seaLevelHpa: Double): Double =
        FORMULA_SCALE_M * (1.0 - (pressureHpa / seaLevelHpa).pow(1.0 / FORMULA_EXPONENT))

    private companion object {
        const val STANDARD_SEA_LEVEL_HPA = 1013.25
        const val FORMULA_SCALE_M = 44_330.0
        const val FORMULA_EXPONENT = 5.255
    }
}

data class WearBaroAltitude(
    val metres: Double,
    val approximate: Boolean,
)
