package com.sza.fastmediasorter.wear.domain.browse

import com.sza.fastmediasorter.wear.domain.model.WearContentType

/**
 * S2136: how to read the four refine keys off one list's item type.
 *
 * The projection is written once and serves every content list on the watch, which is only possible
 * because it never names an item type - it asks this object instead. A key the item does not carry is
 * left null, and [availableSortOrders] then stops offering the orders that would need it: the phone's
 * folder listing knows no dates, and a date order there would be a choice with no consequence
 * (strategic ADR-5).
 */
class BrowseRefineKeys<T>(
    val name: (T) -> String,
    val contentType: (T) -> WearContentType,
    val dateModified: ((T) -> Long)? = null,
    val sizeBytes: ((T) -> Long?)? = null
) {

    /**
     * The orders this list can actually be shown in, in the order the dialog lists them.
     *
     * Built fresh each call but always in the same sequence, so reopening the dialog does not
     * reshuffle the rows under the wearer's finger.
     */
    fun availableSortOrders(): List<BrowseSortOrder> {
        val orders = mutableListOf(
            BrowseSortOrder.DEFAULT,
            BrowseSortOrder.NAME_ASC,
            BrowseSortOrder.NAME_DESC
        )
        if (dateModified != null) {
            orders += BrowseSortOrder.DATE_ASC
            orders += BrowseSortOrder.DATE_DESC
        }
        if (sizeBytes != null) {
            orders += BrowseSortOrder.SIZE_ASC
            orders += BrowseSortOrder.SIZE_DESC
        }
        return orders
    }
}

/**
 * S2136: turns a loaded list plus a refine state into the list to show.
 *
 * Pure by construction - no source access, no coroutine, no I/O - because every setter on a content
 * screen recomputes it, and the strategic performance budget (§3.2) is an in-memory pass over a list
 * that is already there.
 */
object BrowseListProjection {

    /**
     * The loaded [items] narrowed by the query and the type set, then ordered.
     *
     * Narrowing runs before sorting so the comparator only ever sees what survives, and an order
     * whose key the item type does not carry falls back to the incoming order rather than throwing:
     * a state can outlive the screen that produced it, and a stale order must not crash the next one.
     */
    fun <T> refine(
        items: List<T>,
        keys: BrowseRefineKeys<T>,
        state: BrowseRefineState
    ): List<T> {
        var result = items
        if (state.searchQuery.isNotBlank()) {
            val query = state.searchQuery.trim()
            result = result.filter { keys.name(it).contains(query, ignoreCase = true) }
        }
        if (state.contentTypes.isNotEmpty()) {
            result = result.filter { keys.contentType(it) in state.contentTypes }
        }
        return sortedBy(result, keys, state.sortOrder)
    }

    /**
     * The content types present in [items], in the order [WearContentType] declares them.
     *
     * A screen asks this to decide whether to offer the filter at all: one type means the list is
     * already homogeneous and the filter icon would be a button with no effect (strategic ADR-2).
     */
    fun <T> presentTypes(items: List<T>, keys: BrowseRefineKeys<T>): List<WearContentType> {
        val present = items.mapTo(mutableSetOf()) { keys.contentType(it) }
        return WearContentType.entries.filter { it in present }
    }

    private fun <T> sortedBy(
        items: List<T>,
        keys: BrowseRefineKeys<T>,
        order: BrowseSortOrder
    ): List<T> = when (order) {
        BrowseSortOrder.DEFAULT -> items
        BrowseSortOrder.NAME_ASC -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, keys.name))
        BrowseSortOrder.NAME_DESC ->
            items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, keys.name).reversed())
        BrowseSortOrder.DATE_ASC -> keys.dateModified?.let { key -> items.sortedBy(key) } ?: items
        BrowseSortOrder.DATE_DESC -> keys.dateModified?.let { key -> items.sortedByDescending(key) } ?: items
        BrowseSortOrder.SIZE_ASC -> keys.sizeBytes?.let { key -> sortedBySize(items, key, false) } ?: items
        BrowseSortOrder.SIZE_DESC -> keys.sizeBytes?.let { key -> sortedBySize(items, key, true) } ?: items
    }

    /**
     * Size order with the unknown sizes pushed to the end of both directions.
     *
     * A missing size is substituted at the far end of whichever direction is running, rather than
     * sorted as zero: an entry the source could not measure is not an empty entry, and listing it
     * first under "smallest" would state a size the app never learned.
     */
    private fun <T> sortedBySize(items: List<T>, key: (T) -> Long?, descending: Boolean): List<T> {
        val comparator = if (descending) {
            compareByDescending<T> { key(it) ?: Long.MIN_VALUE }
        } else {
            compareBy<T> { key(it) ?: Long.MAX_VALUE }
        }
        return items.sortedWith(comparator)
    }
}
