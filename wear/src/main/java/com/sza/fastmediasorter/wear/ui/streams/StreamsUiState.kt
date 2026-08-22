package com.sza.fastmediasorter.wear.ui.streams

import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.WearViewMode

enum class StreamFilterKind {
    ALL,
    AUDIO_ONLY,
    VIDEO_ONLY
}

enum class StreamSortOrder {
    DEFAULT,
    NAME_ASC,
    NAME_DESC,
    KIND
}

/**
 * S1708/S1871: UI state for the Wear OS streams screen.
 */
data class StreamsUiState(
    val channels: List<WearStreamChannel> = emptyList(),
    val displayChannels: List<WearStreamChannel> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val viewMode: WearViewMode = WearViewMode.LIST,
    val searchQuery: String = "",
    val filterKind: StreamFilterKind = StreamFilterKind.ALL,
    val sortOrder: StreamSortOrder = StreamSortOrder.DEFAULT,
    val showSearchDialog: Boolean = false,
    /**
     * S1946: no activity answered the request for text or speech input. The screen has to say so -
     * the dialog is already closed by then, so a silent refusal is indistinguishable from a search
     * that ran and matched everything.
     */
    val searchInputUnavailable: Boolean = false,
    val showFilterDialog: Boolean = false,
    val showSortDialog: Boolean = false
)
