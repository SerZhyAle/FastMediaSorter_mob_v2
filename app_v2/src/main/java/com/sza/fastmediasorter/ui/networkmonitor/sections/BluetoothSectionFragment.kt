package com.sza.fastmediasorter.ui.networkmonitor.sections

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentNetworkMonitorBluetoothBinding
import com.sza.fastmediasorter.domain.model.networkmonitor.BluetoothDeviceEntry
import com.sza.fastmediasorter.domain.model.networkmonitor.MonitorSection
import com.sza.fastmediasorter.ui.networkmonitor.helpers.RadioToggleBinder
import com.sza.fastmediasorter.ui.networkmonitor.helpers.SignalChartBinder
import com.sza.fastmediasorter.ui.networkmonitor.helpers.toReasonRes
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint

/**
 * S1433: the Bluetooth subscreen - adapter state, the radio switch, the paired list and one chart for a
 * device the user picked.
 *
 * There is no scan action anywhere on this screen and no discovery call behind it. Research artifact 02
 * permits RSSI only for an explicitly selected connected device, and strategic §2 lists background and
 * on-demand scanning among the non-goals, so the picker offers what is already connected and nothing else.
 */
@AndroidEntryPoint
class BluetoothSectionFragment : Fragment() {

    private var _binding: FragmentNetworkMonitorBluetoothBinding? = null
    private val binding
        get() = requireNotNull(_binding) { "Binding is valid only between onCreateView and onDestroyView" }

    private val viewModel: BluetoothSectionViewModel by viewModels()

    private var radioBinder: RadioToggleBinder? = null
    private var chartBinder: SignalChartBinder? = null

    /** The order the picker last offered, so a tap resolves to the right address rather than to a name. */
    private var pickerDevices: List<BluetoothDeviceEntry> = emptyList()

    /** The labels currently in the dropdown, kept so an unchanged set does not swap the adapter. */
    private var pickerLabels: List<String> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentNetworkMonitorBluetoothBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        radioBinder = RadioToggleBinder(
            switch = binding.bluetoothRadioSwitch,
            reasonView = binding.bluetoothRadioReason,
            onToggleRequested = viewModel::onRadioToggleRequested,
        )
        chartBinder = SignalChartBinder(
            chart = binding.bluetoothChart.chartSeries,
            summaryView = binding.bluetoothChart.chartSummary,
            emptyView = binding.bluetoothChart.chartEmpty,
        )
        binding.bluetoothChart.chartHeading.setText(R.string.network_monitor_bluetooth_signal_heading)
        binding.bluetoothDevicePicker.setOnItemClickListener { _, _, position, _ ->
            viewModel.onDeviceSelected(pickerDevices.getOrNull(position)?.address)
        }
        collectOnLifecycle(viewModel.uiState) { render(it) }
        collectOnLifecycle(viewModel.radio) { radioBinder?.render(it) }
        collectOnLifecycle(viewModel.radioOutcome) { radioBinder?.apply(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        radioBinder = null
        chartBinder = null
        pickerDevices = emptyList()
        pickerLabels = emptyList()
        _binding = null
    }

    private fun render(state: BluetoothSectionUiState) {
        renderAdapter(state)
        renderPairedList(state.devices)
        renderPicker(state)
        renderChart(state)
    }

    private fun renderAdapter(state: BluetoothSectionUiState) {
        val entry = state.adapter.data
        binding.bluetoothAdapterValue.setText(
            when {
                entry == null -> R.string.network_monitor_bluetooth_adapter_absent
                entry.isEnabled -> R.string.network_monitor_bluetooth_state_on
                else -> R.string.network_monitor_bluetooth_state_off
            }
        )
        binding.bluetoothAdapterAvailability.isVisible = entry == null
        binding.bluetoothAdapterAvailability.setText(state.adapter.availability.toReasonRes())
    }

    /**
     * One text block rather than a list of views: each line is a whole localized phrase, so joining them
     * costs no grammar, and a handful of paired devices does not need a recycler or an item layout.
     */
    private fun renderPairedList(devices: MonitorSection<List<BluetoothDeviceEntry>>) {
        val entries = devices.data.orEmpty()
        binding.bluetoothPairedList.isVisible = entries.isNotEmpty()
        binding.bluetoothPairedEmpty.isVisible = entries.isEmpty()
        binding.bluetoothPairedEmpty.setText(
            if (devices.data == null) {
                devices.availability.toReasonRes()
            } else {
                R.string.network_monitor_bluetooth_paired_empty
            }
        )
        binding.bluetoothPairedList.text = entries.joinToString(separator = "\n") { device -> describe(device) }
    }

    private fun describe(device: BluetoothDeviceEntry): String = getString(
        if (device.isConnected) {
            R.string.network_monitor_bluetooth_device_connected
        } else {
            R.string.network_monitor_bluetooth_device_idle
        },
        device.name ?: getString(R.string.network_monitor_value_unknown),
    )

    /**
     * The adapter is rebuilt only when the offered set actually changes.
     *
     * The state re-emits on the Monitor's one-per-second tick, and swapping an `ArrayAdapter` under an open
     * dropdown every second would make the list impossible to tap; the same goes for rewriting the field the
     * selection is displayed in.
     */
    private fun renderPicker(state: BluetoothSectionUiState) {
        pickerDevices = state.chartableDevices
        val hasChoices = pickerDevices.isNotEmpty()
        binding.bluetoothDevicePickerLayout.isVisible = hasChoices
        binding.bluetoothPickerEmpty.isVisible = !hasChoices
        val labels = pickerDevices.map { it.name ?: getString(R.string.network_monitor_value_unknown) }
        if (labels != pickerLabels) {
            pickerLabels = labels
            binding.bluetoothDevicePicker.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels)
            )
        }
        val selected = pickerDevices.indexOfFirst { it.address == state.selectedAddress }
        val shown = labels.getOrNull(selected) ?: getString(R.string.network_monitor_bluetooth_picker_none)
        if (binding.bluetoothDevicePicker.text.toString() != shown) {
            // setText(.., false) rather than the filtering overload: the dropdown lists connected devices,
            // and filtering it by the text already in the field would leave one entry to choose from.
            binding.bluetoothDevicePicker.setText(shown, false)
        }
    }

    /**
     * The chart is bound only inside the selected-device branch.
     *
     * With nothing selected the section renders no chart at all rather than an empty one: no device chosen
     * means no GATT connection was opened, so there is nothing measured to draw and an empty frame would
     * suggest otherwise.
     */
    private fun renderChart(state: BluetoothSectionUiState) {
        val hasSelection = state.selectedAddress != null
        binding.bluetoothChart.root.isVisible = hasSelection
        if (hasSelection) {
            chartBinder?.render(state.signal)
        }
    }
}
