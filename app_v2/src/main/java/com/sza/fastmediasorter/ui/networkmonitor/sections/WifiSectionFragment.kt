package com.sza.fastmediasorter.ui.networkmonitor.sections

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentNetworkMonitorWifiBinding
import com.sza.fastmediasorter.databinding.ViewNetworkMonitorLinkDetailsBinding
import com.sza.fastmediasorter.domain.model.networkmonitor.ActiveLink
import com.sza.fastmediasorter.ui.networkmonitor.helpers.RadioToggleBinder
import com.sza.fastmediasorter.ui.networkmonitor.helpers.SignalChartBinder
import com.sza.fastmediasorter.ui.networkmonitor.helpers.toReasonRes
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint

/**
 * S1433: the Wi-Fi subscreen - the radio switch, the connected network, the active link and the RSSI chart.
 *
 * Holds no repository and no radio contract of its own: everything arrives through [WifiSectionViewModel],
 * which is what keeps a domain dependency out of a screen class (CLAUDE.md Rule 3).
 */
@AndroidEntryPoint
class WifiSectionFragment : Fragment() {

    private var _binding: FragmentNetworkMonitorWifiBinding? = null
    private val binding
        get() = requireNotNull(_binding) { "Binding is valid only between onCreateView and onDestroyView" }

    private val viewModel: WifiSectionViewModel by viewModels()

    private var radioBinder: RadioToggleBinder? = null
    private var chartBinder: SignalChartBinder? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentNetworkMonitorWifiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        radioBinder = RadioToggleBinder(
            switch = binding.wifiRadioSwitch,
            reasonView = binding.wifiRadioReason,
            onToggleRequested = viewModel::onRadioToggleRequested,
        )
        chartBinder = SignalChartBinder(
            chart = binding.wifiChart.chartSeries,
            summaryView = binding.wifiChart.chartSummary,
            emptyView = binding.wifiChart.chartEmpty,
        )
        collectOnLifecycle(viewModel.uiState) { render(it) }
        collectOnLifecycle(viewModel.radio) { radioBinder?.render(it) }
        collectOnLifecycle(viewModel.radioOutcome) { radioBinder?.apply(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        radioBinder = null
        chartBinder = null
        _binding = null
    }

    private fun render(state: WifiSectionUiState) {
        renderConnectedNetwork(state)
        renderLink(binding.wifiLinkDetails, state)
        chartBinder?.render(state.signal)
    }

    /**
     * The section states its reason in words instead of leaving the fields blank - strategic criterion 2
     * owes the user the difference between "nothing is connected" and "you declined the location grant",
     * which a row of dashes cannot express.
     */
    private fun renderConnectedNetwork(state: WifiSectionUiState) {
        val entry = state.wifi.data
        val available = entry != null
        binding.wifiDetailsGroup.isVisible = available
        binding.wifiAvailability.isVisible = !available
        binding.wifiAvailability.setText(state.wifi.availability.toReasonRes())
        binding.wifiNetworkValue.text = entry?.ssid ?: unknown()
        binding.wifiFrequencyValue.text = entry?.frequencyMhz?.let {
            getString(R.string.network_monitor_value_mhz, it)
        } ?: unknown()
        binding.wifiLinkSpeedValue.text = entry?.linkSpeedMbps?.let {
            getString(R.string.network_monitor_value_mbps, it)
        } ?: unknown()
        binding.wifiStandardValue.text = entry?.standard ?: unknown()
    }

    private fun renderLink(link: ViewNetworkMonitorLinkDetailsBinding, state: WifiSectionUiState) {
        val active: ActiveLink? = state.link
        link.linkInterfaceValue.text = active?.interfaceName ?: unknown()
        link.linkIpv4Value.text = active?.ipv4Addresses?.joinToString().orEmpty().ifBlank { unknown() }
        link.linkIpv6Value.text = active?.ipv6Addresses?.joinToString().orEmpty().ifBlank { unknown() }
        link.linkDnsValue.text = active?.dnsServers?.joinToString().orEmpty().ifBlank { unknown() }
        link.linkGatewayValue.text = active?.gateway ?: unknown()
        link.linkRouteValue.setText(
            if (active?.hasDefaultRoute == true) {
                R.string.network_monitor_route_present
            } else {
                R.string.network_monitor_route_absent
            }
        )
        link.linkProxyValue.text = active?.proxy ?: unknown()
        link.linkBandwidthValue.text = bandwidthText(state)
    }

    /**
     * Both directions or neither: Android reports the pair, and showing one of them alone would read as a
     * measured asymmetry rather than as a device that answers with nothing.
     */
    private fun bandwidthText(state: WifiSectionUiState): String {
        val down = state.downstreamKbps
        val up = state.upstreamKbps
        if (down == null || up == null) {
            return unknown()
        }
        return getString(
            R.string.network_monitor_bandwidth_pair,
            getString(R.string.network_monitor_value_kbps, down),
            getString(R.string.network_monitor_value_kbps, up),
        )
    }

    private fun unknown(): String = getString(R.string.network_monitor_value_unknown)
}
