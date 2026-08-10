package com.sza.fastmediasorter.ui.networkmonitor.sections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.model.networkmonitor.ActiveLink
import com.sza.fastmediasorter.domain.model.networkmonitor.MonitorSection
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMonitorSnapshot
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkTransport
import com.sza.fastmediasorter.domain.model.networkmonitor.SignalSample
import com.sza.fastmediasorter.domain.model.networkmonitor.SignalSeries
import com.sza.fastmediasorter.domain.model.networkmonitor.TrafficRate
import com.sza.fastmediasorter.domain.repository.NetworkMonitorRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.NetworkHost
import com.sza.fastmediasorter.domain.usecase.networkmonitor.CgnatVerdict
import com.sza.fastmediasorter.domain.usecase.networkmonitor.CheckSelectedResourceUseCase
import com.sza.fastmediasorter.domain.usecase.networkmonitor.ExternalIpState
import com.sza.fastmediasorter.domain.usecase.networkmonitor.MeasureThroughputUseCase
import com.sza.fastmediasorter.domain.usecase.networkmonitor.ObserveTrafficRateUseCase
import com.sza.fastmediasorter.domain.usecase.networkmonitor.ResolveExternalIpUseCase
import com.sza.fastmediasorter.domain.usecase.networkmonitor.ResourceCheckState
import com.sza.fastmediasorter.domain.usecase.networkmonitor.ScanSubnetUseCase
import com.sza.fastmediasorter.domain.usecase.networkmonitor.SubnetScanState
import com.sza.fastmediasorter.domain.usecase.networkmonitor.SubnetScanTarget
import com.sza.fastmediasorter.domain.usecase.networkmonitor.ThroughputMode
import com.sza.fastmediasorter.domain.usecase.networkmonitor.ThroughputState
import com.sza.fastmediasorter.ui.networkmonitor.helpers.appendSample
import com.sza.fastmediasorter.ui.networkmonitor.helpers.emptySignalWindow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** S1433: the four manual operations the Internet section offers. */
enum class InternetAction { RESOURCE_CHECK, SUBNET_SCAN, EXTERNAL_IP, SPEED_TEST }

/** S1433: what the section must say before it spends the user's traffic or reveals their address. */
enum class InternetConfirmation {
    SPEED_TEST_TRAFFIC,
    SUBNET_SCAN_TRAFFIC,
    EXTERNAL_IP_DISCLOSURE,
    METERED_NETWORK,
}

/** S1433: one saved resource the resource-aware actions can be pointed at. */
data class InternetResource(val id: Long, val name: String)

/** S1433: how the last finished operation ended. */
sealed interface InternetActionResult {

    data class ExternalIp(val address: String, val verdict: CgnatVerdict) : InternetActionResult

    /** No echo service answered, so there is no address and no CGNAT comparison to report. */
    data object ExternalIpUnavailable : InternetActionResult

    data class SubnetScan(
        val hostsFound: Int,
        val probed: Int,
        val hosts: List<NetworkHost>,
    ) : InternetActionResult

    data class SubnetRefused(val reason: SubnetRefusal, val cap: Int?) : InternetActionResult

    data class SpeedTest(val downMbps: Double, val upMbps: Double?) : InternetActionResult

    /**
     * Carries no reason text. The underlying failure is an `IOException` message, which
     * `COMMUNICATION_POLICY` §2.2 keeps out of the headline; it is logged instead, where a developer
     * reading a bug report can still find it.
     */
    data object SpeedTestFailed : InternetActionResult

    /** The network started charging between the warning and the run, so no bytes were moved. */
    data object SpeedTestMetered : InternetActionResult

    data class ResourceReachable(val detail: String) : InternetActionResult

    /** Same reasoning as [SpeedTestFailed]: the repository's raw failure text is logged, not shown. */
    data object ResourceUnreachable : InternetActionResult

    data object ResourceMissing : InternetActionResult
}

