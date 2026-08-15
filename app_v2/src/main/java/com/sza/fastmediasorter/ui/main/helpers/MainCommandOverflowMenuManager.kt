package com.sza.fastmediasorter.ui.main.helpers

import androidx.appcompat.widget.PopupMenu
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.databinding.ActivityMainBinding

/**
 * S1672: surfaces the main-screen commands that did not fit on the bar inside the window's "⋮" menu,
 * the way [com.sza.fastmediasorter.ui.browse.managers.ResourceOpsMenuManager] surfaces the Browse
 * bar's evicted commands in the menu that screen already had. No command becomes unreachable when
 * the row is too narrow - owner decision 2026-08-15.
 *
 * The label list and the eviction verdict both come from [MainLayoutChromeManager]: two copies of
 * "which commands live on this bar" would drift the moment one of them gains a button.
 */
class MainCommandOverflowMenuManager(
    private val binding: ActivityMainBinding,
    private val chrome: MainLayoutChromeManager
) {

    /** Adds every evicted command to [popup] and returns how many items were added. */
    fun populate(popup: PopupMenu): Int {
        var added = 0
        chrome.commandLabels.forEach { (viewId, titleRes) ->
            if (chrome.isOverflowed(viewId)) {
                // Icon read live off the button: btnToggleView swaps between grid and list art, and a
                // second copy of that mapping here would show the stale one.
                popup.menu.add(MENU_GROUP, viewId, MENU_ORDER, titleRes).icon = commandView(viewId)?.icon
                added++
            }
        }
        return added
    }

    /**
     * Route an item back to its bar button. The button keeps its click listener while it is GONE, so
     * dispatching through it is what keeps the menu entry and the bar entry from drifting apart.
     */
    fun handleMenuItem(itemId: Int): Boolean {
        // Match against the known command ids first: this router also sees the programs menu's own
        // small integer ids, and a bare findViewById could in principle collide with a generated one.
        val known = chrome.commandLabels.any { (viewId, _) -> viewId == itemId }
        val button = if (known) commandView(itemId) else null
        button?.performClick()
        return button != null
    }

    private fun commandView(viewId: Int): MaterialButton? =
        binding.layoutControlButtons.findViewById(viewId)

    private companion object {
        const val MENU_GROUP = 0

        // Programs items occupy orders 1..10 (MainProgramsMenuCoordinator), so 0 keeps the evicted
        // commands above them.
        const val MENU_ORDER = 0
    }
}
