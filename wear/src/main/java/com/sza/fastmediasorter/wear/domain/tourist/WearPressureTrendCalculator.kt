package com.sza.fastmediasorter.wear.domain.tourist

import java.util.concurrent.TimeUnit

/**
 * S4146: the pressure trend from samples collected while the Tourist section was open. The twin of
 * `PressureTrendCalculator` in `app_v2` (S4145).
 *
 * One sample a minute over six hours keeps the persisted list small; the trend itself reads the last
 * three hours, the span a barometric tendency is conventionally quoted over.
 */
class WearPressureTrendCalculator {

    fun append(samples: List<WearPressureSample>, sample: WearPressureSample): List<WearPressureSample> {
        val last = samples.lastOrNull()
        val accepted = if (last != null && sample.atMillis - last.atMillis < SAMPLE_SPACING_MS) {
            samples
        } else {
            samples + sample
        }
        return accepted.filter { sample.atMillis - it.atMillis <= RETENTION_MS }
    }

    fun trend(samples: List<WearPressureSample>, nowMillis: Long): WearPressureTrendResult {
        val window = samples.filter { nowMillis - it.atMillis <= TREND_WINDOW_MS }
        val oldest = window.firstOrNull()
        val newest = window.lastOrNull()
        return if (oldest == null || newest == null || newest.atMillis - oldest.atMillis < MIN_SPAN_MS) {
            WearPressureTrendResult.Accumulating
        } else {
            val delta = newest.hectopascals - oldest.hectopascals
            val trend = when {
                delta > STEADY_BAND_HPA -> WearPressureTrend.RISING
                delta < -STEADY_BAND_HPA -> WearPressureTrend.FALLING
                else -> WearPressureTrend.STEADY
            }
            WearPressureTrendResult.Ready(trend, delta)
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
