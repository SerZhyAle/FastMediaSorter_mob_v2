package com.sza.fastmediasorter.ui.main.helpers

import android.content.Context
import android.content.Intent
import androidx.appcompat.widget.PopupMenu
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.streams.StreamsActivity

/**
 * Adds the "Трансляции" entry to the main-window dropdown menu and opens [StreamsActivity].
 * Mirrors [MainMiniGameMenuManager]; visibility is owned by the caller, which passes
 * `enabled = capabilityAvailability.isStreamsAvailable()` (S0678) so the entry is absent in photos.
 */
class MainStreamsMenuManager(
    private val context: Context
) {
    fun itemCount(enabled: Boolean): Int = if (enabled) 1 else 0

    fun populate(popup: PopupMenu, enabled: Boolean, order: Int): Int {
        if (!enabled) return 0
        popup.menu.add(0, MENU_ITEM_STREAMS, order, R.string.streams_title)
            .setIcon(R.drawable.ic_cast)
        return 1
    }

    fun handleMenuItem(itemId: Int): Boolean {
        if (itemId != MENU_ITEM_STREAMS) return false
        context.startActivity(Intent(context, StreamsActivity::class.java))
        return true
    }

    companion object {
        // S0770: read by MainActivity to map this item to its open-in-new-window launch.
        const val MENU_ITEM_STREAMS = 14
    }
}
