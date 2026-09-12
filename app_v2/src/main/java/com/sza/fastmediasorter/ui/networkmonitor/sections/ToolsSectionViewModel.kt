package com.sza.fastmediasorter.ui.networkmonitor.sections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.data.repository.networkmonitor.ProbeTargetStore
import com.sza.fastmediasorter.domain.networkmonitor.HostProbeResult
import com.sza.fastmediasorter.domain.usecase.networkmonitor.PingHostState
import com.sza.fastmediasorter.domain.usecase.networkmonitor.PingHostUseCase
import com.sza.fastmediasorter.domain.usecase.networkmonitor.ScanSubnetUseCase
import com.sza.fastmediasorter.domain.usecase.networkmonitor.SubnetScanState
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
import timber.log.Timber
import javax.inject.Inject

/** S2347: which diagnostic is running, and against what. The fragment turns this into words. */
sealed interface ToolsOperation {

    data class Ping(val host: String) : ToolsOperation

    data class TraceRoute(val host: String) : ToolsOperation

    data object SubnetScan : ToolsOperation
}

/**
 * S2347: one console line held as data rather than as a finished sentence.
 *
 * The section used to build each line as an English literal inside this ViewModel, which put every
 * lexeme out of reach of `strings.xml` and therefore out of reach of the thirteen-locale gate. No
 * ViewModel in this feature holds a `Context`, so the only place a line can be worded is the
 * fragment - the same split the Internet section already uses for these very use-case states.
 */
sealed interface ToolsConsoleLine {

    data class Header(val operation: ToolsOperation) : ToolsConsoleLine

    data class PingStarted(val host: String, val attempts: Int) : ToolsConsoleLine

    data class ProbeAttempt(val index: Int, val result: HostProbeResult) : ToolsConsoleLine

    data class PingFinished(val answered: Int, val attempts: Int) : ToolsConsoleLine

    data class TraceStarted(val host: String, val maxHops: Int) : ToolsConsoleLine

    data class TraceHop(val hopIndex: Int, val result: HostProbeResult) : ToolsConsoleLine

    data class TraceFinished(val host: String, val hops: Int, val reachedTarget: Boolean) : ToolsConsoleLine

    data class ScanStarted(val addressCount: Int) : ToolsConsoleLine

    data class ScanHostFound(val ip: String, val openPorts: String) : ToolsConsoleLine

    data class ScanFinished(val hostsFound: Int, val probed: Int) : ToolsConsoleLine

    /**
     * Carries no reason text: the refusal sentences are the Internet section's, one per
     * [SubnetRefusal] arm, and the underlying raw reason goes to the log instead.
     */
    data class ScanRefused(val reason: SubnetRefusal, val cap: Int?) : ToolsConsoleLine

    data object Stopped : ToolsConsoleLine
}

