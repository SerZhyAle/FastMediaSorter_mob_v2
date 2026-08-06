package com.sza.fastmediasorter.domain.usecase.sensors

import com.sza.fastmediasorter.domain.model.sensors.SensorSeriesId
import com.sza.fastmediasorter.domain.model.sensors.SensorSeriesPoint
import com.sza.fastmediasorter.domain.repository.SensorSeriesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * S1179: appends one sample to a chart series, holding the three rules that keep the series a trip
 * meter rather than a moving window.
 */
class RecordSensorSeriesPointUseCase @Inject constructor(
    private val repository: SensorSeriesRepository,
) {

    suspend operator fun invoke(
        id: SensorSeriesId,
        primaryValue: Double,
        secondaryDelta: Double?,
        takenAtMillis: Long,
    ) {
        val stored = repository.observe(id).first()
        val newest = stored.lastOrNull()
        if (newest != null && takenAtMillis - newest.takenAtMillis < MIN_SAMPLE_INTERVAL_MS) return

        if (stored.size >= MAX_POINTS) {
            repository.keepOnly(id, survivorsOf(stored))
        }
        repository.append(
            id,
            SensorSeriesPoint(
                takenAtMillis = takenAtMillis,
                primaryValue = primaryValue,
                secondaryValue = secondaryDelta?.plus(newest?.secondaryValue ?: 0.0)
            )
        )
    }

    /**
     * Halves the series by keeping every second point, then re-adds the newest one if that thinning
     * dropped it. Both ends must survive: the series means "since the last reset", so losing the head
     * would silently redefine it as a moving window, and losing the tail would rewind the running
     * total the next sample builds on.
     */
    private fun survivorsOf(stored: List<SensorSeriesPoint>): List<Long> {
        val kept = stored.filterIndexed { index, _ -> index % 2 == 0 }.map { it.id }
        val newestId = stored.last().id
        return if (newestId in kept) kept else kept + newestId
    }

    internal companion object {
        internal const val MIN_SAMPLE_INTERVAL_MS = 5_000L
        internal const val MAX_POINTS = 720
    }
}
