package com.sza.fastmediasorter.ui.networkmonitor.summary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.card.MaterialCardView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentNetworkMonitorSummaryBinding
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkTransport
import com.sza.fastmediasorter.domain.model.networkmonitor.SectionAvailability
import com.sza.fastmediasorter.ui.networkmonitor.NetworkMonitorSection
import com.sza.fastmediasorter.ui.networkmonitor.helpers.NetworkMonitorSectionHost
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint

/**
 * S1433: the Monitor's first screen - the active connection above a grid of section tiles.
 *
 * Every tile states its availability in words as well as by position, because strategic criterion 2 owes the
 * user the difference between "no Bluetooth radio here" and "you declined the permission", and a colour alone
 * cannot say which (nor be read at all by someone who cannot distinguish it).
 */
@AndroidEntryPoint
class NetworkMonitorSummaryFragment : Fragment() {

    private var _binding: FragmentNetworkMonitorSummaryBinding? = null
    private val binding
        get() = requireNotNull(_binding) { "Binding is valid only between onCreateView and onDestroyView" }

    private val viewModel: NetworkMonitorSummaryViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentNetworkMonitorSummaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindTileClicks()
        collectOnLifecycle(viewModel.uiState) { render(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun bindTileClicks() {
        tileCards().forEach { (section, card) ->
            card.setOnClickListener { openSection(section) }
        }
    }

    private fun openSection(section: NetworkMonitorSection) {
        (activity as? NetworkMonitorSectionHost)?.openSection(section)
    }

    private fun render(state: NetworkMonitorSummaryUiState) {
        renderActiveCard(state)
        val statusViews = tileStatusViews()
        statusViews.forEach { (section, view) ->
            view.setText(state.sections[section].toStatusRes())
        }
        renderFacts(state)
    }

    /**
     * S1617: a tile with no fact loses the line rather than showing a placeholder - the ticket exists
     * because these tiles were bulky and said nothing, and "no data" would be more of both.
     */
    private fun renderFacts(state: NetworkMonitorSummaryUiState) {
        tileFactViews().forEach { (section, view) ->
            val text = state.facts[section]?.let { fact -> factText(fact) }
            view.text = text.orEmpty()
            view.isVisible = !text.isNullOrBlank()
        }
    }

    private fun factText(fact: SectionFact): String? = when (fact) {
        is SectionFact.Name -> fact.value
        is SectionFact.WifiLink -> wifiLinkText(fact)
        is SectionFact.BondedDevices -> getString(R.string.network_monitor_fact_bonded_devices, fact.count)
        is SectionFact.ExternalAddress -> fact.value
    }

    /** Either half may be missing, and a lone separator would read as a rendering fault. */
    private fun wifiLinkText(fact: SectionFact.WifiLink): String? {
        val speed = fact.linkSpeedMbps?.let { getString(R.string.network_monitor_fact_link_speed, it) }
        return listOfNotNull(fact.ssid?.takeIf { it.isNotBlank() }, speed)
            .joinToString(separator = " - ")
            .ifBlank { null }
    }

    private fun renderActiveCard(state: NetworkMonitorSummaryUiState) {
        val transportRes = state.transport?.toLabelRes()
        val hasLink = transportRes != null
        binding.networkMonitorActiveHeadline.text = if (hasLink) {
            headlineFor(getString(transportRes), state.networkName, getString(state.internet.toLabelRes()))
        } else {
            getString(R.string.network_monitor_active_none)
        }
        binding.networkMonitorLocalIpRow.isVisible = hasLink
        binding.networkMonitorLocalIpValue.text = listOfNotNull(state.localIpv4, state.externalIp)
            .joinToString(separator = " | ")
            .ifBlank { getString(R.string.network_monitor_value_unknown) }
    }

    /** The transport alone when the platform gives no network name - an empty separator would read as a bug. */
    private fun headlineFor(transportLabel: String, networkName: String?, internet: String): String {
        val connection = if (networkName.isNullOrBlank()) transportLabel else "$transportLabel - $networkName"
        return "$connection - $internet"
    }

    private fun tileCards(): Map<NetworkMonitorSection, MaterialCardView> = mapOf(
        NetworkMonitorSection.Wifi to binding.tileWifi,
        NetworkMonitorSection.Mobile to binding.tileMobile,
        NetworkMonitorSection.Bluetooth to binding.tileBluetooth,
        NetworkMonitorSection.Gnss to binding.tileGnss,
        NetworkMonitorSection.Internet to binding.tileInternet,
        NetworkMonitorSection.History to binding.tileHistory,
    )

    private fun tileFactViews(): Map<NetworkMonitorSection, TextView> = mapOf(
        NetworkMonitorSection.Wifi to binding.tileWifiFact,
        NetworkMonitorSection.Mobile to binding.tileMobileFact,
        NetworkMonitorSection.Bluetooth to binding.tileBluetoothFact,
        NetworkMonitorSection.Gnss to binding.tileGnssFact,
        NetworkMonitorSection.Internet to binding.tileInternetFact,
        NetworkMonitorSection.History to binding.tileHistoryFact,
    )

    private fun tileStatusViews(): Map<NetworkMonitorSection, TextView> = mapOf(
        NetworkMonitorSection.Wifi to binding.tileWifiStatus,
        NetworkMonitorSection.Mobile to binding.tileMobileStatus,
        NetworkMonitorSection.Bluetooth to binding.tileBluetoothStatus,
        NetworkMonitorSection.Gnss to binding.tileGnssStatus,
        NetworkMonitorSection.Internet to binding.tileInternetStatus,
        NetworkMonitorSection.History to binding.tileHistoryStatus,
    )
}

@StringRes
private fun SectionAvailability?.toStatusRes(): Int = when (this) {
    SectionAvailability.Available -> R.string.network_monitor_status_available
    SectionAvailability.NoHardware -> R.string.network_monitor_status_no_hardware
    is SectionAvailability.NoPermission -> R.string.network_monitor_status_no_permission
    SectionAvailability.NoNetwork -> R.string.network_monitor_status_no_network
    null -> R.string.network_monitor_status_unknown
}

@StringRes
private fun NetworkTransport.toLabelRes(): Int = when (this) {
    NetworkTransport.WIFI -> R.string.network_monitor_section_wifi
    NetworkTransport.CELLULAR -> R.string.network_monitor_section_mobile
    NetworkTransport.ETHERNET -> R.string.network_monitor_transport_ethernet
    NetworkTransport.BLUETOOTH -> R.string.network_monitor_section_bluetooth
    NetworkTransport.VPN -> R.string.network_monitor_transport_vpn
    NetworkTransport.OTHER -> R.string.network_monitor_transport_other
}

@StringRes
private fun InternetReachability.toLabelRes(): Int = when (this) {
    InternetReachability.VALIDATED -> R.string.network_monitor_internet_ok
    InternetReachability.CAPTIVE_PORTAL -> R.string.network_monitor_internet_captive
    InternetReachability.CONNECTED_NO_INTERNET -> R.string.network_monitor_internet_none
    InternetReachability.OFFLINE -> R.string.network_monitor_internet_offline
}
