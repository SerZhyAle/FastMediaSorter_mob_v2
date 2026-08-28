package com.sza.fastmediasorter.ui.networkmonitor.sections

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.panel.OsShortcutCatalog
import com.sza.fastmediasorter.databinding.FragmentNetworkMonitorWifiBinding
import com.sza.fastmediasorter.databinding.ViewNetworkMonitorLinkDetailsBinding
import com.sza.fastmediasorter.domain.model.networkmonitor.ActiveLink
import com.sza.fastmediasorter.domain.model.networkmonitor.SectionAvailability
import com.sza.fastmediasorter.domain.model.networkmonitor.WifiEntry
import com.sza.fastmediasorter.ui.networkmonitor.helpers.NetworkMonitorPermissionManager
import com.sza.fastmediasorter.ui.networkmonitor.helpers.RadioToggleBinder
import com.sza.fastmediasorter.ui.networkmonitor.helpers.SignalChartBinder
import com.sza.fastmediasorter.ui.networkmonitor.helpers.startSystemSurfaceFor
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

    private val permissionManager = NetworkMonitorPermissionManager(this)

    private var radioBinder: RadioToggleBinder? = null
    private var chartBinder: SignalChartBinder? = null

    /**
     * The reason last bound to the name row, so the snapshot's one-per-second tick does not rebind it.
     *
     * Binding costs a permission-registry lookup, a stored-marker read and two binder calls, all on the main
     * thread - the same argument that keeps the Bluetooth picker from swapping its adapter every second.
     */
    private var boundNameReason: SectionAvailability? = null

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
            resetTarget = binding.wifiChart.root,
            onResetRequested = viewModel::onChartResetRequested,
        )
        binding.wifiSectionHeading.setOnClickListener {
            requireContext().startSystemSurfaceFor(OsShortcutCatalog.KEY_WIFI)
        }
        collectOnLifecycle(viewModel.uiState) { render(it) }
        collectOnLifecycle(viewModel.radio) { radioBinder?.render(it) }
        collectOnLifecycle(viewModel.radioOutcome) { radioBinder?.apply(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        radioBinder = null
        chartBinder = null
        boundNameReason = null
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
        if (!permissionManager.bind(binding.wifiAvailability, state.wifi.availability)) {
            binding.wifiAvailability.setText(state.wifi.availability.toReasonRes())
        }
        renderNetworkName(entry)
        binding.wifiFrequencyValue.text = entry?.frequencyMhz?.let {
            getString(R.string.network_monitor_value_mhz, it)
        } ?: unknown()
        binding.wifiLinkSpeedValue.text = entry?.linkSpeedMbps?.let {
            getString(R.string.network_monitor_value_mbps, it)
        } ?: unknown()
        binding.wifiStandardValue.text = entry?.standard ?: unknown()
    }

    /**
     * The name row doubles as the recovery action while the platform withholds the name.
     *
     * Only this one field is redacted - the rows beside it keep their values - so the reason and the grant
     * belong on the row itself rather than on a banner that would hide the whole section (S1853).
     */
    private fun renderNetworkName(entry: WifiEntry?) {
        val name = entry?.ssid
        if (name != null) {
            boundNameReason = null
            clearNameRowAction()
            binding.wifiNetworkValue.contentDescription = null
            binding.wifiNetworkValue.text = name
            return
        }
        val reason = entry?.ssidAvailability ?: SectionAvailability.NoNetwork
        if (reason == boundNameReason) {
            return
        }
        boundNameReason = reason
        if (!permissionManager.bind(binding.wifiNetworkValue, reason)) {
            clearNameRowAction()
            binding.wifiNetworkValue.setText(reason.toReasonRes())
        }
        binding.wifiNetworkValue.contentDescription = getString(
            R.string.network_monitor_wifi_network_reason_description,
            getString(R.string.network_monitor_wifi_network_label),
            binding.wifiNetworkValue.text,
        )
    }

    /** A row that stopped being an action stops behaving like one - the layout marks it clickable up front. */
    private fun clearNameRowAction() {
        binding.wifiNetworkValue.setOnClickListener(null)
        binding.wifiNetworkValue.isClickable = false
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
        link.linkBandwidthDownValue.text = bandwidthText(state.downstreamKbps, state.upstreamKbps)
        link.linkBandwidthUpValue.text = bandwidthText(state.upstreamKbps, state.downstreamKbps)
    }

    /**
     * S1617: one direction per row, but still both or neither.
     *
     * Android reports the pair, and a row showing one figure while its twin says "unknown" would read as
     * a measured asymmetry rather than as a device that answered with nothing - which is why [other] is
     * consulted before [value] is rendered at all.
     */
    private fun bandwidthText(value: Int?, other: Int?): String {
        if (value == null || other == null) {
            return unknown()
        }
        return getString(R.string.network_monitor_value_kbps, value)
    }

    private fun unknown(): String = getString(R.string.network_monitor_value_unknown)
}
