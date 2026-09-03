package com.sza.fastmediasorter.wear.ui.apps.netmonitor

import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkCapabilities
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkSection
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkSnapshot

/**
 * A section button's short fact, named rather than worded.
 *
 * The ViewModel decides WHICH fact is true, the screen decides the WORDS. A fact assembled as a
 * string where no resources are reachable ships one language to all thirteen locales, and the button
 * panel is the most-read surface of this program.
 */
sealed interface WearSectionFact {

    /** Nothing measured yet - the button carries its name alone. */
    data object None : WearSectionFact

    /** A name the platform already returned in the user's own terms: an SSID, an operator. */
    data class Literal(val text: String) : WearSectionFact

    data class Named(val kind: WearFactKind) : WearSectionFact

    data class Satellites(val used: Int, val visible: Int) : WearSectionFact

    data class Rate(val bytesPerSec: Long) : WearSectionFact

    data class Entries(val count: Int) : WearSectionFact

    data class Signal(val dbm: Int) : WearSectionFact
}

enum class WearFactKind {
    On,
    Off,
    NoModem,
    NoSim,
    Active,
    Ready,
    Reachable,
    Offline,
}

/**
 * What the Wear Network Monitor screen renders.
 *
 * @param sections the list of sections available on this watch.
 * @param capabilities hardware capabilities of the watch.
 * @param sectionFacts live short fact per section, named and not yet worded.
 * @param history the readings taken during this visit, newest last.
 * @param signalHistory the Wi-Fi signal window, oldest first, cleared by the section's own action.
 * @param externalIp the address the watch is seen under from outside, null until it answers.
 * @param trafficTotals received and sent bytes counted from the origin the user last reset.
 */
data class NetworkMonitorUiState(
    val sections: List<WearNetworkSection> = emptyList(),
    val capabilities: WearNetworkCapabilities? = null,
    val snapshot: WearNetworkSnapshot? = null,
    val sectionFacts: Map<WearNetworkSection, WearSectionFact> = emptyMap(),
    val permissionsMissing: Boolean = false,
    val history: List<WearNetworkSnapshot> = emptyList(),
    val signalHistory: List<Int> = emptyList(),
    val externalIp: String? = null,
    val trafficTotals: Pair<Long, Long>? = null,
    val isProbing: Boolean = false,
    val probeResult: Boolean? = null,
    val clipboardMessage: String? = null
)
