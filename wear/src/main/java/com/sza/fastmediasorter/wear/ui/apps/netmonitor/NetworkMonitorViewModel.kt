package com.sza.fastmediasorter.wear.ui.apps.netmonitor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkSection
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkSnapshot
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkTransport
import com.sza.fastmediasorter.wear.domain.netmonitor.sectionsFor
import com.sza.fastmediasorter.wear.domain.repository.WearNetworkMonitorRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Everything the screen shows that is not a reading off a radio.
 *
 * Held as one value rather than as five flows so that an action which changes two of them at once -
 * restarting the signal window, resetting the counters - reaches the screen as a single emission.
 *
 * @param trafficOrigin counters read from `TrafficStats` are cumulative since boot, so "reset" cannot
 *   zero them; it moves the origin the section counts from. Null means the raw totals are shown.
 * @param signalEpoch bumped when the user restarts the signal window. The samples themselves are not
 *   held here: appending one per reading from inside the combine would re-enter it on every tick.
 */
private data class MonitorLocalState(
    val isProbing: Boolean = false,
    val probeResult: Boolean? = null,
    val clipboardNotice: String? = null,
    val externalIp: String? = null,
    val trafficOrigin: Pair<Long, Long>? = null,
    val signalEpoch: Int = 0,
)

/**
 * Publishes the watch's readings for as long as the screen observes them and no longer.
 *
 * `WhileSubscribed` with no grace period is the whole cost policy of this program: the flow's
 * `awaitClose` tears the platform callbacks down the moment the last collector leaves, so a program
 * the user walked away from measures nothing.
 */
