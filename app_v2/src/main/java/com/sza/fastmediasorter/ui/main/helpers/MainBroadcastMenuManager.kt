package com.sza.fastmediasorter.ui.main.helpers

import androidx.appcompat.widget.PopupMenu
import com.sza.fastmediasorter.R

/**
 * Manages the "Live Broadcast" menu item in the main Programs panel.
 */
class MainBroadcastMenuManager(
    private val onBroadcast: () -> Unit,
) {

    fun itemCount(enabled: Boolean): Int = if (enabled) 1 else 0

    fun populate(popup: PopupMenu, enabled: Boolean, order: Int): Int {
        if (!enabled) return 0
        popup.menu.add(0, MENU_ITEM_BROADCAST, order, R.string.broadcast_menu_label)
            .setIcon(R.drawable.ic_display)
        return 1
    }

    fun handleMenuItem(itemId: Int): Boolean = when (itemId) {
        MENU_ITEM_BROADCAST -> {
            onBroadcast()
            true
        }
        else -> false
    }

    companion object {
        const val MENU_ITEM_BROADCAST = 24
    }
}
