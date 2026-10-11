package com.sza.fastmediasorter.domain.tourist

import com.sza.fastmediasorter.domain.model.tourist.ElevationSource
import com.sza.fastmediasorter.domain.model.tourist.TouristTripSnapshot
import com.sza.fastmediasorter.domain.model.tourist.TripMotionPolicy
import kotlin.math.abs

/**
 * S4145: pure trip arithmetic over [TouristTripSnapshot] - no clock, no storage, no Android.
 *
 * Distance and moving time grow only between two samples close enough in time to be one walk: a gap
 * (the dashboard was closed) is a break, never a straight line across it. Gain and loss move only past
 * a hysteresis band, so a parked phone's altitude noise is not counted as climbing.
 */
class TripAccumulator {

    fun onMotion(
        snapshot: TouristTripSnapshot,
        speedKmh: Float?,
        distanceDeltaMeters: Double,
        horizontalAccuracyM: Float?,
        atMillis: Long,
    ): TouristTripSnapshot {
        val previous = snapshot.lastSampleAtMillis
        val continuous = previous != null && atMillis - previous in 0..TripMotionPolicy.SAMPLE_GAP_MS
        val counts = continuous && TripMotionPolicy.isMoving(speedKmh, horizontalAccuracyM)
        return snapshot.copy(
            startedAtMillis = snapshot.startedAtMillis ?: atMillis,
            lastSampleAtMillis = atMillis,
            movingMillis = snapshot.movingMillis + if (counts) atMillis - requireNotNull(previous) else 0L,
            distanceMeters = snapshot.distanceMeters + if (counts) distanceDeltaMeters.coerceAtLeast(0.0) else 0.0,
            maxSpeedKmh = maxOf(snapshot.maxSpeedKmh, speedKmh ?: 0f),
        )
    }

    /**
     * Once the barometer has spoken it owns the trip's elevation: mixing in GPS altitude would add the
     * offset between the two sources as a climb.
     */
    fun onElevation(snapshot: TouristTripSnapshot, metres: Double, source: ElevationSource): TouristTripSnapshot {
        val anchor = snapshot.lastElevationMeters
        return when {
            source == ElevationSource.GPS && snapshot.elevationSource == ElevationSource.BAROMETER -> snapshot
            anchor == null || snapshot.elevationSource != source ->
                snapshot.copy(elevationSource = source, lastElevationMeters = metres)
            else -> accumulate(snapshot, anchor, metres, hysteresisFor(source))
        }
    }

    fun reset(snapshot: TouristTripSnapshot): TouristTripSnapshot =
        TouristTripSnapshot.EMPTY.copy(pressureSamples = snapshot.pressureSamples)

    /** Window of (cumulative distance, elevation) pairs, oldest first; null until it spans enough ground. */
    fun slopePercent(window: List<Pair<Double, Double>>): Float? {
        val first = window.firstOrNull()
        val last = window.lastOrNull()
        val run = if (first != null && last != null) last.first - first.first else 0.0
        return if (first == null || last == null || run < MIN_SLOPE_RUN_M) {
            null
        } else {
            ((last.second - first.second) / run * PERCENT).toFloat()
        }
    }

    private fun accumulate(
        snapshot: TouristTripSnapshot,
        anchor: Double,
        metres: Double,
        hysteresis: Double,
    ): TouristTripSnapshot {
        val delta = metres - anchor
        return when {
            abs(delta) < hysteresis -> snapshot
            delta > 0 -> snapshot.copy(gainMeters = snapshot.gainMeters + delta, lastElevationMeters = metres)
            else -> snapshot.copy(lossMeters = snapshot.lossMeters - delta, lastElevationMeters = metres)
        }
    }

    private fun hysteresisFor(source: ElevationSource): Double = when (source) {
        ElevationSource.BAROMETER -> BAROMETER_HYSTERESIS_M
        ElevationSource.GPS -> GPS_HYSTERESIS_M
    }

    private companion object {
        const val BAROMETER_HYSTERESIS_M = 3.0
        const val GPS_HYSTERESIS_M = 10.0
        const val MIN_SLOPE_RUN_M = 20.0
        const val PERCENT = 100.0
    }
}
