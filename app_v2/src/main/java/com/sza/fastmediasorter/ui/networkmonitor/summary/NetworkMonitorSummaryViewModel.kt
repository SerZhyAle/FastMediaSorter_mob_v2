package com.sza.fastmediasorter.ui.networkmonitor.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMonitorSnapshot
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkTransport
import com.sza.fastmediasorter.domain.model.networkmonitor.SectionAvailability
import com.sza.fastmediasorter.domain.model.networkmonitor.VisibleNetwork
import com.sza.fastmediasorter.domain.repository.NetworkMonitorRepository
import com.sza.fastmediasorter.ui.networkmonitor.NetworkMonitorSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
 * S1433: everything the Summary screen renders, already decided.
 *
 * [sections] maps a tile to the reason it may be empty. A null value means the snapshot cannot answer for
 * that section - GNSS needs a registered status callback that only its own screen owns - and the tile says
 * so instead of guessing "available".
 */
data class NetworkMonitorSummaryUiState(
    val transport: NetworkTransport?,
    val networkName: String?,
    val localIpv4: String?,
    val internet: InternetReachability,
    val sections: Map<NetworkMonitorSection, SectionAvailability?>,
) {
    companion object {

        /** Shown for the frame between the screen opening and the first snapshot arriving. */
        val Empty = NetworkMonitorSummaryUiState(
            transport = null,
            networkName = null,
            localIpv4 = null,
            internet = InternetReachability.OFFLINE,
            sections = emptyMap(),
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
) : ViewModel() {

    val uiState: StateFlow<NetworkMonitorSummaryUiState> = repository.observeSnapshot()
        .map { it.toUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), NetworkMonitorSummaryUiState.Empty)
}

private fun NetworkMonitorSnapshot.toUiState(): NetworkMonitorSummaryUiState {
    val active = networks.firstOrNull { it.isActive }
    return NetworkMonitorSummaryUiState(
        transport = active?.transport,
        networkName = resolveNetworkName(active?.transport),
        localIpv4 = activeLink?.ipv4Addresses?.firstOrNull(),
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
    )
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
