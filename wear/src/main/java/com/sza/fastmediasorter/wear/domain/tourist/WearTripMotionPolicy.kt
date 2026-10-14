package com.sza.fastmediasorter.wear.domain.tourist

/**
 * S4146: the thresholds that decide "moving", "trusted fix" and the GPS accuracy word for the watch
 * Tourist trip.
 *
 * The twin of `TripMotionPolicy` in `app_v2` (S4145): the two modules share no source, so a value
 * changed on one side is changed on the other in the same edit - otherwise the phone and the watch
 * report different moving time for the same walk.
 */
object WearTripMotionPolicy {

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

    fun isTrustedFix(horizontalAccuracyM: Float?): Boolean =
        horizontalAccuracyM != null && horizontalAccuracyM <= TRUSTED_HORIZONTAL_ACCURACY_M

    fun gpsAccuracyGrade(accuracyM: Float?): WearGpsAccuracyGrade? = when {
        accuracyM == null -> null
        accuracyM <= GPS_ACCURACY_GOOD_M -> WearGpsAccuracyGrade.GOOD
        accuracyM <= GPS_ACCURACY_MEDIUM_M -> WearGpsAccuracyGrade.MEDIUM
        else -> WearGpsAccuracyGrade.POOR
    }
}

enum class WearGpsAccuracyGrade {
    GOOD,
    MEDIUM,
    POOR,
}
