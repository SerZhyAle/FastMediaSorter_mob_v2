package com.sza.fastmediasorter.wear.ui.apps.netmonitor

import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkSection
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkSnapshot

/**
 * What the screen renders. The section list arrives already decided by the domain rule, so the
 * screen composes what it is given and never asks which sections this watch deserves.
 *
 * @param history the readings taken during this visit, newest last. It lives only as long as the
 * view model, which is what keeps the program from paying for anything after it is left.
 */
data class NetworkMonitorUiState(
    val sections: List<WearNetworkSection> = emptyList(),
    val snapshot: WearNetworkSnapshot? = null,
    val permissionsMissing: Boolean = false,
    val history: List<WearNetworkSnapshot> = emptyList()
)
