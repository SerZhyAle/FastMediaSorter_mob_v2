package com.sza.fastmediasorter.wear.domain.tourist

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** S4152: a position as sent to the phone, already rounded. */
data class WearWeatherPosition(val latitude: Double, val longitude: Double)

/**
 * S4152: when Tourist asks the phone for the weather, and when an answer is too old to show.
 *
 * The phone caches a reading for twenty minutes, so asking more often than [REQUEST_INTERVAL_MS] would only
 * re-read its cache; a walk past [MOVE_THRESHOLD_METRES] reaches a different cache cell and asks at once.
 * The constants are the spec author's, not an owner ruling (S4152 section 3.3).
 */
object WearTouristWeatherRequestPolicy {

    const val REQUEST_INTERVAL_MS = 15L * 60L * 1000L
    const val MOVE_THRESHOLD_METRES = 2_000.0
    const val MAX_AGE_MS = 60L * 60L * 1000L

    private const val ROUNDING_SCALE = 100.0
    private const val EARTH_RADIUS_METRES = 6_371_000.0

    /** Two decimals, about a kilometre: the precision of the phone's weather cache key, and no finer. */
    fun round(latitude: Double, longitude: Double): WearWeatherPosition = WearWeatherPosition(
        latitude = (latitude * ROUNDING_SCALE).roundToLong() / ROUNDING_SCALE,
        longitude = (longitude * ROUNDING_SCALE).roundToLong() / ROUNDING_SCALE,
    )

    fun shouldRequest(
        lastPosition: WearWeatherPosition?,
        lastRequestAtMs: Long?,
        position: WearWeatherPosition,
        nowMs: Long,
    ): Boolean {
        if (lastPosition == null || lastRequestAtMs == null) return true
        val intervalElapsed = nowMs - lastRequestAtMs >= REQUEST_INTERVAL_MS
        return intervalElapsed || distanceMetres(lastPosition, position) > MOVE_THRESHOLD_METRES
    }

    /** An answer observed more than [MAX_AGE_MS] ago describes another hour's sky, so it is not shown. */
    fun isFresh(observedAtMs: Long, nowMs: Long): Boolean = nowMs - observedAtMs <= MAX_AGE_MS

    private fun distanceMetres(a: WearWeatherPosition, b: WearWeatherPosition): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METRES * asin(sqrt(h))
    }
}
