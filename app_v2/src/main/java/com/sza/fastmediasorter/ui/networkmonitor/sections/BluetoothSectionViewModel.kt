package com.sza.fastmediasorter.ui.networkmonitor.sections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.domain.model.networkmonitor.BluetoothDeviceEntry
import com.sza.fastmediasorter.domain.model.networkmonitor.BluetoothEntry
import com.sza.fastmediasorter.domain.model.networkmonitor.MonitorSection
import com.sza.fastmediasorter.domain.model.networkmonitor.SectionAvailability
import com.sza.fastmediasorter.domain.model.networkmonitor.SignalSeries
import com.sza.fastmediasorter.domain.radio.RadioControlContract
import com.sza.fastmediasorter.domain.radio.RadioKind
import com.sza.fastmediasorter.domain.repository.NetworkMonitorRepository
import com.sza.fastmediasorter.domain.usecase.networkmonitor.ObserveBluetoothDevicesUseCase
import com.sza.fastmediasorter.domain.usecase.networkmonitor.ObserveBluetoothRssiUseCase
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * S1433: everything the Bluetooth subscreen renders.
 *
 * [selectedAddress] is part of the state rather than a Fragment field because it decides whether anything is
 * sampled at all: with none chosen no GATT connection is opened, which is how research artifact 02's "only
 * an explicitly selected connected device" rule is enforced structurally instead of by a filter.
 */
data class BluetoothSectionUiState(
    val adapter: MonitorSection<BluetoothEntry>,
    val devices: MonitorSection<List<BluetoothDeviceEntry>>,
    val selectedAddress: String?,
    val signal: MonitorSection<SignalSeries>,
) {

    /**
     * Only a GATT link can be charted, which is narrower than being connected.
     *
     * A bonded but absent device has no connection to read at all, and a headset connected over a classic
     * profile has one that carries no RSSI - offering either would draw an empty chart (S1853).
     */
    val chartableDevices: List<BluetoothDeviceEntry>
        get() = devices.data.orEmpty().filter { it.isChartable }

    companion object {

        /** Shown for the frame between the screen opening and the first snapshot arriving. */
        val Empty = BluetoothSectionUiState(
            adapter = MonitorSection.absent(SectionAvailability.NoHardware),
            devices = MonitorSection.absent(SectionAvailability.NoHardware),
            selectedAddress = null,
            signal = emptySignalWindow(),
        )
    }
}

/**
 * S1433: composes the Bluetooth subscreen and owns its radio switch.
 *
 * The device selection lives here and drives a `flatMapLatest`, so changing it tears the previous GATT
 * connection down instead of leaving two open - and clearing it stops sampling altogether rather than
 * leaving a connection alive behind a hidden chart.
 */
@HiltViewModel
class BluetoothSectionViewModel @Inject constructor(
    repository: NetworkMonitorRepository,
    observeDevices: ObserveBluetoothDevicesUseCase,
    observeRssi: ObserveBluetoothRssiUseCase,
    private val radioControl: RadioControlContract,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val selectedAddress = MutableStateFlow<String?>(null)

    private val _radioOutcome = MutableSharedFlow<RadioToggleOutcome>(extraBufferCapacity = 1)

    /** One-shot: replaying it would reopen the system Bluetooth screen on every rotation. */
    val radioOutcome: SharedFlow<RadioToggleOutcome> = _radioOutcome.asSharedFlow()

    /** `flowOn` because the reader answers from `Settings.Global` and the adapter, neither on the main thread. */
    val radio: StateFlow<RadioToggleState> = radioControl.state(RadioKind.BLUETOOTH)
        .map { isOn -> RadioToggleState(isOn = isOn, isSupported = radioControl.isToggleSupported) }
        .flowOn(ioDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), initialRadioState())

    private val chartResets = ChartWindowResetManager()

    // The reset is folded inside the per-device branch, not around it: the sampler holds the GATT connection
    // for as long as its flow is collected, so resetting the selection instead would drop the radio link.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val signal: Flow<MonitorSection<SignalSeries>> = selectedAddress
        .flatMapLatest { address ->
            observeRssi(address)
                .withChartResets(chartResets.resets)
                .scan(emptySignalWindow()) { window, event ->
                    when (event) {
                        is ChartWindowEvent.Sample -> window.appendSample(event.reading)
                        is ChartWindowEvent.Reset -> collectingSignalWindow()
                    }
                }
        }

    val uiState: StateFlow<BluetoothSectionUiState> = combine(
        repository.observeSnapshot(),
        observeDevices(),
        selectedAddress,
        signal,
    ) { snapshot, devices, address, series ->
        BluetoothSectionUiState(
            adapter = snapshot.bluetooth,
            devices = devices,
            selectedAddress = address,
            signal = series,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), BluetoothSectionUiState.Empty)

    /** Starts the selected device's window over without touching its GATT connection. */
    fun onChartResetRequested() {
        chartResets.reset(ChartWindowResetManager.SINGLE_CHART)
    }

    /** Charts [address], or stops charting when it is null. */
    fun onDeviceSelected(address: String?) {
        selectedAddress.value = address
    }

    /** Asks the platform to flip Bluetooth and reports which of the three outcomes it was. */
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
     * The contract confirms a flip by observing the state and reads that state on the caller's context, so
     * the attempt is moved off the main thread here rather than inside a controller this ticket does not own.
     */
    private suspend fun attemptToggle(): RadioToggleOutcome =
        if (withContext(ioDispatcher) { radioControl.toggle(RadioKind.BLUETOOTH) }) {
            RadioToggleOutcome.Switched
        } else {
            RadioToggleOutcome.NeedsSystemUi(RadioKind.BLUETOOTH)
        }

    private fun initialRadioState(): RadioToggleState =
        RadioToggleState(isOn = null, isSupported = radioControl.isToggleSupported)
}
