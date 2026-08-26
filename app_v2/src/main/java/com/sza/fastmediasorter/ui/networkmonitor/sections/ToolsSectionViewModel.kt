package com.sza.fastmediasorter.ui.networkmonitor.sections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.data.repository.networkmonitor.ProbeTargetStore
import com.sza.fastmediasorter.domain.networkmonitor.HostProbeResult
import com.sza.fastmediasorter.domain.usecase.networkmonitor.PingHostState
import com.sza.fastmediasorter.domain.usecase.networkmonitor.PingHostUseCase
import com.sza.fastmediasorter.domain.usecase.networkmonitor.SubnetScanState
import com.sza.fastmediasorter.domain.usecase.networkmonitor.ScanSubnetUseCase
import com.sza.fastmediasorter.domain.usecase.networkmonitor.SubnetScanTarget
import com.sza.fastmediasorter.domain.usecase.networkmonitor.TraceRouteState
import com.sza.fastmediasorter.domain.usecase.networkmonitor.TraceRouteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ToolsSectionUiState(
    val isRunning: Boolean = false,
    val runningActionLabel: String? = null,
    val consoleOutput: String = "",
    val targets: List<String> = emptyList(),
    val rangeFirst: String = "",
    val rangeLast: String = "",
)

@HiltViewModel
class ToolsSectionViewModel @Inject constructor(
    private val pingHostUseCase: PingHostUseCase,
    private val traceRouteUseCase: TraceRouteUseCase,
    private val scanSubnetUseCase: ScanSubnetUseCase,
    private val probeTargetStore: ProbeTargetStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToolsSectionUiState())
    val uiState: StateFlow<ToolsSectionUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    init {
        viewModelScope.launch {
            probeTargetStore.targets.collect { targetList ->
                _uiState.update { it.copy(targets = targetList) }
            }
        }
        viewModelScope.launch {
            val range = scanSubnetUseCase.deviceSubnetRange()
            if (range != null) {
                _uiState.update { it.copy(rangeFirst = range.firstAddress, rangeLast = range.lastAddress) }
            }
        }
    }

    fun startPing(host: String, networkLabel: String) {
        val cleanHost = host.trim()
        if (cleanHost.isEmpty()) return
        probeTargetStore.addTarget(cleanHost)

        activeJob?.cancel()
        _uiState.update {
            it.copy(
                isRunning = true,
                runningActionLabel = "Ping $cleanHost...",
                consoleOutput = appendConsole(it.consoleOutput, "--- Ping $cleanHost ---")
            )
        }

        activeJob = viewModelScope.launch {
            pingHostUseCase(cleanHost, networkLabel).collect { state ->
                when (state) {
                    is PingHostState.Started -> {
                        _uiState.update {
                            it.copy(consoleOutput = appendConsole(it.consoleOutput, "Pinging $cleanHost (${state.totalAttempts} attempts)..."))
                        }
                    }
                    is PingHostState.Attempt -> {
                        val line = formatHostProbeResult(state.attemptIndex, state.result)
                        _uiState.update {
                            it.copy(consoleOutput = appendConsole(it.consoleOutput, line))
                        }
                    }
                    is PingHostState.Finished -> {
                        _uiState.update {
                            it.copy(
                                isRunning = false,
                                runningActionLabel = null,
                                consoleOutput = appendConsole(it.consoleOutput, "Ping finished: ${state.succeededCount}/${state.attempts.size} replied.\n")
                            )
                        }
                    }
                }
            }
        }
    }

    fun startTraceRoute(host: String, networkLabel: String) {
        val cleanHost = host.trim()
        if (cleanHost.isEmpty()) return
        probeTargetStore.addTarget(cleanHost)

        activeJob?.cancel()
        _uiState.update {
            it.copy(
                isRunning = true,
                runningActionLabel = "Traceroute $cleanHost...",
                consoleOutput = appendConsole(it.consoleOutput, "--- Traceroute $cleanHost ---")
            )
        }

        activeJob = viewModelScope.launch {
            traceRouteUseCase(cleanHost, networkLabel).collect { state ->
                when (state) {
                    is TraceRouteState.Started -> {
                        _uiState.update {
                            it.copy(consoleOutput = appendConsole(it.consoleOutput, "Tracing route to $cleanHost (max ${state.maxHops} hops)..."))
                        }
                    }
                    is TraceRouteState.Hop -> {
                        val line = "Hop ${state.hopIndex}: ${formatHostProbeResult(state.hopIndex, state.result)}"
                        _uiState.update {
                            it.copy(consoleOutput = appendConsole(it.consoleOutput, line))
                        }
                    }
                    is TraceRouteState.Finished -> {
                        val summary = if (state.reachedTarget) "Target $cleanHost reached in ${state.hops.size} hop(s)." else "Trace complete (${state.hops.size} hop(s))."
                        _uiState.update {
                            it.copy(
                                isRunning = false,
                                runningActionLabel = null,
                                consoleOutput = appendConsole(it.consoleOutput, "$summary\n")
                            )
                        }
                    }
                }
            }
        }
    }

    fun startSubnetScan(first: String, last: String, networkLabel: String) {
        val target = if (first.isBlank() || last.isBlank()) {
            SubnetScanTarget.DeviceSubnet
        } else {
            SubnetScanTarget.AddressRange(first.trim(), last.trim())
        }

        activeJob?.cancel()
        _uiState.update {
            it.copy(
                isRunning = true,
                runningActionLabel = "Subnet Scan...",
                consoleOutput = appendConsole(it.consoleOutput, "--- Subnet Scan ---")
            )
        }

        activeJob = viewModelScope.launch {
            scanSubnetUseCase(target, networkLabel).collect { state ->
                when (state) {
                    is SubnetScanState.Started -> {
                        _uiState.update {
                            it.copy(consoleOutput = appendConsole(it.consoleOutput, "Scanning ${state.addressCount} addresses..."))
                        }
                    }
                    is SubnetScanState.HostFound -> {
                        val hostLine = "Found: ${state.host.ip} (${state.host.openPorts.joinToString()})"
                        _uiState.update {
                            it.copy(consoleOutput = appendConsole(it.consoleOutput, hostLine))
                        }
                    }
                    is SubnetScanState.Progress -> {
                        // Keep progress updated in UI
                    }
                    is SubnetScanState.Finished -> {
                        _uiState.update {
                            it.copy(
                                isRunning = false,
                                runningActionLabel = null,
                                consoleOutput = appendConsole(it.consoleOutput, "Scan complete: ${state.hostsFound} host(s) found on ${state.probed} addresses.\n")
                            )
                        }
                    }
                    is SubnetScanState.RangeInvalid -> {
                        _uiState.update {
                            it.copy(
                                isRunning = false,
                                runningActionLabel = null,
                                consoleOutput = appendConsole(it.consoleOutput, "Invalid range: ${state.reason}\n")
                            )
                        }
                    }
                    is SubnetScanState.RangeTooLarge -> {
                        _uiState.update {
                            it.copy(
                                isRunning = false,
                                runningActionLabel = null,
                                consoleOutput = appendConsole(it.consoleOutput, "Range too large: ${state.requested} (max ${state.cap})\n")
                            )
                        }
                    }
                    SubnetScanState.LocalNetworkPermissionMissing -> {
                        _uiState.update {
                            it.copy(
                                isRunning = false,
                                runningActionLabel = null,
                                consoleOutput = appendConsole(it.consoleOutput, "Permission missing for local network scan.\n")
                            )
                        }
                    }
                    SubnetScanState.SubnetUnknown -> {
                        _uiState.update {
                            it.copy(
                                isRunning = false,
                                runningActionLabel = null,
                                consoleOutput = appendConsole(it.consoleOutput, "No active subnet to scan.\n")
                            )
                        }
                    }
                }
            }
        }
    }

    fun cancelActiveOperation() {
        activeJob?.cancel()
        activeJob = null
        _uiState.update {
            it.copy(
                isRunning = false,
                runningActionLabel = null,
                consoleOutput = appendConsole(it.consoleOutput, "Operation cancelled.\n")
            )
        }
    }

    fun clearConsole() {
        _uiState.update { it.copy(consoleOutput = "") }
    }

    fun removeTarget(target: String) {
        probeTargetStore.removeTarget(target)
    }

    fun clearAllTargets() {
        probeTargetStore.clearAll()
    }

    private fun appendConsole(current: String, line: String): String {
        return if (current.isEmpty()) line else "$current\n$line"
    }

    private fun formatHostProbeResult(index: Int, result: HostProbeResult): String = when (result) {
        is HostProbeResult.Reached -> "[$index] Reached ${result.respondingAddress ?: "target"} (${result.roundTripMillis.toInt()} ms)"
        is HostProbeResult.HopAnswered -> "[$index] Hop ${result.hopAddress} (${result.roundTripMillis.toInt()} ms)"
        HostProbeResult.NotReached -> "[$index] Request timed out (did not answer)"
        is HostProbeResult.NotMeasurable -> "[$index] Could not measure (${result.cause.name}: ${result.detail ?: "unavailable"})"
    }
}
