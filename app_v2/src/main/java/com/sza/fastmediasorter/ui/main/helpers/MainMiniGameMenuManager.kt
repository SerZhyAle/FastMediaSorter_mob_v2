package com.sza.fastmediasorter.ui.main.helpers

import android.content.Context
import com.sza.fastmediasorter.core.game.GameLaunchIntents

/**
 * S2673: dispatches the mini-game tap. The entry itself is drawn by MainProgramsMenuCoordinator from
 * the sub-program registry.
 */
class MainMiniGameMenuManager(
    private val context: Context
) {

    fun handleMenuItem(itemId: Int): Boolean {
        if (itemId != MENU_ITEM_GAME) return false
        context.startActivity(GameLaunchIntents.game(context))
        return true
    }

    companion object {
        // S0770: read by MainActivity to map this item to its new-window launch + "Remove" write.
        const val MENU_ITEM_GAME = 2
    }
}
