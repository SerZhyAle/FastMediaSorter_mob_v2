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
