package com.sza.fastmediasorter.wear.domain.model

import kotlin.math.roundToInt

/**
 * S3013: Summary statistics computed over a collection of heart rate history entries.
 *
 * @param count total number of recordings included in this summary.
 * @param avgBpm mean heart rate in BPM (rounded to nearest integer).
 * @param minBpm lowest heart rate recorded in the set.
 * @param maxBpm highest heart rate recorded in the set.
 * @param avgZone zone corresponding to the average heart rate.
 * @param zoneCounts frequency count of readings in each heart rate zone.
 */
data class HeartRateSummary(
    val count: Int,
    val avgBpm: Int,
    val minBpm: Int,
    val maxBpm: Int,
    val avgZone: HeartRateZone,
    val zoneCounts: Map<HeartRateZone, Int>
)

/**
 * S3013: Analytics calculation utilities for heart rate measurements.
 */
object HeartRateAnalytics {

    /**
     * Computes summary statistics over the provided heart rate entries.
     * Returns null if [entries] is empty.
     */
    fun computeSummary(entries: List<HeartRateHistoryEntry>): HeartRateSummary? {
        if (entries.isEmpty()) return null

        val count = entries.size
        val avg = entries.map { it.bpm }.average().roundToInt()
        val min = entries.minOf { it.bpm }
        val max = entries.maxOf { it.bpm }

        val zoneCounts = mutableMapOf<HeartRateZone, Int>()
        for (zone in HeartRateZone.entries) {
            zoneCounts[zone] = 0
        }
        for (entry in entries) {
            val z = HeartRateZone.classify(entry.bpm)
            zoneCounts[z] = (zoneCounts[z] ?: 0) + 1
        }

        return HeartRateSummary(
            count = count,
            avgBpm = avg,
            minBpm = min,
            maxBpm = max,
            avgZone = HeartRateZone.classify(avg),
            zoneCounts = zoneCounts
        )
    }
}
