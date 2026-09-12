package com.sza.fastmediasorter.ui.browse.helpers

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.BrowseSwipeAction

/**
 * S2533: the one place a swipe action's label and icon are decided.
 *
 * Both the settings rows and the swipe background read from here, so an action is named with the
 * same words in the picker and under the sliding row. Every label is the key the row's own overflow
 * menu already uses - no second dictionary of operations.
 */
object BrowseSwipeActionCatalog {

    @StringRes
    fun labelResFor(action: BrowseSwipeAction): Int = when (action) {
        BrowseSwipeAction.NONE -> R.string.browse_swipe_action_none
        BrowseSwipeAction.OPEN_IN_PLAYER -> R.string.action_open
        BrowseSwipeAction.SEND_TO -> R.string.share_to_menu_title
        BrowseSwipeAction.INFO -> R.string.file_information
        BrowseSwipeAction.FAVORITE -> R.string.favorite
        BrowseSwipeAction.COPY -> R.string.copy
        BrowseSwipeAction.MOVE -> R.string.move
        BrowseSwipeAction.RENAME -> R.string.rename
        BrowseSwipeAction.EXTRACT_ARCHIVE -> R.string.unarchive_action_extract
        BrowseSwipeAction.DELETE -> R.string.delete
    }

    @DrawableRes
    fun iconResFor(action: BrowseSwipeAction): Int = when (action) {
        BrowseSwipeAction.NONE -> R.drawable.ic_gesture_action_none
        BrowseSwipeAction.OPEN_IN_PLAYER -> R.drawable.ic_play
        BrowseSwipeAction.SEND_TO -> R.drawable.ic_share
        BrowseSwipeAction.INFO -> R.drawable.ic_info
        BrowseSwipeAction.FAVORITE -> R.drawable.ic_star_outline
        BrowseSwipeAction.COPY -> R.drawable.ic_copy
        BrowseSwipeAction.MOVE -> R.drawable.ic_move
        // Extract has no icon of its own - its menu entry is text-only. ic_move is the nearest
        // existing drawable: extraction, like a move, lands the archive's contents elsewhere.
        BrowseSwipeAction.EXTRACT_ARCHIVE -> R.drawable.ic_move
        BrowseSwipeAction.RENAME -> R.drawable.ic_rename
        BrowseSwipeAction.DELETE -> R.drawable.ic_delete
    }

    /** Declaration order, so every surface offers the same order. */
    fun orderedForPicker(): List<BrowseSwipeAction> = BrowseSwipeAction.entries
}
