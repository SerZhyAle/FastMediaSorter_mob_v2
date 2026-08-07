package com.sza.fastmediasorter.ui.player.standalone

import android.graphics.Color
import androidx.appcompat.widget.PopupMenu

// S1407: PopupMenu hides item icons unless setForceShowIcon is called, which is why the standalone
// overflow rendered as plain text while the embedded player's did not - it was never a styling
// decision. Three of the app's four popups already make this call; this brings the fifth in line.
private const val OVERFLOW_ICON_TINT = Color.DKGRAY

/**
 * S1407: show and tint the overflow item icons the same way `CommandPanelController` does for the
 * embedded player, so one command looks identical in both windows.
 *
 * Call immediately after `inflate(..)` and before the per-item visibility rules: tinting walks the
 * items that exist, so it must run once the menu is populated, and hiding an item afterwards does
 * not undo the tint.
 */
fun PopupMenu.applyStandaloneOverflowIcons() {
    setForceShowIcon(true)
    tintMenuIcons(menu)
    timber.log.Timber.d("S1407: standalone overflow icons enabled, ${menu.size()} top-level items")
}

private fun tintMenuIcons(menu: android.view.Menu) {
    for (i in 0 until menu.size()) {
        val item = menu.getItem(i)
        item.icon?.let { icon ->
            icon.setTint(OVERFLOW_ICON_TINT)
            item.icon = icon
        }
        // Children of a section carry their own icons and are not reached by walking the top level.
        item.subMenu?.let { tintMenuIcons(it) }
    }
}
