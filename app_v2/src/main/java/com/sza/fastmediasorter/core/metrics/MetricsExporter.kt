package com.sza.fastmediasorter.core.metrics

import com.sza.fastmediasorter.domain.model.ResourceType
import timber.log.Timber

/**
 * Aggregates metrics from all recorders into a unified export payload (C2-T3).
 *
 * Collects data from:
 * - [ScanMetricsRecorder] — scan timing statistics
 * - [OperationMetricsRecorder] — connection-test and resource-save counters
 *
 * ### Export formats
 *
 * - [toJsonString]   — simple JSON string suitable for file export or HTTP POST
 * - [exportToLogcat] — structured Timber log for debugging
 *
 * ### Future integration
 *
 * The exported JSON string can be sent to a backend analytics endpoint, stored in
 * `SharedPreferences`, or written to `temp/` by a diagnostic tool.
 */
object MetricsExporter {

    /**
     * Builds a full [MetricsReport] from all in-memory recorders.
     * Returns null if no metrics have been recorded in any recorder.
     */
    fun buildReport(): MetricsReport? {
        val scanSummary = ScanMetricsRecorder.summary()
        val opSnapshot = OperationMetricsRecorder.snapshot()

        val hasAnyData = scanSummary != null ||
            opSnapshot.connectionTestTotal > 0 ||
            opSnapshot.resourceSaveTotal > 0

        if (!hasAnyData) return null

        val perTypeScan = ResourceType.entries.mapNotNull { type ->
            val typeEvents = ScanMetricsRecorder.events.filter { it.resourceType == type }
            if (typeEvents.isEmpty()) return@mapNotNull null
            val sorted = typeEvents.sortedBy { it.durationMs }
            ScanTypeStats(
                resourceType = type.name,
                count = sorted.size,
                medianMs = sorted[sorted.size / 2].durationMs,
                maxMs = sorted.last().durationMs,
                avgFileCount = sorted.map { it.fileCount }.average().toLong()
            )
        }

        return MetricsReport(
            scanSummary = scanSummary,
            scanPerType = perTypeScan,
            connectionTestSuccess = opSnapshot.connectionTestSuccess,
            connectionTestFailure = opSnapshot.connectionTestFailure,
            resourceSaveSuccess = opSnapshot.resourceSaveSuccess,
            resourceSaveFailure = opSnapshot.resourceSaveFailure,
            connectionTestByType = ResourceType.entries.associate { type ->
                type.name to ConnectionTypeStats(
                    success = opSnapshot.connectionTestSuccessByType.getOrElse(type.ordinal) { 0L },
                    failure = opSnapshot.connectionTestFailureByType.getOrElse(type.ordinal) { 0L }
                )
            }
        )
    }

    /**
     * Returns a compact JSON representation of the current metrics state.
     *
     * The format is intentionally simple (no external library required):
     * valid JSON with string and number values.
     *
     * @return JSON string, or `"{}"` if no metrics recorded yet.
     */
    fun toJsonString(): String {
        val report = buildReport() ?: return "{}"
        return buildString {
            append("{\n")
            append("  \"scan\": {\n")
            report.scanSummary?.let { s ->
                append("    \"totalScans\": ${s.totalScans},\n")
                append("    \"medianMs\": ${s.medianMs},\n")
                append("    \"p95Ms\": ${s.p95Ms},\n")
                append("    \"maxMs\": ${s.maxMs},\n")
                append("    \"minMs\": ${s.minMs},\n")
                append("    \"avgFileCount\": ${s.avgFileCount}\n")
            } ?: append("    \"totalScans\": 0\n")
            append("  },\n")
            append("  \"scanPerType\": [\n")
            report.scanPerType.forEachIndexed { idx, t ->
                append("    { \"type\": \"${t.resourceType}\", \"count\": ${t.count}, \"medianMs\": ${t.medianMs}, \"maxMs\": ${t.maxMs}, \"avgFileCount\": ${t.avgFileCount} }")
                if (idx < report.scanPerType.lastIndex) append(",")
                append("\n")
            }
            append("  ],\n")
            append("  \"connectionTest\": {\n")
            append("    \"success\": ${report.connectionTestSuccess},\n")
            append("    \"failure\": ${report.connectionTestFailure},\n")
            append("    \"byType\": {\n")
            val byTypeEntries = report.connectionTestByType.entries.toList()
            byTypeEntries.forEachIndexed { idx, (type, stats) ->
                append("      \"$type\": { \"success\": ${stats.success}, \"failure\": ${stats.failure} }")
                if (idx < byTypeEntries.lastIndex) append(",")
                append("\n")
            }
            append("    }\n")
            append("  },\n")
            append("  \"resourceSave\": {\n")
            append("    \"success\": ${report.resourceSaveSuccess},\n")
            append("    \"failure\": ${report.resourceSaveFailure}\n")
            append("  }\n")
            append("}")
        }
    }

