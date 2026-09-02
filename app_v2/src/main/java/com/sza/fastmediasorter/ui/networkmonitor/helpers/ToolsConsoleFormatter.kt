package com.sza.fastmediasorter.ui.networkmonitor.helpers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.networkmonitor.HostProbeResult
import com.sza.fastmediasorter.domain.networkmonitor.HostProbeUnavailability
import com.sza.fastmediasorter.ui.networkmonitor.sections.SubnetRefusal
import com.sza.fastmediasorter.ui.networkmonitor.sections.ToolsConsoleLine
import com.sza.fastmediasorter.ui.networkmonitor.sections.ToolsOperation

/**
 * S2347: words the tools console in the user's locale.
 *
 * The lines arrive as data because `ToolsSectionViewModel` has no `Context` and must not grow one -
 * every other ViewModel in this feature keeps the same rule. Living here rather than in the fragment
 * follows `SignalChartBinder`, the section's other `Context`-scoped formatter.
 */
internal fun Context.renderToolsConsole(lines: List<ToolsConsoleLine>): String =
    lines.joinToString(separator = "\n") { line ->
        val text = renderConsoleLine(line)
        // A finished run keeps the blank line that used to separate it from the next one.
        if (line.endsRun()) text + "\n" else text
    }

/** S2347: the progress caption above the console while an operation runs. */
internal fun Context.renderToolsProgressLabel(operation: ToolsOperation): String = when (operation) {
    is ToolsOperation.Ping -> getString(R.string.network_monitor_running_ping, operation.host)
    is ToolsOperation.TraceRoute -> getString(R.string.network_monitor_running_traceroute, operation.host)
    ToolsOperation.SubnetScan -> getString(R.string.network_monitor_running_scan_subnet)
}

private fun Context.renderConsoleLine(line: ToolsConsoleLine): String = when (line) {
    is ToolsConsoleLine.Header -> renderHeader(line.operation)
    is ToolsConsoleLine.PingStarted ->
        getString(R.string.network_monitor_console_ping_started, line.host, line.attempts)
    is ToolsConsoleLine.ProbeAttempt -> renderProbe(line.index, line.result)
    is ToolsConsoleLine.PingFinished ->
        getString(R.string.network_monitor_console_ping_finished, line.answered, line.attempts)
    is ToolsConsoleLine.TraceStarted ->
        getString(R.string.network_monitor_console_traceroute_started, line.host, line.maxHops)
    is ToolsConsoleLine.TraceHop -> renderProbe(line.hopIndex, line.result)
    is ToolsConsoleLine.TraceFinished -> renderTraceFinished(line)
    is ToolsConsoleLine.ScanStarted ->
        getString(R.string.network_monitor_console_scan_started, line.addressCount)
    is ToolsConsoleLine.ScanHostFound ->
        getString(R.string.network_monitor_result_subnet_host, line.ip, line.openPorts)
    is ToolsConsoleLine.ScanFinished ->
        getString(R.string.network_monitor_result_subnet_scan, line.hostsFound, line.probed)
    is ToolsConsoleLine.ScanRefused -> renderScanRefusal(line.reason, line.cap)
    ToolsConsoleLine.Stopped -> getString(R.string.network_monitor_stopped)
}

private fun Context.renderHeader(operation: ToolsOperation): String = when (operation) {
    is ToolsOperation.Ping -> getString(R.string.network_monitor_console_ping_header, operation.host)
    is ToolsOperation.TraceRoute ->
        getString(R.string.network_monitor_console_traceroute_header, operation.host)
    ToolsOperation.SubnetScan -> getString(R.string.network_monitor_console_subnet_header)
}

private fun Context.renderTraceFinished(line: ToolsConsoleLine.TraceFinished): String =
    if (line.reachedTarget) {
        getString(R.string.network_monitor_console_traceroute_reached, line.host, line.hops)
    } else {
        getString(R.string.network_monitor_console_traceroute_done, line.hops)
    }

private fun Context.renderProbe(index: Int, result: HostProbeResult): String = when (result) {
    is HostProbeResult.Reached -> getString(
        R.string.network_monitor_console_probe_reached,
        index,
        result.respondingAddress ?: getString(R.string.network_monitor_console_probe_target),
        result.roundTripMillis.toInt()
    )
    is HostProbeResult.HopAnswered -> getString(
        R.string.network_monitor_console_probe_hop,
        index,
        result.hopAddress,
        result.roundTripMillis.toInt()
    )
    HostProbeResult.NotReached -> getString(R.string.network_monitor_console_probe_timeout, index)
    // result.detail stays out: its KDoc reserves it for the log, and the ViewModel records it there.
    is HostProbeResult.NotMeasurable -> getString(
        R.string.network_monitor_console_probe_unavailable,
        index,
        getString(result.cause.toLabelRes())
    )
}

private fun HostProbeUnavailability.toLabelRes(): Int = when (this) {
    HostProbeUnavailability.NO_NETWORK -> R.string.network_monitor_probe_cause_no_network
    HostProbeUnavailability.NAME_NOT_RESOLVED -> R.string.network_monitor_probe_cause_name_not_resolved
    HostProbeUnavailability.MECHANISM_UNAVAILABLE -> R.string.network_monitor_probe_cause_mechanism
}

/**
 * The four refusal sentences are the Internet section's, reused verbatim so one refusal never reads
 * two ways. The mapping is duplicated there rather than shared because that section's files are
 * pending an on-device pass under S1617 and must not change under this ticket.
 */
private fun Context.renderScanRefusal(reason: SubnetRefusal, cap: Int?): String = when (reason) {
    SubnetRefusal.PERMISSION -> getString(R.string.network_monitor_result_subnet_permission)
    SubnetRefusal.SUBNET_UNKNOWN -> getString(R.string.network_monitor_result_subnet_unknown)
    SubnetRefusal.RANGE_TOO_LARGE ->
        getString(R.string.network_monitor_result_subnet_too_large, cap ?: 0)
    SubnetRefusal.RANGE_INVALID -> getString(R.string.network_monitor_result_subnet_invalid)
}

/** A run's last line keeps a blank line after it, so consecutive runs stay readable. */
private fun ToolsConsoleLine.endsRun(): Boolean = this is ToolsConsoleLine.PingFinished ||
    this is ToolsConsoleLine.TraceFinished ||
    this is ToolsConsoleLine.ScanFinished ||
    this is ToolsConsoleLine.ScanRefused ||
    this is ToolsConsoleLine.Stopped
