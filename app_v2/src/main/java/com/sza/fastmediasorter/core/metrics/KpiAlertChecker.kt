package com.sza.fastmediasorter.core.metrics

import timber.log.Timber

/**
 * Runtime KPI alert checker for quality degradation detection (C2-T8).
 *
 * Evaluates the current [MetricsReport] against configurable thresholds and
 * produces a list of [KpiAlert] entries. Callers can log the alerts, surface
 * them in diagnostic UI, or send them to an analytics backend.
 *
 * ### Default thresholds (from QUALITY_BASELINE.md)
 *
 * | KPI | WARN | FAIL |
 * |-----|------|------|
 * | Scan p95 latency | > 6 000 ms | > 10 000 ms |
 * | Connection-test failure rate | > 10 % | > 30 % |
 * | Resource-save failure rate | > 0 % | > 5 % |
 *
 * ### Usage
 *
 * ```kotlin
 * val alerts = KpiAlertChecker.checkNow()
 * if (alerts.any { it.severity == KpiSeverity.FAIL }) {
 *     // Block release / notify developer
 * }
 * ```
 *
 * Thresholds are mutable `var` fields so they can be adjusted at runtime
 * for testing or flavour-specific calibration.
 */
object KpiAlertChecker {

    // ---- Thresholds -------------------------------------------------------

    /** Scan p95 latency WARN threshold (ms). Default: 6 000 ms from QUALITY_BASELINE. */
    var scanP95WarnMs: Long = 6_000L

    /** Scan p95 latency FAIL threshold (ms). Default: 10 000 ms. */
    var scanP95FailMs: Long = 10_000L

    /**
     * Connection-test failure rate that triggers a WARN (0.0–1.0).
     * Default: 10 %.
     */
    var connTestFailRateWarn: Double = 0.10

    /**
     * Connection-test failure rate that triggers a FAIL (0.0–1.0).
     * Default: 30 %.
     */
    var connTestFailRateFail: Double = 0.30

    /**
     * Resource-save failure rate that triggers a WARN (0.0–1.0).
     * Default: 0 % (any failure is noteworthy).
     */
    var resourceSaveFailRateWarn: Double = 0.00

    /**
     * Resource-save failure rate that triggers a FAIL (0.0–1.0).
     * Default: 5 %.
     */
    var resourceSaveFailRateFail: Double = 0.05

    // -----------------------------------------------------------------------

