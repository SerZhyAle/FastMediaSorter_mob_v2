package com.sza.fastmediasorter.wear.domain.browse

import com.sza.fastmediasorter.wear.domain.model.WearContentType

/**
 * S2199: holds a remembered type filter until a list exists to check it against, then hands back only
 * the part that list can honour.
 *
 * Browse is entered separately per media type and per category, so a filter stored on one route can
 * name types the next route does not hold. Applied unchecked it empties the list, which would make
 * remembering the wearer's choice the cause of a screen that looks broken - strategic ADR-7.
 *
 * Its own class rather than a field and a method on the ViewModel: that class is at its function
 * ceiling, and the empty-versus-null distinction below is only assertable from a test if it lives
 * somewhere a test can reach.
 */
class BrowseRefineRestore {

    private var pending: Set<WearContentType>? = null

    /** An empty stored set is nothing to restore, so it is dropped rather than held. */
    fun remember(stored: Set<WearContentType>) {
        pending = stored.takeIf { it.isNotEmpty() }
    }

    /**
     * The remembered types this list can actually show, or null when there is nothing to apply.
     *
     * Null rather than an empty set, because an empty [BrowseRefineState.contentTypes] already means
     * "every type": handing one back would read as a filter the wearer cleared rather than one that
     * never applied. Consuming clears the holder either way, so a later reload of the same screen
     * cannot re-apply a filter the wearer has since switched off.
     */
    fun consume(present: Collection<WearContentType>): Set<WearContentType>? {
        val stored = pending ?: return null
        pending = null
        return stored.intersect(present.toSet()).takeIf { it.isNotEmpty() }
    }
}
