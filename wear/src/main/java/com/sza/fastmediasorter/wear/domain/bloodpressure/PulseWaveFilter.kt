package com.sza.fastmediasorter.wear.domain.bloodpressure

import com.sza.fastmediasorter.wear.domain.bodysensor.PpgWindow
import kotlin.math.max
import kotlin.math.min

/**
 * S3113: the pulse channel of one window, on a uniform time grid, in the two forms beat detection reads.
 *
 * [smooth] keeps the baseline, because per-beat features are measured against a line drawn between the
 * beat's own onsets. [highPassed] has the baseline removed, because peak detection needs a zero to be
 * "above". [rawMean] is the channel's mean count, the denominator of the perfusion index.
 */
class PreparedPulseWave(
    val smooth: DoubleArray,
    val highPassed: DoubleArray,
    val rawMean: Double,
    val rateHz: Double
)

/** S3113: turns a raw window into a [PreparedPulseWave] by the rules written down in research 04. */
object PulseWaveFilter {

    const val RATE_HZ = 100.0
    private const val SMOOTHING_SECONDS = 0.1
    private const val BASELINE_SECONDS = 2.0
    private const val NANOS_PER_SECOND = 1_000_000_000.0
    private const val MIN_SAMPLES = 4

    /** Null when the window holds too few pulse samples to interpolate between. */
    fun prepare(window: PpgWindow): PreparedPulseWave? {
        val samples = window.ppg.filter { it.channels.size > window.layout.channel }
        if (samples.size < MIN_SAMPLES) return null
        val origin = samples.first().timestampNanos
        val times = DoubleArray(samples.size) { (samples[it].timestampNanos - origin) / NANOS_PER_SECOND }
        val raw = DoubleArray(samples.size) { samples[it].channels[window.layout.channel].toDouble() }
        val sign = if (window.layout.inverted) -1.0 else 1.0
        val uniform = resample(times, DoubleArray(raw.size) { sign * raw[it] })
        val smooth = movingAverage(uniform, (SMOOTHING_SECONDS * RATE_HZ).toInt())
        val baseline = movingAverage(smooth, (BASELINE_SECONDS * RATE_HZ).toInt())
        return PreparedPulseWave(
            smooth = smooth,
            highPassed = DoubleArray(smooth.size) { smooth[it] - baseline[it] },
            rawMean = raw.average(),
            rateHz = RATE_HZ
        )
    }

    /**
     * Catmull-Rom interpolation onto [RATE_HZ]. Event delivery jitters by about 1.5 ms around 40 ms
     * (research 03), and treating the events as evenly spaced would move every fiducial point by that much.
     */
    private fun resample(times: DoubleArray, values: DoubleArray): DoubleArray {
        val count = ((times.last() - times.first()) * RATE_HZ).toInt() + 1
        var segment = 0
        return DoubleArray(count) { index ->
            val time = times.first() + index / RATE_HZ
            while (segment < times.size - 2 && times[segment + 1] < time) {
                segment++
            }
            val span = times[segment + 1] - times[segment]
            val fraction = if (span > 0.0) ((time - times[segment]) / span).coerceIn(0.0, 1.0) else 0.0
            catmullRom(values, segment, fraction)
        }
    }

    @Suppress("MagicNumber") // The coefficients are the Catmull-Rom basis itself, not tunable thresholds.
    private fun catmullRom(values: DoubleArray, segment: Int, u: Double): Double {
        val last = values.size - 1
        val p0 = values[max(segment - 1, 0)]
        val p1 = values[segment]
        val p2 = values[min(segment + 1, last)]
        val p3 = values[min(segment + 2, last)]
        return 0.5 * (
            2 * p1 +
                (-p0 + p2) * u +
                (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u +
                (-p0 + 3 * p1 - 3 * p2 + p3) * u * u * u
            )
    }

    /** Centred moving average; the window shrinks at both ends instead of padding them with a guess. */
    private fun movingAverage(signal: DoubleArray, width: Int): DoubleArray {
        val prefix = DoubleArray(signal.size + 1)
        signal.forEachIndexed { index, value -> prefix[index + 1] = prefix[index] + value }
        val half = width / 2
        return DoubleArray(signal.size) { index ->
            val from = max(0, index - half)
            val to = min(signal.size, index + half + 1)
            (prefix[to] - prefix[from]) / (to - from)
        }
    }
}
