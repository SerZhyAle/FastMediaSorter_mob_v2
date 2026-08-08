package com.sza.fastmediasorter.core.menu

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R

/**
 * S1424: one row of a channel's action menu, wherever that menu is drawn.
 *
 * Entries and order mirror the streams screen's tile menu, which is the fuller of the two branches -
 * the list row keeps pin/unpin as its own button instead of a menu row.
 *
 * Unlike [ResourceMenuAction] an entry carries no caption: two of the rows say the opposite thing
 * depending on the channel's current state, so the caption is resolved by
 * [StreamActionCatalog.labelRes] rather than frozen into the entry.
 */
enum class StreamMenuAction(@param:DrawableRes val iconRes: Int) {
    TOGGLE_PIN(R.drawable.ic_pin),
    MOVE_UP(R.drawable.ic_arrow_upward),
    MOVE_DOWN(R.drawable.ic_arrow_downward),
    MOVE_TO_TOP(R.drawable.ic_arrow_upward),
    TOGGLE_FAVORITE(R.drawable.ic_favorite),
    ADD_SHORTCUT(R.drawable.ic_widget_resource_launch),
    EDIT(R.drawable.ic_edit_20),

    // S1474: sits with the other reading actions, above sharing and well clear of removal.
    ABOUT_CHANNEL(R.drawable.ic_info),
    SHARE_LINK(R.drawable.ic_share),
    REMOVE(R.drawable.ic_delete),
    ;

    /**
     * Item id for a programmatically built `Menu`. The streams screen has no menu XML to borrow ids
     * from, so the entry is its own id - offset past `Menu.NONE`, which is 0 and would make the first
     * row's id indistinguishable from "no id".
     */
    val menuItemId: Int get() = ordinal + FIRST_MENU_ITEM_ID

    companion object {

        private const val FIRST_MENU_ITEM_ID = 1

        /** The action a menu row stands for, or null for a row no action owns. */
        fun byMenuItemId(itemId: Int): StreamMenuAction? = entries.getOrNull(itemId - FIRST_MENU_ITEM_ID)
    }
}

/**
 * S1424: the single source of a channel menu's composition, and the counterpart of
 * [ResourceActionCatalog] for the second cell kind the launcher long press has to serve
 * (strategic 2, goal 2).
 *
 * Composition only - presence, not enabled state. The streams screen still decides on its own which
 * reorder row is greyed at the ends of the pinned block, because that depends on the channel's index
 * inside a list this object never sees.
 */
object StreamActionCatalog {

    /** Everything about a channel that changes what it offers. */
    data class Facts(
        val isPinned: Boolean = false,
        val isFavorite: Boolean = false,
        val favoritesEnabled: Boolean = false,
        val isReorderable: Boolean = false,
        val isManualOrigin: Boolean = false,
    )

    /**
     * Strategic 5.2. Reordering needs the whole pinned block rather than one identifier, and the
     * favourite toggle reads state that exists only while the streams screen is open.
     */
    private val DESKTOP_EXCLUDED = setOf(
        StreamMenuAction.MOVE_UP,
        StreamMenuAction.MOVE_DOWN,
        StreamMenuAction.MOVE_TO_TOP,
        StreamMenuAction.TOGGLE_FAVORITE,
        // S1474: the window measures the transmission, which needs a decoder and the streams screen's
        // own probe manager. A desktop cell owns neither, and strategic §11 names only the card and the
        // player menus as entry points.
        StreamMenuAction.ABOUT_CHANNEL,
    )

    /**
     * Items [surface] offers for a channel described by [facts], in menu order.
     *
     * [canRun] is the caller's own veto over a row it will not serve - either because it cannot yet
     * carry the action out, which keeps an unwired row absent rather than dead (strategic ADR-2), or
     * because it renders that action somewhere else. The streams list row uses the second reason:
     * pin/unpin is a button on the row itself there, and only the tile folds it into the menu.
     */
    fun actionsFor(
        surface: MenuActionSurface,
        facts: Facts,
        canRun: (StreamMenuAction) -> Boolean = { true },
    ): List<StreamMenuAction> = StreamMenuAction.entries.filter { action ->
        isRelevant(action, facts) && isOfferedOn(surface, action) && canRun(action)
    }

    /** The caption of [action] for a channel in state [facts]; two rows flip with that state. */
    @StringRes
    fun labelRes(action: StreamMenuAction, facts: Facts): Int = when (action) {
        StreamMenuAction.TOGGLE_PIN -> if (facts.isPinned) R.string.streams_unpin else R.string.streams_pin
        StreamMenuAction.MOVE_UP -> R.string.streams_move_up
        StreamMenuAction.MOVE_DOWN -> R.string.streams_move_down
        StreamMenuAction.MOVE_TO_TOP -> R.string.streams_move_to_top
        StreamMenuAction.TOGGLE_FAVORITE -> favoriteLabelRes(facts)
        StreamMenuAction.ADD_SHORTCUT -> R.string.streams_add_to_home_screen
        StreamMenuAction.EDIT -> R.string.streams_edit
        StreamMenuAction.ABOUT_CHANNEL -> R.string.stream_info_menu_title
        StreamMenuAction.SHARE_LINK -> R.string.streams_send_link
        StreamMenuAction.REMOVE -> R.string.streams_remove
    }

    @StringRes
    private fun favoriteLabelRes(facts: Facts): Int = if (facts.isFavorite) {
        R.string.streams_remove_from_favorites
    } else {
        R.string.streams_add_to_favorites
    }

    private fun isOfferedOn(surface: MenuActionSurface, action: StreamMenuAction): Boolean =
        surface != MenuActionSurface.LAUNCHER_DESKTOP || action !in DESKTOP_EXCLUDED

    /** The streams screen's existing gates, moved here unchanged. */
    private fun isRelevant(action: StreamMenuAction, facts: Facts): Boolean = when (action) {
        StreamMenuAction.MOVE_UP, StreamMenuAction.MOVE_DOWN, StreamMenuAction.MOVE_TO_TOP ->
            facts.isReorderable

        StreamMenuAction.TOGGLE_FAVORITE -> facts.favoritesEnabled
        StreamMenuAction.EDIT -> facts.isManualOrigin
        else -> true
    }
}
