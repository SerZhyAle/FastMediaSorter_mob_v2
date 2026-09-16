package com.sza.fastmediasorter.wear.domain.bloodpressure

import com.sza.fastmediasorter.wear.domain.bodysensor.PpgWindow
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * S3113: judges one captured window and, when it passes, reduces it to [PulseWaveFeatures].
 *
 * Pure Kotlin with no Android type (strategic ADR-3), so the rules run in unit tests against the owner's
 * own recording. Every threshold is research 04's, named here so that changing one is a one-line edit.
 */
class ExtractPulseWaveFeaturesUseCase @Inject constructor() {

    operator fun invoke(window: PpgWindow): PulseWaveAnalysis {
        val wave = PulseWaveFilter.prepare(window)
        val beats = wave?.let { PulseBeatDetector.detect(it) }.orEmpty()
        val accepted = acceptedBeats(beats)
        val perfusion = if (wave != null && accepted.isNotEmpty()) {
            accepted.map { it.amplitude }.median() / wave.rawMean
        } else {
            0.0
        }
        return when {
            standardDeviation(window.motion.map { it.magnitude.toDouble() }) > MAX_MOTION_STD ->
                PulseWaveAnalysis.Rejected(PulseWaveRejection.MOTION)
            beats.isEmpty() || accepted.size < beats.size * MIN_ACCEPTED_SHARE || perfusion < MIN_PERFUSION ->
                PulseWaveAnalysis.Rejected(PulseWaveRejection.WEAK_SIGNAL)
            accepted.size < MIN_BEATS -> PulseWaveAnalysis.Rejected(PulseWaveRejection.TOO_FEW_BEATS)
            else -> PulseWaveAnalysis.Accepted(featuresOf(accepted, perfusion))
        }
    }

    /** A beat is kept when its period and height look like the window's typical beat. */
    private fun acceptedBeats(beats: List<PulseBeat>): List<PulseBeat> {
        if (beats.isEmpty()) return emptyList()
        val period = beats.map { it.periodSeconds }.median()
        val amplitude = beats.map { it.amplitude }.median()
        return beats.filter { beat ->
            abs(beat.periodSeconds - period) <= period * MAX_PERIOD_DEVIATION &&
                beat.amplitude in amplitude / MAX_AMPLITUDE_RATIO..amplitude * MAX_AMPLITUDE_RATIO
        }
    }

    private fun featuresOf(accepted: List<PulseBeat>, perfusion: Double): PulseWaveFeatures = PulseWaveFeatures(
        heartRateBpm = SECONDS_PER_MINUTE / accepted.map { it.periodSeconds }.median(),
        systolicUpstrokeSeconds = accepted.map { it.upstrokeSeconds }.median(),
        pulseWidth50Seconds = accepted.map { it.width50Seconds }.median(),
        perfusionIndex = perfusion,
        acceptedBeats = accepted.size
    )

    private fun List<Double>.median(): Double {
        val sorted = sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }

    private fun standardDeviation(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60.0
        const val MAX_MOTION_STD = 0.25
        const val MIN_PERFUSION = 0.0002
        const val MIN_ACCEPTED_SHARE = 0.6
        const val MIN_BEATS = 15
        const val MAX_PERIOD_DEVIATION = 0.2
        const val MAX_AMPLITUDE_RATIO = 3.0
    }
}