    /**
     * Logs the current metrics report to Logcat via [Timber].
     *
     * Useful for debugging or CI log analysis. Each section is logged on a
     * separate line for grep-ability.
     */
    fun exportToLogcat() {
        val report = buildReport()
        if (report == null) {
            Timber.d("MetricsExporter: no metrics recorded yet")
            return
        }
        Timber.d("MetricsExporter: === METRICS REPORT ===")
        report.scanSummary?.let { s ->
            Timber.d(
                "MetricsExporter: scan total=${s.totalScans} median=${s.medianMs}ms " +
                    "p95=${s.p95Ms}ms max=${s.maxMs}ms min=${s.minMs}ms avgFiles=${s.avgFileCount}"
            )
        }
        report.scanPerType.forEach { t ->
            Timber.d(
                "MetricsExporter: scan[${t.resourceType}] count=${t.count} " +
                    "median=${t.medianMs}ms max=${t.maxMs}ms avgFiles=${t.avgFileCount}"
            )
        }
        Timber.d(
            "MetricsExporter: connTest ok=${report.connectionTestSuccess} " +
                "fail=${report.connectionTestFailure}"
        )
        report.connectionTestByType.forEach { (type, stats) ->
            Timber.d("MetricsExporter: connTest[$type] ok=${stats.success} fail=${stats.failure}")
        }
        Timber.d(
            "MetricsExporter: resourceSave ok=${report.resourceSaveSuccess} " +
                "fail=${report.resourceSaveFailure}"
        )
        Timber.d("MetricsExporter: =====================")
    }
}

// ---------------------------------------------------------------------------
// Data model
// ---------------------------------------------------------------------------

/**
 * Unified metrics report covering all instrumented subsystems.
 *
 * All counts are non-negative. Fields are nullable only when the corresponding
 * recorder has no data (e.g. [scanSummary] is null before the first scan).
 */
data class MetricsReport(
    val scanSummary: ScanMetricsSummary?,
    val scanPerType: List<ScanTypeStats>,
    val connectionTestSuccess: Long,
    val connectionTestFailure: Long,
    val resourceSaveSuccess: Long,
    val resourceSaveFailure: Long,
    val connectionTestByType: Map<String, ConnectionTypeStats>
) {
    /** Overall connection-test success rate in (0..1], or null if no tests. */
    val connectionTestSuccessRate: Double?
        get() {
            val total = connectionTestSuccess + connectionTestFailure
            return if (total == 0L) null else connectionTestSuccess.toDouble() / total
        }

    /** Overall resource-save success rate in (0..1], or null if no saves. */
    val resourceSaveSuccessRate: Double?
        get() {
            val total = resourceSaveSuccess + resourceSaveFailure
            return if (total == 0L) null else resourceSaveSuccess.toDouble() / total
        }
}

/** Per-resource-type scan timing statistics. */
data class ScanTypeStats(
    val resourceType: String,
    val count: Int,
    val medianMs: Long,
    val maxMs: Long,
    val avgFileCount: Long
)

/** Success/failure pair for connection tests broken down by type. */
data class ConnectionTypeStats(
    val success: Long,
    val failure: Long
)