    /**
     * Evaluates all KPIs and returns the list of active alerts.
     * Returns an empty list if no metrics have been recorded yet.
     */
    fun checkNow(): List<KpiAlert> {
        val report = MetricsExporter.buildReport() ?: return emptyList()
        val alerts = mutableListOf<KpiAlert>()

        // --- Scan p95 alert ----------------------------------------------------
        report.scanSummary?.let { scan ->
            when {
                scan.p95Ms > scanP95FailMs -> alerts += KpiAlert(
                    kpi        = "scan_p95_latency",
                    severity   = KpiSeverity.FAIL,
                    actualValue = "${scan.p95Ms} ms",
                    threshold   = "$scanP95FailMs ms",
                    message    = "Scan p95 latency ${scan.p95Ms}ms exceeds FAIL threshold ${scanP95FailMs}ms"
                )
                scan.p95Ms > scanP95WarnMs -> alerts += KpiAlert(
                    kpi        = "scan_p95_latency",
                    severity   = KpiSeverity.WARN,
                    actualValue = "${scan.p95Ms} ms",
                    threshold   = "$scanP95WarnMs ms",
                    message    = "Scan p95 latency ${scan.p95Ms}ms exceeds WARN threshold ${scanP95WarnMs}ms"
                )
            }
        }

        // --- Connection-test failure-rate alert --------------------------------
        val connTotal = report.connectionTestSuccess + report.connectionTestFailure
        if (connTotal > 0) {
            val failRate = report.connectionTestFailure.toDouble() / connTotal
            when {
                failRate > connTestFailRateFail -> alerts += KpiAlert(
                    kpi         = "conn_test_fail_rate",
                    severity    = KpiSeverity.FAIL,
                    actualValue = "%.1f%%".format(failRate * 100),
                    threshold   = "%.1f%%".format(connTestFailRateFail * 100),
                    message     = "Connection-test failure rate ${"%.1f".format(failRate * 100)}% " +
                        "exceeds FAIL threshold ${"%.1f".format(connTestFailRateFail * 100)}%"
                )
                failRate > connTestFailRateWarn -> alerts += KpiAlert(
                    kpi         = "conn_test_fail_rate",
                    severity    = KpiSeverity.WARN,
                    actualValue = "%.1f%%".format(failRate * 100),
                    threshold   = "%.1f%%".format(connTestFailRateWarn * 100),
                    message     = "Connection-test failure rate ${"%.1f".format(failRate * 100)}% " +
                        "exceeds WARN threshold ${"%.1f".format(connTestFailRateWarn * 100)}%"
                )
            }
        }

        // --- Resource-save failure-rate alert ---------------------------------
        val saveTotal = report.resourceSaveSuccess + report.resourceSaveFailure
        if (saveTotal > 0) {
            val failRate = report.resourceSaveFailure.toDouble() / saveTotal
            when {
                failRate > resourceSaveFailRateFail -> alerts += KpiAlert(
                    kpi         = "resource_save_fail_rate",
                    severity    = KpiSeverity.FAIL,
                    actualValue = "%.1f%%".format(failRate * 100),
                    threshold   = "%.1f%%".format(resourceSaveFailRateFail * 100),
                    message     = "Resource-save failure rate ${"%.1f".format(failRate * 100)}% " +
                        "exceeds FAIL threshold ${"%.1f".format(resourceSaveFailRateFail * 100)}%"
                )
                failRate > resourceSaveFailRateWarn -> alerts += KpiAlert(
                    kpi         = "resource_save_fail_rate",
                    severity    = KpiSeverity.WARN,
                    actualValue = "%.1f%%".format(failRate * 100),
                    threshold   = "%.1f%%".format(resourceSaveFailRateWarn * 100),
                    message     = "Resource-save failure rate ${"%.1f".format(failRate * 100)}% " +
                        "exceeds WARN threshold ${"%.1f".format(resourceSaveFailRateWarn * 100)}%"
                )
            }
        }

        alerts.forEach { alert ->
            when (alert.severity) {
                KpiSeverity.FAIL -> Timber.e("KpiAlert FAIL - ${alert.kpi}: ${alert.message}")
                KpiSeverity.WARN -> Timber.w("KpiAlert WARN - ${alert.kpi}: ${alert.message}")
                KpiSeverity.OK   -> { /* no-op */ }
            }
        }

        if (alerts.isEmpty()) {
            Timber.d("KpiAlertChecker: all KPIs within thresholds")
        }

        return alerts.toList()
    }

    /** Resets all mutable thresholds back to their default values. Useful in tests. */
    fun resetThresholds() {
        scanP95WarnMs           = 6_000L
        scanP95FailMs           = 10_000L
        connTestFailRateWarn    = 0.10
        connTestFailRateFail    = 0.30
        resourceSaveFailRateWarn = 0.00
        resourceSaveFailRateFail = 0.05
    }
}

// ---------------------------------------------------------------------------
// Data model
// ---------------------------------------------------------------------------

/** Severity level for a [KpiAlert]. */
enum class KpiSeverity {
    /** KPI is within acceptable range. */
    OK,

    /** KPI exceeded WARN threshold - investigate. */
    WARN,

    /** KPI exceeded FAIL threshold - action required. */
    FAIL
}

/**
 * A single KPI alert produced by [KpiAlertChecker].
 *
 * @property kpi         Machine-readable KPI identifier (e.g. `"scan_p95_latency"`).
 * @property severity    Alert severity.
 * @property actualValue Human-readable current value (e.g. `"7200 ms"`).
 * @property threshold   Human-readable threshold that was exceeded.
 * @property message     Full human-readable description.
 */
data class KpiAlert(
    val kpi: String,
    val severity: KpiSeverity,
    val actualValue: String,
    val threshold: String,
    val message: String
)
