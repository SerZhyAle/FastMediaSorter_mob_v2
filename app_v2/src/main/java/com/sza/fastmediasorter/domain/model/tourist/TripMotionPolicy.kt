package com.sza.fastmediasorter.domain.model.tourist

/**
 * S4145: the thresholds that decide "moving", "trusted fix" and the GPS accuracy word for the Tourist
 * trip.
 *
 * One rule for the phone and the watch: S4146 mirrors these values in the wear module, so a change here
 * is a change there - otherwise the two devices report different moving time for the same walk.
 */
object TripMotionPolicy {

    const val MOVING_MIN_SPEED_KMH: Float = 1.8f
    const val TRUSTED_HORIZONTAL_ACCURACY_M: Float = 50f
    const val GPS_ACCURACY_GOOD_M: Float = 10f
    const val GPS_ACCURACY_MEDIUM_M: Float = 30f
    const val TRUSTED_VERTICAL_ACCURACY_M: Float = 10f

    /** Two samples further apart than this are a break, never a straight line across the gap. */
    const val SAMPLE_GAP_MS: Long = 30_000L

    /** A fix with no reported accuracy is not trusted: nothing proves it is not a cell-tower guess. */
    fun isMoving(speedKmh: Float?, horizontalAccuracyM: Float?): Boolean =
        speedKmh != null &&
            horizontalAccuracyM != null &&
            speedKmh >= MOVING_MIN_SPEED_KMH &&
            horizontalAccuracyM <= TRUSTED_HORIZONTAL_ACCURACY_M

    fun gpsAccuracyGrade(accuracyM: Float?): GpsAccuracyGrade? = when {
        accuracyM == null -> null
        accuracyM <= GPS_ACCURACY_GOOD_M -> GpsAccuracyGrade.GOOD
        accuracyM <= GPS_ACCURACY_MEDIUM_M -> GpsAccuracyGrade.MEDIUM
        else -> GpsAccuracyGrade.POOR
    }
}

enum class GpsAccuracyGrade {
    GOOD,
    MEDIUM,
    POOR,
}
