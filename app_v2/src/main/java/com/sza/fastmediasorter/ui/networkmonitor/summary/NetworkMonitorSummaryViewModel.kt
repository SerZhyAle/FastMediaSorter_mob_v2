package com.sza.fastmediasorter.ui.networkmonitor.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMonitorSnapshot
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkTransport
import com.sza.fastmediasorter.domain.model.networkmonitor.SectionAvailability
import com.sza.fastmediasorter.domain.model.networkmonitor.VisibleNetwork
import com.sza.fastmediasorter.domain.repository.NetworkMonitorRepository
import com.sza.fastmediasorter.domain.usecase.networkmonitor.ExternalIpState
import com.sza.fastmediasorter.domain.usecase.networkmonitor.ResolveExternalIpUseCase
import com.sza.fastmediasorter.ui.networkmonitor.NetworkMonitorSection
import com.sza.fastmediasorter.ui.networkmonitor.helpers.ExternalIpSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** S1433: how the active network is doing, as far as the platform will commit to an answer. */
enum class InternetReachability {
    /** Android itself validated the link, so this is a verdict rather than our guess. */
    VALIDATED,

    /** Connected to a portal that wants a sign-in before it passes traffic. */
    CAPTIVE_PORTAL,

    /** A link is up and carries no internet. */
    CONNECTED_NO_INTERNET,

    /** No active network at all. */
    OFFLINE,
}

/**
 * S1617: the one fact a tile carries beside its availability - the thing worth knowing without opening
 * the section.
 *
 * A section with nothing to say has no entry at all rather than a placeholder: this whole ticket exists
 * because six tiles that only said "on" or "off" filled half the screen, and a line reading "no data"
 * would add bulk while adding nothing.
 */
sealed interface SectionFact {

    /** A name the platform gave us, rendered as it came - a mobile operator. */
    data class Name(val value: String) : SectionFact

    /** The connected network and how fast the link to it is; either half may be missing. */
    data class WifiLink(val ssid: String?, val linkSpeedMbps: Int?) : SectionFact

    /** A count and never a list: naming bonded devices needs BLUETOOTH_CONNECT and says who the user is. */
    data class BondedDevices(val count: Int) : SectionFact

    /** The address the outside world sees, once the user has asked for it. */
    data class ExternalAddress(val value: String) : SectionFact
}

/**
 * S1433: everything the Summary screen renders, already decided.
 *
 * [sections] maps a tile to the reason it may be empty. A null value means the snapshot cannot answer for
 * that section - GNSS needs a registered status callback that only its own screen owns - and the tile says
 * so instead of guessing "available".
 *
 * [facts] carries at most one fact per tile (S1617). A missing key means this section has nothing to say
 * right now, which is a different thing from having a fact whose value is unknown.
 */
data class NetworkMonitorSummaryUiState(
    val transport: NetworkTransport?,
    val networkName: String?,
    val localIpv4: String?,
    val externalIp: String?,
    val isResolvingExternalIp: Boolean,
    val internet: InternetReachability,
    val sections: Map<NetworkMonitorSection, SectionAvailability?>,
    val facts: Map<NetworkMonitorSection, SectionFact>,
) {
    companion object {

        /** Shown for the frame between the screen opening and the first snapshot arriving. */
        val Empty = NetworkMonitorSummaryUiState(
            transport = null,
            networkName = null,
            localIpv4 = null,
            externalIp = null,
            isResolvingExternalIp = false,
            internet = InternetReachability.OFFLINE,
            sections = emptyMap(),
            facts = emptyMap(),
        )
    }
}

/**
 * S1433: composes the Summary screen state from the Monitor's single read path.
 *
 * Shared with `WhileSubscribed()` and no stop timeout on purpose: the collector is the Fragment's STARTED
 * lifecycle, so the upstream device observers unregister the moment the screen stops. A grace period would
 * keep the connectivity and telephony callbacks live behind a screen the user has already left, which the
 * tactical plan's "no background work" invariant forbids.
 */
