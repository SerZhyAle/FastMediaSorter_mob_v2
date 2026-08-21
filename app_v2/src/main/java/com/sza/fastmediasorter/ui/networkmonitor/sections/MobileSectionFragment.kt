package com.sza.fastmediasorter.ui.networkmonitor.sections

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentNetworkMonitorMobileBinding
import com.sza.fastmediasorter.databinding.ViewNetworkMonitorSignalChartBinding
import com.sza.fastmediasorter.domain.model.networkmonitor.MonitorSection
import com.sza.fastmediasorter.domain.model.networkmonitor.SimEntry
import com.sza.fastmediasorter.ui.networkmonitor.helpers.NetworkMonitorPermissionManager
import com.sza.fastmediasorter.ui.networkmonitor.helpers.SignalChartBinder
import com.sza.fastmediasorter.ui.networkmonitor.helpers.startFirstAvailableSystemSurface
import com.sza.fastmediasorter.ui.networkmonitor.helpers.toReasonRes
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint

/**
 * S1433: the Mobile subscreen - the modem count, the visible SIMs and one signal chart per SIM.
 *
 * It offers no toggle at all, which is the honest shape rather than a missing feature: strategic §3.2
 * records that mobile data needs `MODIFY_PHONE_STATE` and that `SubscriptionManager` exposes no public
 * enable path, so a switch here would be a control that can never work on any Android version.
 */
@AndroidEntryPoint
class MobileSectionFragment : Fragment() {

    private var _binding: FragmentNetworkMonitorMobileBinding? = null
    private val binding
        get() = requireNotNull(_binding) { "Binding is valid only between onCreateView and onDestroyView" }

    private val viewModel: MobileSectionViewModel by viewModels()

    private val permissionManager = NetworkMonitorPermissionManager(this)

    private var chartBinders: List<SignalChartBinder> = emptyList()

    /**
     * Which SIM slot each chart currently draws, so a reset names the slot the user is looking at.
     *
     * The sampler omits a SIM that reports nothing, so the second chart does not always belong to slot 1.
     */
    private var chartSlots: List<Int> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentNetworkMonitorMobileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chartBinders = chartBindings().mapIndexed { index, chart ->
            SignalChartBinder(
                chart = chart.chartSeries,
                summaryView = chart.chartSummary,
                emptyView = chart.chartEmpty,
                resetTarget = chart.root,
                onResetRequested = { viewModel.onChartResetRequested(chartSlots.getOrNull(index) ?: index) },
            )
        }
        binding.btnMobileOpenSystem.setOnClickListener { openSystemNetworkSurface() }
        collectOnLifecycle(viewModel.uiState) { render(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        chartBinders = emptyList()
        chartSlots = emptyList()
        _binding = null
    }

    /**
     * The connectivity panel first, the full network-operator screen behind it.
     *
     * `ACTION_INTERNET_CONNECTIVITY` drops the system sheet over this screen from API 29 so the user stays
     * inside the Monitor; below that level it does not exist and the settings screen is the whole answer.
     */
    private fun openSystemNetworkSurface() {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            }
            add(Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS))
            add(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }
        requireContext().startFirstAvailableSystemSurface(candidates)
    }

    private fun render(state: MobileSectionUiState) {
        // Shown whatever the permission answer is: the count needs none, and hiding it behind a grant the
        // user declined would withhold a fact they are entitled to (see the snapshot's own note).
        binding.mobileModemCountValue.text =
            state.activeModemCount?.toString() ?: getString(R.string.network_monitor_value_unknown)
        renderSims(state.sims)
        renderCharts(state)
    }

    private fun renderSims(sims: MonitorSection<List<SimEntry>>) {
        val entries = sims.data.orEmpty()
        binding.mobileSimsAvailability.isVisible = entries.isEmpty()
        if (!permissionManager.bind(binding.mobileSimsAvailability, sims.availability)) {
            binding.mobileSimsAvailability.setText(sims.availability.toReasonRes())
        }
        renderSim(binding.mobileSim1Group, binding.mobileSim1Label, binding.mobileSim1Value, entries.getOrNull(0))
        renderSim(binding.mobileSim2Group, binding.mobileSim2Label, binding.mobileSim2Value, entries.getOrNull(1))
    }

    private fun renderSim(group: View, label: TextView, value: TextView, entry: SimEntry?) {
        group.isVisible = entry != null
        if (entry == null) {
            return
        }
        label.text = getString(R.string.network_monitor_mobile_sim_label, entry.slotIndex + SLOT_DISPLAY_OFFSET)
        val operator = entry.operatorName ?: getString(R.string.network_monitor_value_unknown)
        value.text = if (entry.isDataPreferred) {
            getString(R.string.network_monitor_mobile_sim_data_preferred, operator)
        } else {
            operator
        }
    }

    /**
     * One chart per SIM, bound in slot order.
     *
     * A slot with no series hides its whole block rather than drawing an empty chart: the sampler omits a
     * series for a SIM that reports nothing, and an empty frame would suggest a measurement was taken.
     */
    private fun renderCharts(state: MobileSectionUiState) {
        val windows = state.signals.data.orEmpty()
        chartSlots = chartBindings().indices.map { index -> windows.getOrNull(index)?.slotIndex ?: index }
        chartBindings().forEachIndexed { index, chart ->
            val window = windows.getOrNull(index)
            chart.root.isVisible = window != null || index == 0
            chart.chartHeading.text = getString(
                R.string.network_monitor_mobile_sim_signal_heading,
                (window?.slotIndex ?: index) + SLOT_DISPLAY_OFFSET,
            )
            val series = window?.series
            chartBinders.getOrNull(index)?.render(
                if (series == null) {
                    MonitorSection.absent(state.signals.availability)
                } else {
                    MonitorSection.available(series)
                }
            )
        }
    }

    private fun chartBindings(): List<ViewNetworkMonitorSignalChartBinding> =
        listOf(binding.mobileSim1Chart, binding.mobileSim2Chart)

    private companion object {

        /** Slot indices are zero-based in the platform and one-based on every SIM tray ever printed. */
        const val SLOT_DISPLAY_OFFSET = 1
    }
}