@HiltViewModel
class NetworkMonitorViewModel @Inject constructor(
    private val repository: WearNetworkMonitorRepository,
    private val preferencesRepository: WearPreferencesRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val capabilities = repository.capabilities()
    private val sections = sectionsFor(capabilities)

    /** Session-only, newest last. Written by the collector below. */
    private val history = mutableListOf<WearNetworkSnapshot>()

    /** The Wi-Fi signal window the section draws, oldest first; the user restarts it from there. */
    private val signalWindow = mutableListOf<Int>()

    private val localState = MutableStateFlow(MonitorLocalState())

    /** The link the last lookup answered for; a new link invalidates the address, a poll tick does not. */
    private var externalIpTransport: WearNetworkTransport? = null

    private val initialState = NetworkMonitorUiState(
        sections = sections,
        capabilities = capabilities,
        permissionsMissing = !repository.permissionsGranted()
    )

    val uiState: StateFlow<NetworkMonitorUiState> = combine(
        repository.snapshots(),
        preferencesRepository.viewMode,
        localState
    ) { snapshot, viewMode, local ->
        record(snapshot, viewMode, local)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), initialState)

    fun probeConnection(host: String = "1.1.1.1") {
        viewModelScope.launch {
            localState.update { it.copy(isProbing = true, probeResult = null) }
            val success = repository.probeReachability(host)
            localState.update { it.copy(isProbing = false, probeResult = success) }
        }
    }

    /** Drops the signal window so the graph starts measuring again from now. */
    fun restartSignalWindow() {
        signalWindow.clear()
        localState.update { it.copy(signalEpoch = it.signalEpoch + 1) }
    }

    /** Moves the traffic origin to the current totals; the live rates are untouched. */
    fun resetTrafficTotals() {
        val rate = uiState.value.snapshot?.trafficRate ?: return
        localState.update { it.copy(trafficOrigin = rate.totalRxBytes to rate.totalTxBytes) }
    }

    fun copyToClipboard(label: String, text: String) {
        try {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            val clip = ClipData.newPlainText(label, text)
            clipboard?.setPrimaryClip(clip)
            localState.update { it.copy(clipboardNotice = text) }
        } catch (e: SecurityException) {
            // Some OEM watch builds restrict clipboard access; that should not crash the screen.
            Timber.d(e, "Failed to copy text to clipboard")
        }
    }

    fun clearClipboardNotice() {
        localState.update { it.copy(clipboardNotice = null) }
    }

    private fun sectionFact(
        section: WearNetworkSection,
        snapshot: WearNetworkSnapshot
    ): WearSectionFact = when (section) {
        WearNetworkSection.Summary -> transportFact(snapshot)
        WearNetworkSection.Wifi -> wifiFact(snapshot)
        WearNetworkSection.Mobile -> mobileFact(snapshot)
        WearNetworkSection.Bluetooth -> booleanFact(snapshot.isBluetoothEnabled)
        WearNetworkSection.Gnss -> gnssFact(snapshot)
        WearNetworkSection.Traffic -> WearSectionFact.Rate(snapshot.trafficRate?.rxBytesPerSec ?: 0L)
        WearNetworkSection.Internet -> internetFact(snapshot)
        WearNetworkSection.History -> WearSectionFact.Entries(history.size)
    }

    private fun transportFact(snapshot: WearNetworkSnapshot): WearSectionFact =
        snapshot.activeTransport?.let { WearSectionFact.Literal(it.name) } ?: WearSectionFact.None

    private fun wifiFact(snapshot: WearNetworkSnapshot): WearSectionFact =
        snapshot.wifiNetworkName?.let { WearSectionFact.Literal(it) }
            ?: snapshot.wifiSignalDbm?.let { WearSectionFact.Signal(it) }
            ?: WearSectionFact.None

    private fun mobileFact(snapshot: WearNetworkSnapshot): WearSectionFact = when {
        !capabilities.hasMobile -> WearSectionFact.Named(WearFactKind.NoModem)
        snapshot.mobileOperator != null -> WearSectionFact.Literal(snapshot.mobileOperator)
        snapshot.hasMobileData == true -> WearSectionFact.Named(WearFactKind.Active)
        else -> WearSectionFact.Named(WearFactKind.NoSim)
    }

    private fun booleanFact(value: Boolean?): WearSectionFact = when (value) {
        true -> WearSectionFact.Named(WearFactKind.On)
        false -> WearSectionFact.Named(WearFactKind.Off)
        null -> WearSectionFact.None
    }

    private fun internetFact(snapshot: WearNetworkSnapshot): WearSectionFact =
        if (snapshot.hasInternet == true) {
            WearSectionFact.Named(WearFactKind.Reachable)
        } else {
            WearSectionFact.Named(WearFactKind.Offline)
        }

    private fun gnssFact(snapshot: WearNetworkSnapshot): WearSectionFact {
        val details = snapshot.gnssDetails?.takeIf { it.satellitesVisible > 0 }
        return when {
            details != null -> WearSectionFact.Satellites(details.satellitesUsed, details.satellitesVisible)
            snapshot.hasLocationProvider == true -> WearSectionFact.Named(WearFactKind.Ready)
            else -> WearSectionFact.None
        }
    }

    private fun record(
        snapshot: WearNetworkSnapshot,
        viewMode: WearViewMode,
        local: MonitorLocalState
    ): NetworkMonitorUiState {
        if (history.isEmpty() || history.last().activeTransport != snapshot.activeTransport) {
            history.add(snapshot)
        }
        if (history.size > HISTORY_LIMIT) {
            history.removeAt(0)
        }

        snapshot.wifiSignalDbm?.let { dbm ->
            signalWindow.add(dbm)
            if (signalWindow.size > SIGNAL_WINDOW_LIMIT) {
                signalWindow.removeAt(0)
            }
        }

        requestExternalIpIfLinkChanged(snapshot, local)

        val facts = sections.associateWith { section -> sectionFact(section, snapshot) }
        val totals = totalsSince(snapshot, local.trafficOrigin)

        return NetworkMonitorUiState(
            sections = sections,
            capabilities = capabilities,
            snapshot = snapshot,
            sectionFacts = facts,
            viewMode = viewMode,
            permissionsMissing = !repository.permissionsGranted(),
            history = history.toList(),
            signalHistory = signalWindow.toList(),
            externalIp = local.externalIp,
            trafficTotals = totals,
            isProbing = local.isProbing,
            probeResult = local.probeResult,
            clipboardMessage = local.clipboardNotice
        )
    }

    /** The totals the section shows: raw until the user reset them, relative to the origin after. */
    private fun totalsSince(
        snapshot: WearNetworkSnapshot,
        origin: Pair<Long, Long>?
    ): Pair<Long, Long>? = snapshot.trafficRate?.let { rate ->
        val originRx = origin?.first ?: 0L
        val originTx = origin?.second ?: 0L
        (rate.totalRxBytes - originRx).coerceAtLeast(0L) to
            (rate.totalTxBytes - originTx).coerceAtLeast(0L)
    }

    /**
     * The only field of this program that leaves the device, so it is asked for once per link and
     * never on a poll tick - five seconds apart, that would be twelve lookups a minute standing still.
     */
    private fun requestExternalIpIfLinkChanged(
        snapshot: WearNetworkSnapshot,
        local: MonitorLocalState
    ) {
        val transport = snapshot.activeTransport
        if (transport == null) {
            externalIpTransport = null
            if (local.externalIp != null) {
                localState.update { it.copy(externalIp = null) }
            }
            return
        }
        if (transport == externalIpTransport && local.externalIp != null) return
        externalIpTransport = transport
        viewModelScope.launch {
            val resolved = repository.resolveExternalIp()
            localState.update { it.copy(externalIp = resolved) }
        }
    }

    private companion object {
        const val HISTORY_LIMIT = 20
        const val SIGNAL_WINDOW_LIMIT = 24
    }
}
