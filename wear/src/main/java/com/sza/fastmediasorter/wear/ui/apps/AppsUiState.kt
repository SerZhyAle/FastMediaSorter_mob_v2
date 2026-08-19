package com.sza.fastmediasorter.wear.ui.apps

import com.sza.fastmediasorter.wear.domain.model.WearApp
import com.sza.fastmediasorter.wear.domain.model.WearViewMode

/** What the Apps screen draws: the already-filtered, already-ordered programs and nothing else. */
data class AppsUiState(
    val apps: List<WearApp> = emptyList(),
    val viewMode: WearViewMode = WearViewMode.LIST
)
