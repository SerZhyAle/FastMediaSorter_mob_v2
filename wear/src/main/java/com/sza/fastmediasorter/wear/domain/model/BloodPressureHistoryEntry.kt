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
 */
data class BloodPressureHistoryEntry(
    val id: Long,
    val systolic: Int,
    val diastolic: Int,
    val timestampMillis: Long
)
