package com.sza.fastmediasorter.wear.ui.apps.systeminfo

import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection

/**
 * What the system-information screen draws.
 *
 * [loading] is a field of its own rather than an empty [sections] list, because the two say different
 * things: the watch has not been asked yet, versus the watch answered nothing at all. Collapsing them
 * would show a blank screen while the report is still being read.
 *
 * [refreshing] is separate from [loading] for the same class of reason: a re-read has a report to show
 * already, and blanking it would take the reading away from the user at the moment they asked for a
 * fresher one.
 */
data class SystemInfoUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val sections: List<WearSystemInfoSection> = emptyList()
)
