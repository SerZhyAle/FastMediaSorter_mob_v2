package com.sza.fastmediasorter.wear.domain.tourist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearTripAccumulatorTest {

    private val accumulator = WearTripAccumulator()

    @Test
    fun `first sample starts the trip and adds nothing`() {
        val trip = accumulator.onMotion(WearTripSnapshot.EMPTY, WALK_KMH, 5.0, GOOD_ACCURACY, T0)

        assertEquals(T0, trip.startedAtMillis)
        assertEquals(0.0, trip.distanceMeters, DELTA)
        assertEquals(0L, trip.movingMillis)
    }

    @Test
    fun `continuous moving sample adds distance and moving time`() {
        val first = accumulator.onMotion(WearTripSnapshot.EMPTY, WALK_KMH, 0.0, GOOD_ACCURACY, T0)
        val second = accumulator.onMotion(first, WALK_KMH, 5.0, GOOD_ACCURACY, T0 + STEP_MS)

        assertEquals(5.0, second.distanceMeters, DELTA)
        assertEquals(STEP_MS, second.movingMillis)
    }

    @Test
    fun `a gap over thirty seconds adds no distance`() {
        val first = accumulator.onMotion(WearTripSnapshot.EMPTY, WALK_KMH, 0.0, GOOD_ACCURACY, T0)
        val second = accumulator.onMotion(first, WALK_KMH, 400.0, GOOD_ACCURACY, T0 + GAP_MS)

        assertEquals(0.0, second.distanceMeters, DELTA)
        assertEquals(0L, second.movingMillis)
    }

    @Test
    fun `a loose fix adds no distance`() {
        val first = accumulator.onMotion(WearTripSnapshot.EMPTY, WALK_KMH, 0.0, GOOD_ACCURACY, T0)
        val second = accumulator.onMotion(first, WALK_KMH, 30.0, LOOSE_ACCURACY, T0 + STEP_MS)

        assertEquals(0.0, second.distanceMeters, DELTA)
    }

    @Test
    fun `a stationary sample adds no moving time`() {
        val first = accumulator.onMotion(WearTripSnapshot.EMPTY, 0f, 0.0, GOOD_ACCURACY, T0)
        val second = accumulator.onMotion(first, 0.5f, 1.0, GOOD_ACCURACY, T0 + STEP_MS)

        assertEquals(0L, second.movingMillis)
        assertEquals(0.0, second.distanceMeters, DELTA)
    }

    @Test
    fun `gps hysteresis ignores five metres of noise`() {
        val anchored = accumulator.onElevation(WearTripSnapshot.EMPTY, 100.0, WearElevationSource.GPS)
        val noisy = accumulator.onElevation(anchored, 105.0, WearElevationSource.GPS)

        assertEquals(0.0, noisy.gainMeters, DELTA)
    }

    @Test
    fun `barometer gain and loss pass the hysteresis band`() {
        var trip = accumulator.onElevation(WearTripSnapshot.EMPTY, 100.0, WearElevationSource.BAROMETER)
        trip = accumulator.onElevation(trip, 110.0, WearElevationSource.BAROMETER)
        trip = accumulator.onElevation(trip, 104.0, WearElevationSource.BAROMETER)

        assertEquals(10.0, trip.gainMeters, DELTA)
        assertEquals(6.0, trip.lossMeters, DELTA)
    }

    @Test
    fun `barometer replaces gps as the source and gps is ignored after`() {
        var trip = accumulator.onElevation(WearTripSnapshot.EMPTY, 100.0, WearElevationSource.GPS)
        trip = accumulator.onElevation(trip, 140.0, WearElevationSource.BAROMETER)
        trip = accumulator.onElevation(trip, 200.0, WearElevationSource.GPS)

        assertEquals(WearElevationSource.BAROMETER, trip.elevationSource)
        assertEquals(0.0, trip.gainMeters, DELTA)
    }

    @Test
    fun `reset keeps pressure samples`() {
        val samples = listOf(WearPressureSample(T0, PRESSURE))
        val trip = WearTripSnapshot(startedAtMillis = T0, distanceMeters = 42.0, pressureSamples = samples)

        val reset = accumulator.reset(trip)

        assertNull(reset.startedAtMillis)
        assertEquals(0.0, reset.distanceMeters, DELTA)
        assertEquals(samples, reset.pressureSamples)
    }

    @Test
    fun `slope is null at a standstill`() {
        assertNull(accumulator.slopePercent(listOf(0.0 to 100.0, 2.0 to 101.0)))
    }

    @Test
    fun `slope is ten percent over a hundred metres climbing ten`() {
        assertEquals(10f, accumulator.slopePercent(listOf(0.0 to 100.0, 100.0 to 110.0))!!, DELTA.toFloat())
    }

    private companion object {
        const val T0 = 1_000_000L
        const val STEP_MS = 1_000L
        const val GAP_MS = 31_000L
        const val WALK_KMH = 5f
        const val GOOD_ACCURACY = 5f
        const val LOOSE_ACCURACY = 80f
        const val PRESSURE = 1000f
        const val DELTA = 0.001
    }
}
