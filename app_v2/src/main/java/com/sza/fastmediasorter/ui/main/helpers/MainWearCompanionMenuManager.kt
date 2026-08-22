package com.sza.fastmediasorter.ui.main.helpers

import android.content.Context
import androidx.appcompat.widget.PopupMenu
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.wear.WearCompanionActivity

/**
 * S1735: the Wear companion's own entry in the programs menu.
 *
 * A per-program helper rather than another branch inside the coordinator - strategic ADR-2. The coordinator
 * already carries every program's branches, so the next entry placed inside it is pure growth, while this
 * shape is one the same file already accepts for the mini-game, screen recording and link download.
 *
 * Visibility is decided by the caller and arrives as [enabled]: it is the user's setting AND the build's
 * watch bridge, and this class must not learn either of those on its own or the two surfaces could start
 * disagreeing about them.
 */
class MainWearCompanionMenuManager(
    private val context: Context
) {
    fun itemCount(enabled: Boolean): Int = if (enabled) 1 else 0

    fun populate(popup: PopupMenu, enabled: Boolean, order: Int): Int {
        if (!enabled) return 0
        popup.menu.add(0, MENU_ITEM_WEAR_COMPANION, order, R.string.wear_companion)
            .setIcon(R.drawable.ic_watch)
        return 1
    }

    fun handleMenuItem(itemId: Int): Boolean {
        if (itemId != MENU_ITEM_WEAR_COMPANION) return false
        context.startActivity(WearCompanionActivity.createIntent(context))
        return true
    }

    companion object {
        // Read by the coordinator to map this item to its new-window launch and its "Remove" write.
        const val MENU_ITEM_WEAR_COMPANION = 20
    }
}
