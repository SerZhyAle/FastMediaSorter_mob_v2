package com.sza.fastmediasorter.ui.main.helpers

import android.content.Context
import androidx.appcompat.widget.PopupMenu
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.game.GameLaunchIntents

class MainMiniGameMenuManager(
    private val context: Context
) {
    fun itemCount(enabled: Boolean): Int = if (enabled) 1 else 0

    fun populate(popup: PopupMenu, enabled: Boolean, order: Int): Int {
        if (!enabled) return 0
        popup.menu.add(0, MENU_ITEM_GAME, order, R.string.game_menu_label)
            .setIcon(R.drawable.ic_game_kryvavitsa)
        return 1
    }

    fun handleMenuItem(itemId: Int): Boolean {
        if (itemId != MENU_ITEM_GAME) return false
        context.startActivity(GameLaunchIntents.game(context))
        return true
    }

    companion object {
        private const val MENU_ITEM_GAME = 2
    }
}