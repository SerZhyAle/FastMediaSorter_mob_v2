package com.sza.fastmediasorter.ui.statistics

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.di.UnitSystemEntryPoint
import com.sza.fastmediasorter.core.util.formatFileSize
import com.sza.fastmediasorter.domain.model.Quantity
import com.sza.fastmediasorter.domain.stats.MediaActionCounts
import com.sza.fastmediasorter.domain.stats.StatsMediaType
import dagger.hilt.android.EntryPointAccessors
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Pure presentation formatter for the statistics dashboard (S0473 Phase 04).
 *
 * Maps raw `Long` metric values to display strings: grouped counts, human-readable byte sizes
 * (delegated to the shared [formatFileSize] - no duplicated size table), and `h m` / `m s`
 * durations. Distribution percentages are derived from [MediaActionCounts]. [Context] resolves the
 * unit string resources and the measurement system that decides date order (S2795); numeric grouping
 * follows [Locale.getDefault] to match [formatFileSize] and stay API-23-safe on the legacy flavor.
 * No business logic here.
 */
object StatisticsRowFormatter {

    // S2795: this object is not in the injection graph, so it resolves the seam the way the project's
    // other out-of-graph formatters do. The application-scoped singletons behind it are safe to hold;
    // the system itself is read per call, so a switched setting shows on the next bind.
    @Volatile
    private var cachedSeam: UnitSystemEntryPoint? = null

    private fun seam(context: Context): UnitSystemEntryPoint = cachedSeam ?: EntryPointAccessors
        .fromApplication(context.applicationContext, UnitSystemEntryPoint::class.java)
        .also { cachedSeam = it }

    /** Locale-grouped integer, e.g. 1 248 / 1,248 depending on the device locale. */
    fun formatCount(value: Long): String =
        String.format(Locale.getDefault(), "%,d", value)

    /** Human-readable size via the shared project formatter (KB/MB/GB or exact bytes for small files). */
    fun formatBytes(context: Context, value: Long): String = formatFileSize(context, value)

    /**
     * Compact duration. Above an hour -> "Hh Mm" (e.g. "12h 30m"); below -> "Mm Ss" (e.g. "4m 05s").
     * Sub-second input still renders "0m 00s" so a non-zero counter never collapses to an empty cell.
     */
    fun formatDuration(context: Context, millis: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val hourUnit = context.getString(R.string.statistics_unit_hours_short)
        val minuteUnit = context.getString(R.string.statistics_unit_minutes_short)
        val secondUnit = context.getString(R.string.statistics_unit_seconds_short)
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d%s %d%s", hours, hourUnit, minutes, minuteUnit)
        } else {
            String.format(Locale.getDefault(), "%d%s %02d%s", minutes, minuteUnit, seconds, secondUnit)
        }
    }

    /**
     * The always-on first-launch marker, in the field order the user's measurement system sets (S2795).
     * A locale-derived order would put this date in a different shape from the same date in a file row.
     */
    fun formatDate(context: Context, epochMs: Long): String {
        val entryPoint = seam(context)
        return entryPoint.quantityFormatter()
            .format(Quantity.Date(epochMs), entryPoint.unitSystemProvider().value)
    }

    /**
     * Per-type processed-file totals for the distribution bar. Each entry's weight is
     * copied + moved + deleted; the percentage is of the grand total. Types with zero weight
     * are dropped so the legend never carries a meaningless 0 % entry.
     */
    fun buildDistribution(byType: Map<StatsMediaType, MediaActionCounts>): List<DistributionSlice> {
        val weights = byType.mapValues { (_, counts) -> counts.copied + counts.moved + counts.deleted }
            .filterValues { it > 0L }
        val total = weights.values.sum()
        if (total <= 0L) return emptyList()
        return StatsMediaType.entries
            .filter { weights.containsKey(it) }
            .map { type ->
                val weight = weights.getValue(type)
                DistributionSlice(
                    type = type,
                    value = weight,
                    percent = (weight * 100.0 / total).toInt().coerceIn(0, 100),
                )
            }
    }
}

/** One segment of the type-distribution bar: which media type, its raw weight and rounded share. */
data class DistributionSlice(
    val type: StatsMediaType,
    val value: Long,
    val percent: Int,
)
