package com.sza.fastmediasorter.domain.tourist

import com.sza.fastmediasorter.domain.model.tourist.PressureSample
import com.sza.fastmediasorter.domain.model.tourist.PressureTrend
import com.sza.fastmediasorter.domain.model.tourist.PressureTrendResult
import java.util.concurrent.TimeUnit

/**
 * S4145: the pressure trend from samples collected while the dashboard was open.
 *
 * One sample a minute over six hours keeps the persisted list small; the trend itself reads the last
 * three hours, the span a barometric tendency is conventionally quoted over.
 */
class PressureTrendCalculator {

    fun append(samples: List<PressureSample>, sample: PressureSample): List<PressureSample> {
        val last = samples.lastOrNull()
        val accepted = if (last != null && sample.atMillis - last.atMillis < SAMPLE_SPACING_MS) {
            samples
        } else {
            samples + sample
        }
        return accepted.filter { sample.atMillis - it.atMillis <= RETENTION_MS }
    }

    fun trend(samples: List<PressureSample>, nowMillis: Long): PressureTrendResult {
        val window = samples.filter { nowMillis - it.atMillis <= TREND_WINDOW_MS }
        val oldest = window.firstOrNull()
        val newest = window.lastOrNull()
        return if (oldest == null || newest == null || newest.atMillis - oldest.atMillis < MIN_SPAN_MS) {
            PressureTrendResult.Accumulating
        } else {
            val delta = newest.hectopascals - oldest.hectopascals
            val trend = when {
                delta > STEADY_BAND_HPA -> PressureTrend.RISING
                delta < -STEADY_BAND_HPA -> PressureTrend.FALLING
                else -> PressureTrend.STEADY
            }
            PressureTrendResult.Ready(trend, delta)
        }
    }

    private companion object {
        val SAMPLE_SPACING_MS = TimeUnit.MINUTES.toMillis(1)
        val RETENTION_MS = TimeUnit.HOURS.toMillis(6)
        val TREND_WINDOW_MS = TimeUnit.HOURS.toMillis(3)
        val MIN_SPAN_MS = TimeUnit.HOURS.toMillis(1)
        const val STEADY_BAND_HPA = 1f
    }
}
