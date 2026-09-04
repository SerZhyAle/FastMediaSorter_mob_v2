package com.sza.fastmediasorter.wear.ui.home

import com.sza.fastmediasorter.wear.domain.model.HomeSection
import com.sza.fastmediasorter.wear.domain.model.WearViewMode

/**
 * What the home screen draws: the already-filtered, already-ordered sections and nothing else.
 *
 * S1974: [lastUsedResources] is held apart from [sections] rather than prepended to it. The screen
 * chunks [sections] into rows, so a shortcut inside that list would shift every predefined section by
 * one cell each time it appeared or vanished; kept apart, it can own a row of its own instead.
 */
data class HomeUiState(
    val lastUsedResources: List<HomeSection> = emptyList(),
    val sections: List<HomeSection> = emptyList(),
    val viewMode: WearViewMode = WearViewMode.LIST
)

/**
 * S2524: what the playback service is playing right now, as the home screen needs to draw it.
 *
 * Held outside [HomeUiState] rather than inside it, for the same reason S1974 holds the shortcuts
 * apart: this row appears and disappears with the sound, and the screen chunks [HomeUiState.sections]
 * into grid rows, so a transient member of that list would move every section cell each time the
 * sound started or stopped.
 *
 * @param fileId the file to reopen, or null when the session carries none - a background session
 *   started from a source with no library file has no player address, so the row can only stop it.
 */
data class HomeNowPlaying(
    val title: String,
    val subtitle: String?,
    val fileId: Long?
)
