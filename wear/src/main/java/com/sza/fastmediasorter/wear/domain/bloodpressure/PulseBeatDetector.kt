package com.sza.fastmediasorter.wear.domain.bloodpressure

/**
 * S3113: one heartbeat measured on the pulse wave.
 *
 * [amplitude] is in raw counts above the line joining the beat's two onsets; the time values are seconds.
 */
data class PulseBeat(
    val periodSeconds: Double,
    val upstrokeSeconds: Double,
    val width50Seconds: Double,
    val amplitude: Double
)

/** S3113: finds beats and measures them, by the rules written down in research 04. */
object PulseBeatDetector {

    private const val REFRACTORY_SECONDS = 0.33
    private const val HALF = 0.5

    fun detect(wave: PreparedPulseWave): List<PulseBeat> {
        val peaks = systolicPeaks(wave.highPassed, (REFRACTORY_SECONDS * wave.rateHz).toInt())
        val onsets = peaks.zipWithNext { from, to -> argMin(wave.smooth, from, to) }
        return onsets.zipWithNext { onset, next -> measure(wave, onset, next) }.filterNotNull()
    }

    /**
     * Local maxima above the baseline. Within the refractory distance only the higher one survives,
     * because a pulse faster than 180 bpm at rest is far less likely than a ripple on one beat.
     */
    private fun systolicPeaks(signal: DoubleArray, refractory: Int): List<Int> {
        val peaks = mutableListOf<Int>()
        for (index in 1 until signal.size - 1) {
            val isPeak = signal[index] > 0.0 && signal[index] >= signal[index - 1] && signal[index] > signal[index + 1]
            val previous = peaks.lastOrNull()
            when {
                !isPeak -> Unit
                previous == null || index - previous >= refractory -> peaks += index
                signal[index] > signal[previous] -> peaks[peaks.lastIndex] = index
            }
        }
        return peaks
    }

    private fun measure(wave: PreparedPulseWave, onset: Int, next: Int): PulseBeat? {
        val detrended = Detrended(wave.smooth, onset, next)
        val peak = (onset + 1 until next).maxByOrNull { detrended[it] }?.takeIf { detrended[it] > 0.0 } ?: return null
        val amplitude = detrended[peak]
        val rising = halfCrossingBefore(detrended, onset, peak, amplitude * HALF)
        val falling = halfCrossingAfter(detrended, peak, next, amplitude * HALF)
        return if (rising != null && falling != null) {
            PulseBeat(
                periodSeconds = (next - onset) / wave.rateHz,
                upstrokeSeconds = (peak - onset) / wave.rateHz,
                width50Seconds = (falling - rising) / wave.rateHz,
                amplitude = amplitude
            )
        } else {
            null
        }
    }

    /** Fractional index where the rise crosses [level], interpolated between the two samples around it. */
    private fun halfCrossingBefore(signal: Detrended, onset: Int, peak: Int, level: Double): Double? =
        (peak downTo onset + 1).firstOrNull { signal[it - 1] < level && level <= signal[it] }
            ?.let { index -> (index - 1) + (level - signal[index - 1]) / (signal[index] - signal[index - 1]) }

    /** Fractional index where the fall crosses [level], interpolated between the two samples around it. */
    private fun halfCrossingAfter(signal: Detrended, peak: Int, next: Int, level: Double): Double? =
        (peak until next).firstOrNull { signal[it] >= level && level > signal[it + 1] }
            ?.let { index -> index + (signal[index] - level) / (signal[index] - signal[index + 1]) }

    private fun argMin(signal: DoubleArray, from: Int, to: Int): Int =
        (from until to).minByOrNull { signal[it] } ?: from

    /** The smoothed wave with the straight line between two onsets subtracted, so both onsets read zero. */
    private class Detrended(private val signal: DoubleArray, private val onset: Int, private val next: Int) {
        operator fun get(index: Int): Double {
            val slope = (signal[next] - signal[onset]) / (next - onset)
            return signal[index] - (signal[onset] + slope * (index - onset))
        }
    }
}
