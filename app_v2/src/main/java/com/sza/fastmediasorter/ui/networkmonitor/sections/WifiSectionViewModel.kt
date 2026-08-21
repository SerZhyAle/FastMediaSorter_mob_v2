package com.sza.fastmediasorter.ui.networkmonitor.sections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.domain.model.networkmonitor.ActiveLink
import com.sza.fastmediasorter.domain.model.networkmonitor.MonitorSection
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMonitorSnapshot
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkTransport
import com.sza.fastmediasorter.domain.model.networkmonitor.SectionAvailability
import com.sza.fastmediasorter.domain.model.networkmonitor.SignalSeries
import com.sza.fastmediasorter.domain.model.networkmonitor.WifiEntry
import com.sza.fastmediasorter.domain.radio.RadioControlContract
import com.sza.fastmediasorter.domain.radio.RadioKind
import com.sza.fastmediasorter.domain.repository.NetworkMonitorRepository
import com.sza.fastmediasorter.domain.usecase.networkmonitor.ObserveWifiSignalUseCase
import com.sza.fastmediasorter.ui.networkmonitor.helpers.ChartWindowEvent
import com.sza.fastmediasorter.ui.networkmonitor.helpers.ChartWindowResetManager
import com.sza.fastmediasorter.ui.networkmonitor.helpers.RadioToggleOutcome
import com.sza.fastmediasorter.ui.networkmonitor.helpers.RadioToggleState
import com.sza.fastmediasorter.ui.networkmonitor.helpers.appendSample
import com.sza.fastmediasorter.ui.networkmonitor.helpers.collectingSignalWindow
import com.sza.fastmediasorter.ui.networkmonitor.helpers.emptySignalWindow
import com.sza.fastmediasorter.ui.networkmonitor.helpers.withChartResets
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * S1433: everything the Wi-Fi subscreen renders, already decided.
 *
 * [link] is the active link, but only while the active transport is Wi-Fi. Step 08.2 asks for "the active
 * network's link", and read literally that puts a mobile interface, IP and DNS under a Wi-Fi heading the
 * moment Wi-Fi drops - which a diagnostic screen's reader would take for their Wi-Fi address. Strategic
 * §3.1.1 item 3 scopes this section to the current Wi-Fi network and its own data, so off-Wi-Fi the block is
 * absent and the section says why rather than answering with somebody else's link.
 */
data class WifiSectionUiState(
    val wifi: MonitorSection<WifiEntry>,
    val link: ActiveLink?,
    val downstreamKbps: Int?,
    val upstreamKbps: Int?,
    val signal: MonitorSection<SignalSeries>,
) {
    companion object {

        /** Shown for the frame between the screen opening and the first snapshot arriving. */
        val Empty = WifiSectionUiState(
            wifi = MonitorSection.absent(SectionAvailability.NoNetwork),
            link = null,
            downstreamKbps = null,
            upstreamKbps = null,
            signal = emptySignalWindow(),
        )
    }
}

/**
 * S1433: composes the Wi-Fi subscreen and owns its radio switch.
 *
 * The `RadioControlContract` is injected here and never into the Fragment - a screen class holding a domain
 * dependency is what the Phase 07 audit had to undo, and the `activity-logic` ratchet fails a closure over a
 * new one.
 *
 * Shared with `WhileSubscribed()` and no stop timeout, like the Summary screen: the collector is the
 * Fragment's STARTED lifecycle, so the sampler and the connectivity observers behind these flows stop the
 * moment the screen does. A grace period would leave them registered behind a screen the user has left,
 * which the tactical plan's "no background work" invariant forbids.
 */
@HiltViewModel
class WifiSectionViewModel @Inject constructor(
    repository: NetworkMonitorRepository,
    observeWifiSignal: ObserveWifiSignalUseCase,
    private val radioControl: RadioControlContract,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _radioOutcome = MutableSharedFlow<RadioToggleOutcome>(extraBufferCapacity = 1)

    /**
     * One-shot, with no replay on purpose.
     *
     * An outcome is acted on once. Replaying the last one would reopen the system Wi-Fi panel on every
     * rotation, long after the tap that produced it.
     */
    val radioOutcome: SharedFlow<RadioToggleOutcome> = _radioOutcome.asSharedFlow()

    /**
     * The observed radio state.
     *
     * `flowOn` because the reader answers from `Settings.Global` and, failing that, from `WifiManager` -
     * a content-resolver read and a binder call, neither of which belongs on the main thread.
     */
    val radio: StateFlow<RadioToggleState> = radioControl.state(RadioKind.WIFI)
        .map { isOn -> RadioToggleState(isOn = isOn, isSupported = radioControl.isToggleSupported) }
        .flowOn(ioDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), initialRadioState())

    private val chartResets = ChartWindowResetManager()

    val uiState: StateFlow<WifiSectionUiState> = combine(
        repository.observeSnapshot(),
        observeWifiSignal()
            .withChartResets(chartResets.resets)
            .scan(emptySignalWindow()) { window, event ->
                when (event) {
                    is ChartWindowEvent.Sample -> window.appendSample(event.reading)
                    is ChartWindowEvent.Reset -> collectingSignalWindow()
                }
            },
    ) { snapshot, signal -> snapshot.toUiState(signal) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), WifiSectionUiState.Empty)

    /** Starts the signal window over; the sampler keeps ticking, so the chart refills from the next reading. */
    fun onChartResetRequested() {
        chartResets.reset(ChartWindowResetManager.SINGLE_CHART)
    }

    /** Asks the platform to flip Wi-Fi and reports which of the three outcomes it was. */
    fun onRadioToggleRequested() {
        viewModelScope.launch {
            val outcome = if (radioControl.isToggleSupported) {
                attemptToggle()
            } else {
                RadioToggleOutcome.Unsupported
            }
            _radioOutcome.emit(outcome)
        }
    }

    /**
     * The contract confirms a flip by observing the state rather than by trusting the platform's return
     * value, and it reads that state on the caller's context - so the attempt is moved off the main thread
     * here rather than inside a controller this ticket does not own.
     */
    private suspend fun attemptToggle(): RadioToggleOutcome =
        if (withContext(ioDispatcher) { radioControl.toggle(RadioKind.WIFI) }) {
            RadioToggleOutcome.Switched
        } else {
            RadioToggleOutcome.NeedsSystemUi(RadioKind.WIFI)
        }

    private fun initialRadioState(): RadioToggleState =
        RadioToggleState(isOn = null, isSupported = radioControl.isToggleSupported)
}

/**
 * The bandwidth estimate is read from the active network rather than from the Wi-Fi entry: it is a property
 * of the link Android is actually using, and it is null on a device that reports none - which the section
 * states rather than showing a zero nobody measured.
 */
private fun NetworkMonitorSnapshot.toUiState(signal: MonitorSection<SignalSeries>): WifiSectionUiState {
    val activeWifi = networks.firstOrNull { it.isActive && it.transport == NetworkTransport.WIFI }
    return WifiSectionUiState(
        wifi = wifi,
        link = activeLink.takeIf { activeWifi != null },
        downstreamKbps = activeWifi?.downstreamKbps,
        upstreamKbps = activeWifi?.upstreamKbps,
        signal = signal,
    )
}
