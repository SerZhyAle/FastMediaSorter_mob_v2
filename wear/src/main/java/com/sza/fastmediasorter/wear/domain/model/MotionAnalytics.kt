package com.sza.fastmediasorter.wear.domain.model

import kotlin.math.roundToLong

/**
 * S3014: Summary statistics computed over a collection of motion/step history entries.
 *
 * @param count total number of recordings included in this summary.
 * @param totalSteps cumulative sum of all recorded steps.
 * @param avgSteps mean steps per recording (rounded to nearest whole step).
 * @param minSteps lowest recorded step count in the set.
 * @param maxSteps highest recorded step count in the set.
 * @param avgIntensity intensity tier corresponding to the average steps.
 * @param intensityCounts frequency count of entries in each intensity tier.
 */
data class MotionSummary(
    val count: Int,
    val totalSteps: Long,
    val avgSteps: Long,
    val minSteps: Long,
    val maxSteps: Long,
    val avgIntensity: ActivityIntensity,
    val intensityCounts: Map<ActivityIntensity, Int>
)

/**
 * S3014: Analytics calculation utilities for motion activity measurements.
 */
object MotionAnalytics {

    /**
     * Computes summary statistics over the provided motion entries.
     * Returns null if [entries] is empty.
     */
    fun computeSummary(entries: List<MotionHistoryEntry>): MotionSummary? {
        if (entries.isEmpty()) return null

        val count = entries.size
        val total = entries.sumOf { it.steps }
        val avg = entries.map { it.steps }.average().roundToLong()
        val min = entries.minOf { it.steps }
        val max = entries.maxOf { it.steps }

        val intensityCounts = mutableMapOf<ActivityIntensity, Int>()
        for (intensity in ActivityIntensity.entries) {
            intensityCounts[intensity] = 0
        }
        for (entry in entries) {
            val tier = ActivityIntensity.classify(entry.steps)
            intensityCounts[tier] = (intensityCounts[tier] ?: 0) + 1
        }

        return MotionSummary(
            count = count,
            totalSteps = total,
            avgSteps = avg,
            minSteps = min,
            maxSteps = max,
            avgIntensity = ActivityIntensity.classify(avg),
            intensityCounts = intensityCounts
        )
    }
}
