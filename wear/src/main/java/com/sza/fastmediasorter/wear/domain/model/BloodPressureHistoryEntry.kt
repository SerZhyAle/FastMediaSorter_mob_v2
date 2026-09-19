package com.sza.fastmediasorter.wear.domain.model

/**
 * S2809: one completed blood pressure measurement in the domain layer.
 *
 * Unlike [HeartRateHistoryEntry], which carries a single BPM value, a blood pressure
 * reading has two measured values: systolic and diastolic, both in mmHg. The two are
 * stored together because they are taken together and only mean something together.
 *
 * [timestampMillis] is the wall-clock time the measurement was saved, set by the repository
 * implementation so callers never need to pass it.
 *
 * S3113: [source] tells a cuff reading from an estimate, which the history must show; [pulse] is the heart
 * rate measured with it, null for a row typed before the watch measured one.
 */
data class BloodPressureHistoryEntry(
    val id: Long,
    val systolic: Int,
    val diastolic: Int,
    val timestampMillis: Long,
    val source: BloodPressureSource = BloodPressureSource.MANUAL,
    val pulse: Int? = null
)
