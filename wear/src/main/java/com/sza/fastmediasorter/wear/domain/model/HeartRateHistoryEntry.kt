package com.sza.fastmediasorter.wear.domain.model

/**
 * S2808: one completed heart-rate measurement in the domain layer.
 *
 * [timestampMillis] is the wall-clock time the measurement was saved, set by the repository
 * implementation so callers never need to pass it.
 */
data class HeartRateHistoryEntry(
    val id: Long,
    val bpm: Int,
    val timestampMillis: Long
)