data class ToolsSectionUiState(
    val isRunning: Boolean = false,
    val runningOperation: ToolsOperation? = null,
    val consoleLines: List<ToolsConsoleLine> = emptyList(),
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

        val operation = ToolsOperation.Ping(cleanHost)
        beginOperation(operation)

        activeJob = viewModelScope.launch {
            pingHostUseCase(cleanHost, networkLabel).collect { state ->
                when (state) {
                    is PingHostState.Started ->
                        appendLine(ToolsConsoleLine.PingStarted(cleanHost, state.totalAttempts))
                    is PingHostState.Attempt -> {
                        logUnmeasurable(state.result)
                        appendLine(ToolsConsoleLine.ProbeAttempt(state.attemptIndex, state.result))
                    }
                    is PingHostState.Finished ->
                        finishOperation(ToolsConsoleLine.PingFinished(state.succeededCount, state.attempts.size))
                }
            }
        }
    }

    fun startTraceRoute(host: String, networkLabel: String) {
        val cleanHost = host.trim()
        if (cleanHost.isEmpty()) return
        probeTargetStore.addTarget(cleanHost)

        val operation = ToolsOperation.TraceRoute(cleanHost)
        beginOperation(operation)

        activeJob = viewModelScope.launch {
            traceRouteUseCase(cleanHost, networkLabel).collect { state ->
                when (state) {
                    is TraceRouteState.Started ->
                        appendLine(ToolsConsoleLine.TraceStarted(cleanHost, state.maxHops))
                    is TraceRouteState.Hop -> {
                        logUnmeasurable(state.result)
                        appendLine(ToolsConsoleLine.TraceHop(state.hopIndex, state.result))
                    }
                    is TraceRouteState.Finished -> finishOperation(
                        ToolsConsoleLine.TraceFinished(cleanHost, state.hops.size, state.reachedTarget)
                    )
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

        beginOperation(ToolsOperation.SubnetScan)

        activeJob = viewModelScope.launch {
            scanSubnetUseCase(target, networkLabel).collect { state -> onSubnetScanState(state) }
        }
    }

    private fun onSubnetScanState(state: SubnetScanState) {
        when (state) {
            is SubnetScanState.Started -> appendLine(ToolsConsoleLine.ScanStarted(state.addressCount))
            is SubnetScanState.HostFound -> appendLine(
                ToolsConsoleLine.ScanHostFound(state.host.ip, state.host.openPorts.joinToString())
            )
            is SubnetScanState.Progress -> Unit // Progress drives no console line; the spinner shows it.
            is SubnetScanState.Finished ->
                finishOperation(ToolsConsoleLine.ScanFinished(state.hostsFound, state.probed))
            is SubnetScanState.RangeInvalid -> {
                Timber.d("Subnet scan refused, range invalid: %s", state.reason)
                finishOperation(ToolsConsoleLine.ScanRefused(SubnetRefusal.RANGE_INVALID, null))
            }
            is SubnetScanState.RangeTooLarge -> {
                Timber.d("Subnet scan refused, %d addresses requested over a cap of %d", state.requested, state.cap)
                finishOperation(ToolsConsoleLine.ScanRefused(SubnetRefusal.RANGE_TOO_LARGE, state.cap))
            }
            SubnetScanState.LocalNetworkPermissionMissing ->
                finishOperation(ToolsConsoleLine.ScanRefused(SubnetRefusal.PERMISSION, null))
            SubnetScanState.SubnetUnknown ->
                finishOperation(ToolsConsoleLine.ScanRefused(SubnetRefusal.SUBNET_UNKNOWN, null))
        }
    }

    fun cancelActiveOperation() {
        activeJob?.cancel()
        activeJob = null
        finishOperation(ToolsConsoleLine.Stopped)
    }

    fun clearConsole() {
        _uiState.update { it.copy(consoleLines = emptyList()) }
    }

    fun removeTarget(target: String) {
        probeTargetStore.removeTarget(target)
    }

    fun clearAllTargets() {
        probeTargetStore.clearAll()
    }

    private fun beginOperation(operation: ToolsOperation) {
        activeJob?.cancel()
        _uiState.update {
            it.copy(
                isRunning = true,
                runningOperation = operation,
                consoleLines = it.consoleLines + ToolsConsoleLine.Header(operation)
            )
        }
    }

    private fun appendLine(line: ToolsConsoleLine) {
        _uiState.update { it.copy(consoleLines = it.consoleLines + line) }
    }

    private fun finishOperation(line: ToolsConsoleLine) {
        _uiState.update {
            it.copy(
                isRunning = false,
                runningOperation = null,
                consoleLines = it.consoleLines + line
            )
        }
    }

    /**
     * [HostProbeResult.NotMeasurable.detail] is documented as log material, not user material, so the
     * cause and the detail are recorded here and only the cause reaches the state.
     */
    private fun logUnmeasurable(result: HostProbeResult) {
        if (result is HostProbeResult.NotMeasurable) {
            Timber.d("Host probe unavailable: %s (%s)", result.cause, result.detail ?: "no detail")
        }
    }
}