@HiltViewModel
class NetworkMonitorSummaryViewModel @Inject constructor(
    repository: NetworkMonitorRepository,
    private val externalIpSessionStore: ExternalIpSessionStore,
    private val resolveExternalIp: ResolveExternalIpUseCase,
) : ViewModel() {

    private val resolving = MutableStateFlow(false)

    val uiState: StateFlow<NetworkMonitorSummaryUiState> = combine(
        repository.observeSnapshot(),
        externalIpSessionStore.address,
        resolving,
    ) { snapshot, externalIp, isResolving -> snapshot.toUiState(externalIp, isResolving) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), NetworkMonitorSummaryUiState.Empty)

    /**
     * S2025: asks the echo services for the external address, on the user's explicit tap and never otherwise.
     *
     * The same use case and the same session store as the Internet section, so the S1433 privacy contract -
     * returned to the caller, never cached, never logged - cannot drift between the two entry points.
     */
    fun onExternalIpRequested() {
        if (resolving.value) return
        resolving.value = true
        viewModelScope.launch {
            try {
                resolveExternalIp(networkLabel()).collect { state -> apply(state) }
            } finally {
                resolving.value = false
            }
        }
    }

    private fun apply(state: ExternalIpState) {
        when (state) {
            ExternalIpState.Resolving -> Unit
            is ExternalIpState.Resolved -> externalIpSessionStore.update(state.address)
            ExternalIpState.Unavailable -> externalIpSessionStore.clear()
        }
    }

    /** The use case records a history row and takes the label from the caller, which already holds it. */
    private fun networkLabel(): String {
        val state = uiState.value
        return state.networkName?.takeIf { it.isNotBlank() }
            ?: state.transport?.name
            ?: UNKNOWN_NETWORK
    }

    private companion object {

        /** What the history row records when no network can name itself. */
        const val UNKNOWN_NETWORK = "unknown"
    }
}

private fun NetworkMonitorSnapshot.toUiState(
    externalIp: String?,
    isResolvingExternalIp: Boolean,
): NetworkMonitorSummaryUiState {
    val active = networks.firstOrNull { it.isActive }
    return NetworkMonitorSummaryUiState(
        transport = active?.transport,
        networkName = resolveNetworkName(active?.transport),
        localIpv4 = activeLink?.ipv4Addresses?.firstOrNull(),
        externalIp = externalIp,
        isResolvingExternalIp = isResolvingExternalIp,
        internet = active.toReachability(),
        sections = mapOf(
            NetworkMonitorSection.Wifi to wifi.availability,
            NetworkMonitorSection.Mobile to sims.availability,
            NetworkMonitorSection.Bluetooth to bluetooth.availability,
            // GNSS availability is only knowable while its own status callback is registered, and History is
            // a local table that is always reachable - neither is a field of this snapshot.
            NetworkMonitorSection.Gnss to null,
            NetworkMonitorSection.Internet to active.toSectionAvailability(),
            NetworkMonitorSection.History to SectionAvailability.Available,
        ),
        facts = collectFacts(externalIp),
    )
}

/**
 * S1617: one fact per tile, and only where the snapshot actually holds one.
 *
 * Satellites and History are absent by construction rather than by omission: neither is a field of this
 * snapshot - GNSS state belongs to the screen that registers its callback, and the history count lives in
 * a table this read path never touches.
 */
private fun NetworkMonitorSnapshot.collectFacts(externalIp: String?): Map<NetworkMonitorSection, SectionFact> =
    buildMap {
        val wifiEntry = wifi.data
        if (wifiEntry?.ssid != null || wifiEntry?.linkSpeedMbps != null) {
            put(NetworkMonitorSection.Wifi, SectionFact.WifiLink(wifiEntry.ssid, wifiEntry.linkSpeedMbps))
        }
        val operator = sims.data?.firstOrNull { it.isDataPreferred }?.operatorName
        if (!operator.isNullOrBlank()) {
            put(NetworkMonitorSection.Mobile, SectionFact.Name(operator))
        }
        bluetooth.data?.bondedDeviceCount?.let { count ->
            put(NetworkMonitorSection.Bluetooth, SectionFact.BondedDevices(count))
        }
        if (!externalIp.isNullOrBlank()) {
            put(NetworkMonitorSection.Internet, SectionFact.ExternalAddress(externalIp))
        }
    }

/** The human name of the active network, which lives in a different section per transport. */
private fun NetworkMonitorSnapshot.resolveNetworkName(transport: NetworkTransport?): String? =
    when (transport) {
        NetworkTransport.WIFI -> wifi.data?.ssid
        NetworkTransport.CELLULAR -> sims.data?.firstOrNull { it.isDataPreferred }?.operatorName
        else -> null
    }

private fun VisibleNetwork?.toReachability(): InternetReachability = when {
    this == null -> InternetReachability.OFFLINE
    isCaptivePortal -> InternetReachability.CAPTIVE_PORTAL
    hasValidatedInternet -> InternetReachability.VALIDATED
    else -> InternetReachability.CONNECTED_NO_INTERNET
}

/**
 * Not one of the Internet section's checks can run without a link, so an offline device is
 * [SectionAvailability.NoNetwork] rather than an available section that happens to fail everything.
 */
private fun VisibleNetwork?.toSectionAvailability(): SectionAvailability =
    if (this == null) SectionAvailability.NoNetwork else SectionAvailability.Available
