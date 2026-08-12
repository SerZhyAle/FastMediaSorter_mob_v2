package com.sza.fastmediasorter.domain.usecase.sensors

import com.sza.fastmediasorter.domain.model.sensors.SensorSeriesId
import com.sza.fastmediasorter.domain.model.sensors.SensorSeriesPoint
import com.sza.fastmediasorter.domain.repository.SensorSeriesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bound to the production constants so a change to either number moves the test with it. */
private const val CAP = RecordSensorSeriesPointUseCase.MAX_POINTS
private const val THROTTLE_MS = RecordSensorSeriesPointUseCase.MIN_SAMPLE_INTERVAL_MS

/**
 * S1179: the three rules of [RecordSensorSeriesPointUseCase] - throttle, decimation cap, running
 * total - against an in-memory repository.
 */
class RecordSensorSeriesPointUseCaseTest {

    private val repository = FakeSensorSeriesRepository()
    private val useCase = RecordSensorSeriesPointUseCase(repository)
    private val series = SensorSeriesId.SPEED

    @Test
    fun `sample inside the throttle window is dropped`() = runTest {
        useCase(series, primaryValue = 10.0, secondaryDelta = null, takenAtMillis = 0L)
        useCase(series, primaryValue = 20.0, secondaryDelta = null, takenAtMillis = THROTTLE_MS - 1)

        assertEquals(1, repository.points(series).size)
    }

    @Test
    fun `sample outside the throttle window is appended`() = runTest {
        useCase(series, primaryValue = 10.0, secondaryDelta = null, takenAtMillis = 0L)
        useCase(series, primaryValue = 20.0, secondaryDelta = null, takenAtMillis = THROTTLE_MS)

        assertEquals(2, repository.points(series).size)
    }

    @Test
    fun `reaching the cap halves the series`() = runTest {
        repository.seed(series, CAP)

        useCase(series, primaryValue = 1.0, secondaryDelta = null, takenAtMillis = pastSeeded())

        assertEquals(CAP / 2 + 2, repository.points(series).size)
        assertTrue(repository.points(series).size < CAP)
    }

    @Test
    fun `the oldest point survives decimation`() = runTest {
        repository.seed(series, CAP)
        val oldest = repository.points(series).first().takenAtMillis

        useCase(series, primaryValue = 1.0, secondaryDelta = null, takenAtMillis = pastSeeded())

        assertEquals(oldest, repository.points(series).first().takenAtMillis)
    }

    @Test
    fun `the newest point survives decimation`() = runTest {
        repository.seed(series, CAP)
        val newestBefore = repository.points(series).last().takenAtMillis

        useCase(series, primaryValue = 1.0, secondaryDelta = null, takenAtMillis = pastSeeded())

        assertTrue(repository.points(series).any { it.takenAtMillis == newestBefore })
    }

    @Test
    fun `secondary deltas accumulate into a running total`() = runTest {
        recordDeltas(10.0, 5.0, 2.5)

        assertEquals(listOf(10.0, 15.0, 17.5), repository.points(series).map { it.secondaryValue })
    }

    @Test
    fun `the running total restarts from zero after a reset`() = runTest {
        recordDeltas(10.0, 5.0)
        repository.clear(series)

        useCase(series, primaryValue = 1.0, secondaryDelta = 3.0, takenAtMillis = 0L)

        assertEquals(listOf(3.0), repository.points(series).map { it.secondaryValue })
    }

    /** One interval past the seeded tail, so the throttle cannot be what rejects the sample. */
    private fun pastSeeded(): Long = CAP * THROTTLE_MS

    private suspend fun recordDeltas(vararg deltas: Double) {
        deltas.forEachIndexed { index, delta ->
            useCase(series, primaryValue = 1.0, secondaryDelta = delta, takenAtMillis = index * THROTTLE_MS)
        }
    }
}

private class FakeSensorSeriesRepository : SensorSeriesRepository {

    private val rows = mutableMapOf<SensorSeriesId, MutableList<SensorSeriesPoint>>()
    private var nextId = 1L

    override fun observe(id: SensorSeriesId): Flow<List<SensorSeriesPoint>> = flowOf(points(id))

    override suspend fun append(id: SensorSeriesId, point: SensorSeriesPoint) {
        rows.getOrPut(id) { mutableListOf() }.add(point.copy(id = nextId++))
    }

    override suspend fun keepOnly(id: SensorSeriesId, ids: List<Long>) {
        rows[id]?.retainAll { it.id in ids }
    }

    override suspend fun clear(id: SensorSeriesId) {
        rows.remove(id)
    }

    fun points(id: SensorSeriesId): List<SensorSeriesPoint> = rows[id].orEmpty().toList()

    /** Fills a series without going through the use case, so the cap is reached in one statement. */
    fun seed(id: SensorSeriesId, count: Int) {
        val series = rows.getOrPut(id) { mutableListOf() }
        repeat(count) { index ->
            series.add(
                SensorSeriesPoint(
                    id = nextId++,
                    takenAtMillis = index * THROTTLE_MS,
                    primaryValue = index.toDouble(),
                    secondaryValue = null
                )
            )
        }
    }
}
