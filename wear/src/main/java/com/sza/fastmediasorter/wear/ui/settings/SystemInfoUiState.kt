package com.sza.fastmediasorter.wear.ui.settings

import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection

/**
 * What the system-information screen draws.
 *
 * [loading] is a field of its own rather than an empty [sections] list, because the two say different
 * things: the watch has not been asked yet, versus the watch answered nothing at all. Collapsing them
 * would show a blank screen while the report is still being read.
 */
data class SystemInfoUiState(
    val loading: Boolean = true,
    val sections: List<WearSystemInfoSection> = emptyList()
)
