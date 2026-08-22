package com.sza.fastmediasorter.wear.ui.home

import com.sza.fastmediasorter.wear.domain.model.HomeSection
import com.sza.fastmediasorter.wear.domain.model.WearViewMode

/** What the home screen draws: the already-filtered, already-ordered sections and nothing else. */
data class HomeUiState(
    val sections: List<HomeSection> = emptyList(),
    val viewMode: WearViewMode = WearViewMode.LIST
)
