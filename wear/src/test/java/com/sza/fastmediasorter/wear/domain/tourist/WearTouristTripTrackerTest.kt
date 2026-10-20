package com.sza.fastmediasorter.wear.domain.tourist

import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.FakeWearPreferencesRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a slow write that captured the old trip does not undo a later reset`() = runTest {
        val stored = WearTripSnapshot(startedAtMillis = T0, distanceMeters = 800.0)
        val inner = FakeWearPreferencesRepository().apply {
            setTouristTrip(WearTripSnapshotCodec.encode(stored))
        }
        val gate = CompletableDeferred<Unit>()
        val tracker = WearTouristTripTracker(GatedFirstWritePreferences(inner, gate))
        tracker.ensureLoaded()

        val slowWriter = launch { tracker.persist(T0) }
        runCurrent()
        val resetter = launch { tracker.reset() }
        runCurrent()
        gate.complete(Unit)
        slowWriter.join()
        resetter.join()

        val onDisk = WearTripSnapshotCodec.decode(inner.touristTripValue)
        assertNull(onDisk?.startedAtMillis)
        assertEquals(0.0, onDisk?.distanceMeters ?: -1.0, DELTA)
    }

    @Test
    fun `a failed write does not hold back the next attempt`() = runTest {
        val inner = FakeWearPreferencesRepository()
        val tracker = WearTouristTripTracker(FailingFirstWritePreferences(inner))
        tracker.ensureLoaded()
        tracker.onMotion(5f, 0.0, 5f, T0)

        assertThrows(IOException::class.java) {
            runBlocking { tracker.persistIfDue(T0 + PERSIST_MS) }
        }
        assertNull(inner.touristTripValue)
        tracker.persistIfDue(T0 + PERSIST_MS + 1_000L)

        assertNotNull(inner.touristTripValue)
    }

    /** Holds the first write on [gate]; every later write goes straight through. */
    private class GatedFirstWritePreferences(
        private val inner: FakeWearPreferencesRepository,
        private val gate: CompletableDeferred<Unit>,
    ) : WearPreferencesRepository by inner {
        private var writes = 0

        override suspend fun setTouristTrip(value: String?) {
            if (writes++ == 0) gate.await()
            inner.setTouristTrip(value)
        }
    }

    /** Fails the first write with an I/O error, as a full or broken store would. */
    private class FailingFirstWritePreferences(
        private val inner: FakeWearPreferencesRepository,
    ) : WearPreferencesRepository by inner {
        private var failed = false

        override suspend fun setTouristTrip(value: String?) {
            if (!failed) {
                failed = true
                throw IOException("store unavailable")
            }
            inner.setTouristTrip(value)
        }
    }

    private companion object {
        const val T0 = 1_000_000L
        const val PERSIST_MS = 15_000L
        const val DELTA = 0.001
    }
}