/** S1433: why a scan never started. Each has its own sentence; none is an exception message. */
enum class SubnetRefusal { PERMISSION, SUBNET_UNKNOWN, RANGE_TOO_LARGE, RANGE_INVALID }

/**
 * S1433: the live half of the section - the path diagram's facts and the traffic chart.
 *
 * [hasInternet] is the platform's own validation verdict rather than a reachability guess: a network can be
 * connected and useless, and only that flag tells the two apart.
 */
data class InternetSectionUiState(
    val link: ActiveLink?,
    val hasInternet: Boolean,
    val traffic: MonitorSection<SignalSeries>,
    val currentRate: TrafficRate?,
) {

    companion object {

        val Empty = InternetSectionUiState(
            link = null,
            hasInternet = false,
            traffic = emptySignalWindow(),
            currentRate = null,
        )
    }
}

/** S1433: what the manual half of the section is doing right now. */
data class InternetActionState(
    val running: InternetAction? = null,
    val progressFraction: Float? = null,
    val result: InternetActionResult? = null,
    val confirmation: InternetConfirmation? = null,
)

/**
 * S1433: composes the Internet and resources subscreen and owns its four manual operations.
 *
 * One operation at a time, by construction: every action replaces [activeJob], so a second tap cannot leave
 * a scan running behind a speed test and the cancel affordance always refers to something unambiguous.
 *
 * Nothing here starts on its own. The tactical plan's "no automatic traffic" invariant is kept by there
 * being no automatic caller at all - each `run*` is reachable only from [onActionRequested], and the ones
 * that spend traffic pass through [confirmationFor] first.
 */
