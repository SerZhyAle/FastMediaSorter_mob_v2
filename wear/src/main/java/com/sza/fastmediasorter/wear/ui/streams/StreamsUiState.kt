package com.sza.fastmediasorter.wear.ui.streams

import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.WearViewMode

/**
 * S1708: UI state for the Wear OS streams screen.
 */
data class StreamsUiState(
    val channels: List<WearStreamChannel> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val viewMode: WearViewMode = WearViewMode.LIST
)
