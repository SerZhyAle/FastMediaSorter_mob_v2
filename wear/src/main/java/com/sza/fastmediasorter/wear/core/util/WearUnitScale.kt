package com.sza.fastmediasorter.wear.core.util

/**
 * S3101: the watch's copy of the conversions `UnitScale` owns in `app_v2`
 * (`domain/model/UnitScale.kt`).
 *
 * The two modules share no source, so the coefficients cannot be imported; keeping one copy per module
 * instead of one per composable is what removes the drift risk - every card now reads this object, and
 * a coefficient changed on one side is changed on the other in the same edit.
 */
object WearUnitScale {

    private const val KILOMETRES_PER_MILE = 1.609344
    private const val METRES_PER_KILOMETRE = 1000.0
    private const val METRES_PER_MILE = 1609.344
    private const val METRES_PER_FOOT = 0.3048

    fun kmhToMph(kmh: Double): Double = kmh / KILOMETRES_PER_MILE

    fun metresToFeet(metres: Double): Double = metres / METRES_PER_FOOT

    fun metresToKilometres(metres: Double): Double = metres / METRES_PER_KILOMETRE

    fun metresToMiles(metres: Double): Double = metres / METRES_PER_MILE
}