@HiltViewModel
class InternetSectionViewModel @Inject constructor(
    repository: NetworkMonitorRepository,
    observeTrafficRate: ObserveTrafficRateUseCase,
    resourceRepository: ResourceRepository,
    private val resolveExternalIp: ResolveExternalIpUseCase,
    private val scanSubnet: ScanSubnetUseCase,
    private val measureThroughput: MeasureThroughputUseCase,
    private val checkSelectedResource: CheckSelectedResourceUseCase,
) : ViewModel() {

    private val snapshot: StateFlow<NetworkMonitorSnapshot?> = repository.observeSnapshot()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val uiState: StateFlow<InternetSectionUiState> = combine(
        snapshot,
        observeTrafficRate().scan(TrafficWindow.EMPTY) { window, section -> window.fold(section) },
    ) { current, traffic -> current.toUiState(traffic) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), InternetSectionUiState.Empty)

    val resources: StateFlow<List<InternetResource>> = resourceRepository.getAllResources()
        .map { saved -> saved.map { InternetResource(it.id, it.name) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    private val _actionState = MutableStateFlow(InternetActionState())
    val actionState: StateFlow<InternetActionState> = _actionState.asStateFlow()

    /** Null means the internet itself rather than a saved resource - the picker's first entry. */
    private var selectedResourceId: Long? = null

    private var subnetRange: SubnetScanTarget = SubnetScanTarget.DeviceSubnet

    private var activeJob: Job? = null

    private var pendingAction: InternetAction? = null

    /**
     * Which actions have already shown their one-time warning this session.
     *
     * Per open screen rather than persisted: the warning exists so nobody spends traffic without meaning
     * to, and a flag stored across installs would silence it for a user who has forgotten it.
     */
    private val warned = mutableSetOf<InternetAction>()

    private var allowMetered = false

    fun onResourceSelected(resourceId: Long?) {
        selectedResourceId = resourceId
    }

    /** Blank ends mean "this device's own subnet"; anything else is taken as an explicit range. */
    fun onSubnetRangeEntered(firstAddress: String, lastAddress: String) {
        subnetRange = if (firstAddress.isBlank() || lastAddress.isBlank()) {
            SubnetScanTarget.DeviceSubnet
        } else {
            SubnetScanTarget.AddressRange(firstAddress.trim(), lastAddress.trim())
        }
    }

    fun onActionRequested(action: InternetAction) {
        val confirmation = confirmationFor(action)
        if (confirmation == null) {
            start(action)
            return
        }
        pendingAction = action
        _actionState.update { it.copy(confirmation = confirmation) }
    }

    fun onConfirmationAccepted() {
        val action = pendingAction ?: return
        allowMetered = _actionState.value.confirmation == InternetConfirmation.METERED_NETWORK
        pendingAction = null
        warned += action
        _actionState.update { it.copy(confirmation = null) }
        start(action)
    }

    fun onConfirmationDismissed() {
        pendingAction = null
        _actionState.update { it.copy(confirmation = null) }
    }

    /** Cancelling clears the running action here rather than in the job, which is already gone. */
    fun onCancelRequested() {
        activeJob?.cancel()
        activeJob = null
        _actionState.update { it.copy(running = null, progressFraction = null) }
    }

    private fun start(action: InternetAction) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _actionState.value = InternetActionState(running = action)
            try {
                run(action)
            } finally {
                _actionState.update { it.copy(running = null, progressFraction = null) }
            }
        }
    }

    private suspend fun run(action: InternetAction) = when (action) {
        InternetAction.EXTERNAL_IP -> runExternalIp()
        InternetAction.SUBNET_SCAN -> runSubnetScan()
        InternetAction.SPEED_TEST -> runSpeedTest()
        InternetAction.RESOURCE_CHECK -> runResourceCheck()
    }

    private suspend fun runExternalIp() = resolveExternalIp(networkLabel()).collect { state ->
        when (state) {
            ExternalIpState.Resolving -> setProgress(null)
            is ExternalIpState.Resolved ->
                setResult(InternetActionResult.ExternalIp(state.address, state.verdict))
            ExternalIpState.Unavailable -> setResult(InternetActionResult.ExternalIpUnavailable)
        }
    }

    private suspend fun runSubnetScan() {
        val found = mutableListOf<NetworkHost>()
        scanSubnet(subnetRange, networkLabel()).collect { state ->
            when (state) {
                is SubnetScanState.Started -> setProgress(0f)
                is SubnetScanState.Progress -> setProgress(fractionOf(state.probed, state.total))
                is SubnetScanState.HostFound -> found += state.host
                is SubnetScanState.Finished ->
                    setResult(InternetActionResult.SubnetScan(state.hostsFound, state.probed, found.toList()))
                SubnetScanState.LocalNetworkPermissionMissing -> refuse(SubnetRefusal.PERMISSION, null)
                SubnetScanState.SubnetUnknown -> refuse(SubnetRefusal.SUBNET_UNKNOWN, null)
                is SubnetScanState.RangeTooLarge -> refuse(SubnetRefusal.RANGE_TOO_LARGE, state.cap)
                is SubnetScanState.RangeInvalid -> refuse(SubnetRefusal.RANGE_INVALID, null)
            }
        }
    }

    private suspend fun runSpeedTest() =
        measureThroughput(speedTestMode(), networkLabel(), allowMetered).collect { state ->
            when (state) {
                ThroughputState.MeteredNetwork -> setResult(InternetActionResult.SpeedTestMetered)
                is ThroughputState.Progress -> setProgress(state.fraction)
                is ThroughputState.Complete ->
                    setResult(InternetActionResult.SpeedTest(state.downMbps, state.upMbps))
                ThroughputState.ResourceMissing -> setResult(InternetActionResult.ResourceMissing)
                is ThroughputState.Failed -> {
                    Timber.i("Network Monitor speed test did not finish: %s", state.reason)
                    setResult(InternetActionResult.SpeedTestFailed)
                }
            }
        }

    private suspend fun runResourceCheck() {
        val resourceId = selectedResourceId ?: return
        checkSelectedResource(resourceId, networkLabel()).collect { state ->
            when (state) {
                ResourceCheckState.Checking -> setProgress(null)
                is ResourceCheckState.Reachable ->
                    setResult(InternetActionResult.ResourceReachable(state.detail))
                is ResourceCheckState.Unreachable -> {
                    Timber.i("Network Monitor resource check failed: %s", state.reason)
                    setResult(InternetActionResult.ResourceUnreachable)
                }
                ResourceCheckState.ResourceMissing -> setResult(InternetActionResult.ResourceMissing)
            }
        }
    }

    private fun speedTestMode(): ThroughputMode =
        selectedResourceId?.let { ThroughputMode.Resource(it) } ?: ThroughputMode.Internet

    /**
     * The metered warning is raised every time, the other three only once each.
     *
     * A metered network keeps costing money on the second run as much as on the first, while the traffic
     * notice and the address disclosure are facts the user has already been told once this session.
     */
    private fun confirmationFor(action: InternetAction): InternetConfirmation? = when (action) {
        InternetAction.SPEED_TEST -> when {
            isMetered() -> InternetConfirmation.METERED_NETWORK
            action in warned -> null
            else -> InternetConfirmation.SPEED_TEST_TRAFFIC
        }
        InternetAction.SUBNET_SCAN ->
            if (action in warned) null else InternetConfirmation.SUBNET_SCAN_TRAFFIC
        InternetAction.EXTERNAL_IP ->
            if (action in warned) null else InternetConfirmation.EXTERNAL_IP_DISCLOSURE
        InternetAction.RESOURCE_CHECK -> null
    }

    private fun isMetered(): Boolean =
        snapshot.value?.networks?.firstOrNull { it.isActive }?.isMetered == true

    /**
     * The label the history row carries, built here because this screen already holds the snapshot -
     * resolving it inside a use case would start device observation for the length of one measurement.
     */
    private fun networkLabel(): String {
        val active = snapshot.value?.networks?.firstOrNull { it.isActive } ?: return UNKNOWN_NETWORK
        return if (active.transport == NetworkTransport.WIFI) {
            snapshot.value?.wifi?.data?.ssid ?: active.transport.name
        } else {
            active.transport.name
        }
    }

    private fun setProgress(fraction: Float?) {
        _actionState.update { it.copy(progressFraction = fraction) }
    }

    private fun setResult(result: InternetActionResult) {
        _actionState.update { it.copy(result = result, progressFraction = null) }
    }

    private fun refuse(reason: SubnetRefusal, cap: Int?) {
        setResult(InternetActionResult.SubnetRefused(reason, cap))
    }

    private fun fractionOf(probed: Int, total: Int): Float? =
        if (total > 0) probed.toFloat() / total else null

    private companion object {

        /** Written into the exported history, never shown - hence a literal rather than a string key. */
        const val UNKNOWN_NETWORK = "unknown"
    }
}

/**
 * The traffic chart's accumulated window plus the reading it ends on.
 *
 * Both come from one fold rather than two collections of the same sampler: the counters are read once a
 * second and reading them twice would double the work for one screen.
 */
private data class TrafficWindow(
    val series: MonitorSection<SignalSeries>,
    val latest: TrafficRate?,
) {

    fun fold(section: MonitorSection<TrafficRate>): TrafficWindow {
        val rate = section.data
        val reading = if (rate == null) {
            MonitorSection.absent<SignalSample>(section.availability)
        } else {
            MonitorSection.available(SignalSample(rate.takenAtMillis, rate.totalBytesPerSecond))
        }
        return TrafficWindow(series.appendSample(reading), rate)
    }

    companion object {

        val EMPTY = TrafficWindow(emptySignalWindow(), null)
    }
}

private fun NetworkMonitorSnapshot?.toUiState(traffic: TrafficWindow): InternetSectionUiState {
    val active = this?.networks?.firstOrNull { it.isActive }
    return InternetSectionUiState(
        link = this?.activeLink,
        hasInternet = active?.hasValidatedInternet == true,
        traffic = traffic.series,
        currentRate = traffic.latest,
    )
}
