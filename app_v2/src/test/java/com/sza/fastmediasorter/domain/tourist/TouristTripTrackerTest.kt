package com.sza.fastmediasorter.domain.tourist

import com.sza.fastmediasorter.domain.model.tourist.PressureSample
import com.sza.fastmediasorter.domain.model.tourist.TouristTripSnapshot
import com.sza.fastmediasorter.domain.repository.TouristTripRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TouristTripTrackerTest {

    private class FakeRepository(var stored: TouristTripSnapshot) : TouristTripRepository {
        var loads = 0

        override suspend fun load(): TouristTripSnapshot {
            loads++
            return stored
        }

        override suspend fun save(snapshot: TouristTripSnapshot) {
            stored = snapshot
        }
    }

    @Test
    fun `a loaded trip is not overwritten by a second load`() = runTest {
        val repository = FakeRepository(TouristTripSnapshot(startedAtMillis = 1L, distanceMeters = 100.0))
        val tracker = TouristTripTracker(repository)

        tracker.ensureLoaded()
        repository.stored = TouristTripSnapshot.EMPTY
        tracker.ensureLoaded()

        assertEquals(1, repository.loads)
        assertEquals(100.0, tracker.snapshot.value.distanceMeters, 0.001)
    }

    @Test
    fun `reset persists an empty trip with the pressure samples kept`() = runTest {
        val samples = listOf(PressureSample(5L, 1000f))
        val repository = FakeRepository(
            TouristTripSnapshot(startedAtMillis = 1L, distanceMeters = 100.0, pressureSamples = samples),
        )
        val tracker = TouristTripTracker(repository)
        tracker.ensureLoaded()

        tracker.reset()

        assertNull(repository.stored.startedAtMillis)
        assertEquals(0.0, repository.stored.distanceMeters, 0.001)
        assertEquals(samples, repository.stored.pressureSamples)
    }
}
