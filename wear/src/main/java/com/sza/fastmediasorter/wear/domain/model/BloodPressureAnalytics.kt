package com.sza.fastmediasorter.wear.domain.model

import kotlin.math.roundToInt

/**
 * S3012: Summary statistics computed over a collection of blood pressure history entries.
 *
 * @param count total number of recordings included in this summary.
 * @param avgSystolic mean systolic pressure in mmHg (rounded to nearest integer).
 * @param avgDiastolic mean diastolic pressure in mmHg (rounded to nearest integer).
 * @param minSystolic lowest systolic pressure recorded in the set.
 * @param minDiastolic lowest diastolic pressure recorded in the set.
 * @param maxSystolic highest systolic pressure recorded in the set.
 * @param maxDiastolic highest diastolic pressure recorded in the set.
 * @param avgCategory classification of the average reading.
 * @param categoryCounts frequency count of readings in each category.
 */
data class BloodPressureSummary(
    val count: Int,
    val avgSystolic: Int,
    val avgDiastolic: Int,
    val minSystolic: Int,
    val minDiastolic: Int,
    val maxSystolic: Int,
    val maxDiastolic: Int,
    val avgCategory: BloodPressureCategory,
    val categoryCounts: Map<BloodPressureCategory, Int>
)

/**
 * S3012: Analytics calculation utilities for blood pressure measurements.
 */
object BloodPressureAnalytics {

    /**
     * Computes summary statistics over the provided history entries.
     * Returns null if [entries] is empty.
     */
    fun computeSummary(entries: List<BloodPressureHistoryEntry>): BloodPressureSummary? {
        if (entries.isEmpty()) return null

        val count = entries.size
        val avgSys = entries.map { it.systolic }.average().roundToInt()
        val avgDia = entries.map { it.diastolic }.average().roundToInt()
        val minSys = entries.minOf { it.systolic }
        val minDia = entries.minOf { it.diastolic }
        val maxSys = entries.maxOf { it.systolic }
        val maxDia = entries.maxOf { it.diastolic }

        val categoryCounts = mutableMapOf<BloodPressureCategory, Int>()
        for (category in BloodPressureCategory.entries) {
            categoryCounts[category] = 0
        }
        for (entry in entries) {
            val cat = BloodPressureCategory.classify(entry.systolic, entry.diastolic)
            categoryCounts[cat] = (categoryCounts[cat] ?: 0) + 1
        }

        return BloodPressureSummary(
            count = count,
            avgSystolic = avgSys,
            avgDiastolic = avgDia,
            minSystolic = minSys,
            minDiastolic = minDia,
            maxSystolic = maxSys,
            maxDiastolic = maxDia,
            avgCategory = BloodPressureCategory.classify(avgSys, avgDia),
            categoryCounts = categoryCounts
        )
    }
}
