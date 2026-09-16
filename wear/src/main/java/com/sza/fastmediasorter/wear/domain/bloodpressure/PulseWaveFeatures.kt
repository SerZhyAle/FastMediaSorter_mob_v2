package com.sza.fastmediasorter.wear.domain.bloodpressure

/**
 * S3113: the feature vector of one accepted window - per-beat medians over the accepted beats.
 *
 * These three plus the pulse are what the calibration model is fitted on (research 04); the perfusion
 * index and the beat count are kept for the quality verdict and for reading a stored pair later.
 */
data class PulseWaveFeatures(
    val heartRateBpm: Double,
    val systolicUpstrokeSeconds: Double,
    val pulseWidth50Seconds: Double,
    val perfusionIndex: Double,
    val acceptedBeats: Int
)

/** S3113: why a window gave no features. Each value has its own sentence on screen. */
enum class PulseWaveRejection {

    /** The wrist moved during the window. */
    MOTION,

    /** Too little pulse in the light, or too many beats that do not look like their neighbours. */
    WEAK_SIGNAL,

    /** The signal was clean but the window held too few beats to take a median over. */
    TOO_FEW_BEATS
}

/** S3113: the verdict on one window - features, or the rule that refused it. */
sealed interface PulseWaveAnalysis {

    data class Accepted(val features: PulseWaveFeatures) : PulseWaveAnalysis

    data class Rejected(val reason: PulseWaveRejection) : PulseWaveAnalysis
}
