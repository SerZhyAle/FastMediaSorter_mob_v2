package com.sza.fastmediasorter.ui.main.helpers

/**
 * Handles a tap on the "Live Broadcast" item of the main Programs panel. The item itself is drawn from
 * the sub-program registry by [MainProgramsMenuCoordinator].
 */
class MainBroadcastMenuManager(
    private val onBroadcast: () -> Unit,
) {

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
