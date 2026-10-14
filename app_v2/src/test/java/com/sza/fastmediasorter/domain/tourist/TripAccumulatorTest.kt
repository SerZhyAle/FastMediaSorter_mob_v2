package com.sza.fastmediasorter.domain.tourist

import com.sza.fastmediasorter.domain.model.tourist.ElevationSource
import com.sza.fastmediasorter.domain.model.tourist.PressureSample
import com.sza.fastmediasorter.domain.model.tourist.TouristTripSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripAccumulatorTest {

    private val accumulator = TripAccumulator()

    @Test
    fun `first sample starts the trip and adds nothing`() {
        val trip = accumulator.onMotion(TouristTripSnapshot.EMPTY, WALK_KMH, 5.0, GOOD_ACCURACY, T0)

        assertEquals(T0, trip.startedAtMillis)
        assertEquals(0.0, trip.distanceMeters, DELTA)
        assertEquals(0L, trip.movingMillis)
    }

    @Test
    fun `continuous moving sample adds distance and moving time`() {
        val first = accumulator.onMotion(TouristTripSnapshot.EMPTY, WALK_KMH, 0.0, GOOD_ACCURACY, T0)
        val second = accumulator.onMotion(first, WALK_KMH, 5.0, GOOD_ACCURACY, T0 + STEP_MS)

        assertEquals(5.0, second.distanceMeters, DELTA)
        assertEquals(STEP_MS, second.movingMillis)
    }

    @Test
    fun `a gap over thirty seconds adds no distance`() {
        val first = accumulator.onMotion(TouristTripSnapshot.EMPTY, WALK_KMH, 0.0, GOOD_ACCURACY, T0)
        val second = accumulator.onMotion(first, WALK_KMH, 400.0, GOOD_ACCURACY, T0 + GAP_MS)

        assertEquals(0.0, second.distanceMeters, DELTA)
        assertEquals(0L, second.movingMillis)
    }

    @Test
    fun `a stationary sample adds no moving time`() {
        val first = accumulator.onMotion(TouristTripSnapshot.EMPTY, 0f, 0.0, GOOD_ACCURACY, T0)
        val second = accumulator.onMotion(first, 0.5f, 1.0, GOOD_ACCURACY, T0 + STEP_MS)

        assertEquals(0L, second.movingMillis)
        assertEquals(0.0, second.distanceMeters, DELTA)
    }

    @Test
    fun `gps gain ignores five metres of noise`() {
        val anchored = accumulator.onElevation(TouristTripSnapshot.EMPTY, 100.0, ElevationSource.GPS)
        val noisy = accumulator.onElevation(anchored, 105.0, ElevationSource.GPS)

        assertEquals(0.0, noisy.gainMeters, DELTA)
    }

    @Test
    fun `barometer gain and loss pass the band`() {
        var trip = accumulator.onElevation(TouristTripSnapshot.EMPTY, 100.0, ElevationSource.BAROMETER)
        trip = accumulator.onElevation(trip, 110.0, ElevationSource.BAROMETER)
        trip = accumulator.onElevation(trip, 104.0, ElevationSource.BAROMETER)

        assertEquals(10.0, trip.gainMeters, DELTA)
        assertEquals(6.0, trip.lossMeters, DELTA)
    }

    @Test
    fun `barometer replaces gps as the source and gps is ignored after`() {
        var trip = accumulator.onElevation(TouristTripSnapshot.EMPTY, 100.0, ElevationSource.GPS)
        trip = accumulator.onElevation(trip, 140.0, ElevationSource.BAROMETER)
        trip = accumulator.onElevation(trip, 200.0, ElevationSource.GPS)

        assertEquals(ElevationSource.BAROMETER, trip.elevationSource)
        assertEquals(0.0, trip.gainMeters, DELTA)
    }

    @Test
    fun `reset keeps pressure samples`() {
        val samples = listOf(PressureSample(T0, PRESSURE))
        val trip = TouristTripSnapshot(startedAtMillis = T0, distanceMeters = 42.0, pressureSamples = samples)

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
        const val PRESSURE = 1000f
        const val DELTA = 0.001
    }
}
