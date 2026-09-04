package com.sza.fastmediasorter.wear.ui.streams

import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.WearViewMode

enum class StreamFilterKind {
    ALL,
    AUDIO_ONLY,
    VIDEO_ONLY
}

/**
 * S2146: the four keys the owner called useful. Declaration order is what the sort dialog renders,
 * so [MOST_USED] leads deliberately - it is also the default the screen starts on.
 *
 * `DEFAULT`, `TOPIC` and `LANGUAGE` are gone. The first returned the CSV's own row order, which is
 * indistinguishable from random to a reader; the other two, added by S1947, were named useless by the
 * owner on 2026-08-27 - see strategic ADR-1. Topic and language remain filter facets.
 */
enum class StreamSortOrder {
    MOST_USED,
    NAME_ASC,
    NAME_DESC,
    KIND
}

/**
 * S2146: one selectable value of a filter facet, with how many channels carry it.
 *
 * [id] is the raw catalogue string and is what the projection matches on; the row's shown text is
 * derived from it at render time. The count travels with the value because the list is ordered by it -
 * deriving the number again in the row would be the per-frame work strategic §7 forbids.
 *
 * The shape is deliberately the pair rather than a bare string: strategic §5.3 records that country
 * would fit it unchanged, should S1947 ever return country to the filter.
 */
data class StreamFacetValue(
    val id: String,
    val channelCount: Int
)

/**
 * S1708/S1871/S1947: UI state for the Wear OS streams screen.
 */
data class StreamsUiState(
    val channels: List<WearStreamChannel> = emptyList(),
    val displayChannels: List<WearStreamChannel> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val viewMode: WearViewMode = WearViewMode.LIST,
    val searchQuery: String = "",
    val filterKind: StreamFilterKind = StreamFilterKind.ALL,
    val sortOrder: StreamSortOrder = StreamSortOrder.MOST_USED,
    val selectedTopic: String? = null,
    val selectedLanguage: String? = null,
    val availableTopics: List<StreamFacetValue> = emptyList(),
    val availableLanguages: List<StreamFacetValue> = emptyList(),
    val showSearchDialog: Boolean = false,
    /**
     * S1946: no activity answered the request for text or speech input. The screen has to say so -
     * the dialog is already closed by then, so a silent refusal is indistinguishable from a search
     * that ran and matched everything.
     */
    val searchInputUnavailable: Boolean = false,
    val showFilterDialog: Boolean = false,
    val showSortDialog: Boolean = false,
    /**
     * S1954: normalized addresses of the marked channels, held in state so the display projection
     * stays a pure function of it. Read from the favourites store when the catalogue changes, not
     * per row, because a per-row lookup would touch the store on every scrolling frame.
     */
    val pinnedStreamIds: Set<String> = emptySet(),
    /**
     * S2149: folded identities of the channels pinned on the paired phone, held apart from
     * [pinnedStreamIds] rather than merged into it. The star means "I marked this here", so a pin the
     * phone sent must not become a watch mark the phone can no longer withdraw - the two are unioned
     * only when the top group is built.
     */
    val phonePinnedIdentities: Set<String> = emptySet()
)
