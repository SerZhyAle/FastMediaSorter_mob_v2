package com.sza.fastmediasorter.wear.domain.tourist

import com.sza.fastmediasorter.wear.domain.usecase.FakeWearPreferencesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearTouristTripTrackerTest {

    @Test
    fun `a stored trip is loaded once`() = runTest {
        val stored = WearTripSnapshot(startedAtMillis = T0, distanceMeters = 800.0)
        val preferences = FakeWearPreferencesRepository().apply {
            setTouristTrip(WearTripSnapshotCodec.encode(stored))
        }
        val tracker = WearTouristTripTracker(preferences)

        tracker.ensureLoaded()

        assertEquals(stored, tracker.snapshot.value)
    }

    @Test
    fun `reset zeroes the trip and keeps pressure samples`() = runTest {
        val samples = listOf(WearPressureSample(T0, 1010f))
        val stored = WearTripSnapshot(
            startedAtMillis = T0,
            movingMillis = 60_000L,
            distanceMeters = 800.0,
            gainMeters = 30.0,
            lossMeters = 10.0,
            maxSpeedKmh = 6f,
            pressureSamples = samples,
        )
        val preferences = FakeWearPreferencesRepository().apply {
            setTouristTrip(WearTripSnapshotCodec.encode(stored))
        }
        val tracker = WearTouristTripTracker(preferences)

        tracker.reset()

        val trip = tracker.snapshot.value
        assertNull(trip.startedAtMillis)
        assertEquals(0L, trip.movingMillis)
        assertEquals(0.0, trip.distanceMeters, DELTA)
        assertEquals(0.0, trip.gainMeters, DELTA)
        assertEquals(0.0, trip.lossMeters, DELTA)
        assertEquals(0f, trip.maxSpeedKmh, DELTA.toFloat())
        assertEquals(samples, trip.pressureSamples)
        assertEquals(trip, WearTripSnapshotCodec.decode(preferences.touristTripValue))
    }

    @Test
    fun `persist is throttled to once per fifteen seconds`() = runTest {
        val preferences = FakeWearPreferencesRepository()
        val tracker = WearTouristTripTracker(preferences)
        tracker.ensureLoaded()

        tracker.onMotion(5f, 0.0, 5f, T0)
        tracker.persistIfDue(T0 + PERSIST_MS)
        val first = preferences.touristTripValue
        tracker.onMotion(5f, 5.0, 5f, T0 + 1_000L)
        tracker.persistIfDue(T0 + PERSIST_MS + 1_000L)

        assertEquals(first, preferences.touristTripValue)
    }

    @Test
    fun `nothing is written before the stored trip was read`() = runTest {
        val preferences = FakeWearPreferencesRepository()
        val tracker = WearTouristTripTracker(preferences)

        tracker.persist(T0)

        assertNull(preferences.touristTripValue)
    }

    private companion object {
        const val T0 = 1_000_000L
        const val PERSIST_MS = 15_000L
        const val DELTA = 0.001
    }
}
