package com.sza.fastmediasorter.wear.domain.browse

import com.sza.fastmediasorter.wear.domain.model.WearContentType

/**
 * S2136: the orders a content list can be shown in.
 *
 * A subset of the phone's own `SortMode`, keeping its spelling so the two apps do not drift apart on
 * what a name order is called. Which of these a given screen actually offers is decided per list by
 * [BrowseRefineKeys.availableSortOrders], not here - strategic ADR-5.
 */
enum class BrowseSortOrder {
    DEFAULT,
    NAME_ASC,
    NAME_DESC,
    DATE_ASC,
    DATE_DESC,
    SIZE_ASC,
    SIZE_DESC
}

/**
 * S2136: what is currently narrowing and ordering a content list, plus which dialog is open.
 *
 * Held beside the loaded list rather than in place of it, so clearing a query re-projects what is
 * already in memory instead of asking the source again (strategic §5.1).
 */
data class BrowseRefineState(
    val searchQuery: String = "",

    /**
     * The types to keep, or an empty set meaning every type.
     *
     * Empty rather than "all seven listed" so a type added to [WearContentType] later does not have
     * to be added here too to keep being shown.
     */
    val contentTypes: Set<WearContentType> = emptySet(),
    val sortOrder: BrowseSortOrder = BrowseSortOrder.DEFAULT,

    /**
     * S2473: whether the refine menu is open over the list.
     *
     * One flag where there were three, because there is now one surface: sort and filter share it,
     * and search no longer has a surface at all - its icon starts the watch's text input directly.
     */
    val showRefineMenu: Boolean = false,

    /**
     * No activity answered the request for text or speech input. The screen has to say so, because
     * the dialog is already closed by then and a silent refusal reads as a search that matched
     * everything - the same trap S1946 recorded on the streams screen.
     */
    val searchInputUnavailable: Boolean = false
) {

    /**
     * Whether anything is currently narrowing or reordering the list.
     *
     * Read to tell an emptied result from an empty resource (strategic §2 goal 6): with nothing
     * active, an empty list means the resource holds nothing.
     */
    val isActive: Boolean
        get() = searchQuery.isNotBlank() ||
            contentTypes.isNotEmpty() ||
            sortOrder != BrowseSortOrder.DEFAULT
}
