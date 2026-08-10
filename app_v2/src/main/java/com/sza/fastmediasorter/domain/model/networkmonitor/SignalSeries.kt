package com.sza.fastmediasorter.domain.model.networkmonitor

import com.sza.fastmediasorter.domain.model.sensors.SensorSeriesPoint

/**
 * S1433: one reading of a live signal source, in the unit its own section displays.
 *
 * The value is a plain number with no unit attached, because the four sources charted by the Monitor are
 * measured in four different units and only the section that owns one knows which it is.
 */
data class SignalSample(
    val takenAtMillis: Long,
    val value: Double,
)

/** S1433: which way a series moved on its last step. */
enum class SignalTrend { RISING, FALLING, FLAT }

/**
 * S1433: the bounded window of samples one signal chart draws.
 *
 * Memory-only by construction. There is no save, no load and no identifier here, because strategic §3.2
 * keeps live signal readings out of storage - a window that could be persisted eventually would be, and the
 * absence of the API is what prevents it rather than a rule somebody has to remember.
 *
 * The window is bounded by sample count and not by age: [SAMPLE_CAPACITY] readings at the one-per-second
 * cadence the Monitor samples on (`ConnectivitySnapshotDataSource.RESAMPLE_INTERVAL_MS`, which the composed
 * snapshot is capped to as well) is the two minutes strategic §3.2 asks a chart to cover. Counting keeps the
 * cost of a window fixed, so a burst of readings shortens the span shown instead of growing the memory held.
 */
data class SignalSeries(
    val samples: List<SignalSample> = emptyList(),
) {

    /** Newest value, or null while nothing has been sampled yet. */
    val last: Double? get() = samples.lastOrNull()?.value

    /** Lowest value still inside the window - it rises again as the reading that set it ages out. */
    val min: Double? get() = samples.minOfOrNull { it.value }

    /** Highest value still inside the window. */
    val max: Double? get() = samples.maxOfOrNull { it.value }

    /**
     * Direction of the final step, or null below two samples.
     *
     * A trend needs a step to measure it, and calling a single reading [SignalTrend.FLAT] would report a
     * stability nobody observed.
     */
    val trend: SignalTrend?
        get() {
            if (samples.size < MIN_TREND_SAMPLES) {
                return null
            }
            val previous = samples[samples.size - 2].value
            val current = samples.last().value
            return when {
                current > previous -> SignalTrend.RISING
                current < previous -> SignalTrend.FALLING
                else -> SignalTrend.FLAT
            }
        }

    /**
     * Appends [sample], dropping the oldest reading once the window is full.
     *
     * A sample arriving less than [MIN_SAMPLE_INTERVAL_MS] after the previous one is ignored. The limit is
     * enforced here rather than by throttling redraws, because the chart repaints only when it is handed new
     * points: bounding the window bounds the repaints, and every source passes through this one funnel, so a
     * future sampler cannot spend the battery a redraw throttle would have had to catch.
     */
    fun append(sample: SignalSample): SignalSeries {
        val previous = samples.lastOrNull()
        if (previous != null && sample.takenAtMillis - previous.takenAtMillis < MIN_SAMPLE_INTERVAL_MS) {
            return this
        }
        return SignalSeries((samples + sample).takeLast(SAMPLE_CAPACITY))
    }

    /**
     * The window as chart points, oldest first.
     *
     * [SensorSeriesPoint.secondaryValue] is null because a signal series has one dimension - the shared
     * chart reads that as "there is no second line", never as "the second line is zero".
     */
    fun toChartPoints(): List<SensorSeriesPoint> = samples.map { sample ->
        SensorSeriesPoint(
            takenAtMillis = sample.takenAtMillis,
            primaryValue = sample.value,
            secondaryValue = null,
        )
    }

    /**
     * The numbers a section shows beside the chart, or null while the window is empty.
     *
     * Numbers only, with no unit and no wording: four sections draw four different quantities, and a summary
     * that carried phrasing would have to be re-translated for each of them.
     */
    fun summary(): SignalSummary? {
        val newest = last ?: return null
        return SignalSummary(
            last = newest,
            min = min ?: newest,
            max = max ?: newest,
            trend = trend,
        )
    }

    companion object {

        /** How many readings a window holds; see the class comment for why the bound is a count. */
        const val SAMPLE_CAPACITY = 120

        /** The floor between two accepted readings - four per second, the cap strategic §3.2 sets. */
        const val MIN_SAMPLE_INTERVAL_MS = 250L

        /** Two readings are the fewest a direction can be derived from. */
        private const val MIN_TREND_SAMPLES = 2
    }
}

/**
 * S1433: what a signal section states in text beside its chart.
 *
 * [trend] is null until the window holds two readings, because a direction needs a step to measure.
 */
data class SignalSummary(
    val last: Double,
    val min: Double,
    val max: Double,
    val trend: SignalTrend?,
)
