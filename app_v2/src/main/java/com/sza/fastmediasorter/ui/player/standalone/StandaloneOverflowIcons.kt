package com.sza.fastmediasorter.ui.player.standalone

import android.content.Context
import androidx.appcompat.widget.PopupMenu
import com.sza.fastmediasorter.ui.common.tintIconsFromTheme

// S1407: PopupMenu hides item icons unless setForceShowIcon is called, which is why the standalone
// overflow rendered as plain text while the embedded player's did not - it was never a styling
// decision. Three of the app's four popups already make this call; this brings the fifth in line.

/**
 * S1407: show and tint the overflow item icons the same way `CommandPanelController` does for the
 * embedded player, so one command looks identical in both windows.
 *
 * Call immediately after `inflate(..)` and before the per-item visibility rules: tinting walks the
 * items that exist, so it must run once the menu is populated, and hiding an item afterwards does
 * not undo the tint. [context] is the popup's own context, whose theme decides the icon colour.
 */
fun PopupMenu.applyStandaloneOverflowIcons(context: Context) {
    setForceShowIcon(true)
    menu.tintIconsFromTheme(context)
}
