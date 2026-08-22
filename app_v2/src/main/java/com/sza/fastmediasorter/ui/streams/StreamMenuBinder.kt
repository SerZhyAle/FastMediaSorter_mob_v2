package com.sza.fastmediasorter.ui.streams

import android.view.Menu
import com.sza.fastmediasorter.core.menu.MenuActionSurface
import com.sza.fastmediasorter.core.menu.StreamActionCatalog
import com.sza.fastmediasorter.core.menu.StreamMenuAction
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity

/**
 * S1424: fills a channel's overflow menu from [StreamActionCatalog], for the streams-screen list row
 * and its tile alike.
 *
 * The two adapters used to build the same menu by hand, twice, and had already drifted apart - the
 * tile folds pin/unpin into the menu while the row keeps it as a button. Composition has one home
 * now; each adapter still routes a chosen row to its own callbacks, which are its own.
 */
object StreamMenuBinder {

    private const val ORIGIN_MANUAL = "MANUAL"

    /**
     * [pinnedRows] is the ordered pinned block, which is what decides whether reordering has anywhere
     * to go - a single pinned channel offers no reorder row (S0938).
     */
    fun factsOf(
        source: StreamSourceEntity,
        pinnedRows: List<StreamSourceEntity>,
        favoritesEnabled: Boolean,
        isFavorite: Boolean,
        wearSendAvailable: Boolean,
    ) = StreamActionCatalog.Facts(
        isPinned = source.pinned,
        isFavorite = isFavorite,
        favoritesEnabled = favoritesEnabled,
        isReorderable = source.pinned && pinnedRows.size > 1,
        // CATALOG and IMPORTED rows are owned by their sync and must not be hand-edited (S0660 6.4).
        isManualOrigin = source.sourceOrigin == ORIGIN_MANUAL,
        wearSendAvailable = wearSendAvailable,
    )

    fun build(
        menu: Menu,
        source: StreamSourceEntity,
        pinnedRows: List<StreamSourceEntity>,
        facts: StreamActionCatalog.Facts,
        canRun: (StreamMenuAction) -> Boolean,
    ) {
        val pinnedIndex = pinnedRows.indexOfFirst { it.id == source.id }
        StreamActionCatalog.actionsFor(MenuActionSurface.MAIN_WINDOW, facts, canRun).forEach { action ->
            // Order = Menu.NONE on every item, so the menu follows add-order without order literals.
            menu.add(Menu.NONE, action.menuItemId, Menu.NONE, StreamActionCatalog.labelRes(action, facts))
                .isEnabled = isEnabled(action, pinnedIndex, pinnedRows.lastIndex)
        }
    }

    /**
     * Whether a row is present is the catalog's call; whether it is greyed is not. Sitting at an end
     * of the pinned block depends on where this channel is in a list the catalog never sees (S0938).
     */
    private fun isEnabled(action: StreamMenuAction, pinnedIndex: Int, lastPinnedIndex: Int): Boolean =
        when (action) {
            StreamMenuAction.MOVE_UP, StreamMenuAction.MOVE_TO_TOP -> pinnedIndex > 0
            StreamMenuAction.MOVE_DOWN -> pinnedIndex < lastPinnedIndex
            else -> true
        }
}
