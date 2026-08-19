package com.sza.fastmediasorter.ui.launcher.helpers

import android.view.View
import android.widget.ListPopupWindow
import com.sza.fastmediasorter.R

/**
 * S1466: the four desktop entry points the quick menu spends, passed as one value.
 *
 * A bundle rather than four constructor parameters because [LauncherEditModeManager] already carries six
 * of its own, and the screen hands all four of these to it from the same place.
 *
 * @param addItem the picker with no square named - the taskbar "+" path, where the repository picks the
 *   position (S1209).
 * @param addItemAtSlot the same picker told which square was pressed.
 */
class LauncherDesktopActions(
    val addItem: () -> Unit,
    val addItemAtSlot: (row: Int, col: Int) -> Unit,
    val wallpaper: () -> Unit,
    val launcherSettings: () -> Unit,
)

/**
 * S1466: the menu a long press on an empty desktop square opens.
 *
 * Presentation reuses [LauncherAppMenuRow] and [LauncherAppShortcutAdapter] for the same reason
 * [LauncherCellActionMenuManager] does: a long press on the desktop and a long press on a cell are one
 * gesture to the user, so they may not produce two different-looking lists.
 *
 * The four actions are the owner's, fixed in strategic 3.3 in this order, and this class only shows
 * them - whether the gesture is allowed to open a menu at all is the caller's decision (strategic 2.4
 * keeps the locked desktop a silent refusal, and that guard belongs where the gesture is read).
 */
class LauncherDesktopQuickMenu(
    private val onAddItem: () -> Unit,
    private val onEditDesktop: () -> Unit,
    private val onWallpaper: () -> Unit,
    private val onLauncherSettings: () -> Unit,
) {

    private var window: ListPopupWindow? = null

    /**
     * Opens the menu at the pressed point of [anchor], given in that view's own coordinates.
     *
     * Offset down and right of the press rather than centred on it: the square under the finger is where
     * the new item will land, and a menu covering it hides the answer to "where am I adding this"
     * (strategic risk 4).
     */
    fun show(anchor: View, xPx: Int, yPx: Int) {
        // A second long press replaces the first menu instead of stacking a second window on it.
        dismiss()
        val context = anchor.context
        val rows = listOf(
            LauncherAppMenuRow.Action(
                label = context.getString(R.string.launcher_quick_menu_add_item),
                iconRes = R.drawable.ic_add,
                onSelected = onAddItem,
            ),
            LauncherAppMenuRow.Action(
                label = context.getString(R.string.launcher_quick_menu_edit_desktop),
                iconRes = R.drawable.ic_edit_20,
                onSelected = onEditDesktop,
            ),
            LauncherAppMenuRow.Action(
                label = context.getString(R.string.launcher_quick_menu_wallpaper),
                iconRes = R.drawable.ic_image,
                onSelected = onWallpaper,
            ),
            LauncherAppMenuRow.Action(
                label = context.getString(R.string.launcher_menu_launcher_settings),
                iconRes = R.drawable.ic_settings,
                onSelected = onLauncherSettings,
            ),
        )
        val popup = ListPopupWindow(context)
        popup.anchorView = anchor
        // Modal so D-pad, keyboard and mouse focus enter the list instead of staying on the desktop.
        popup.isModal = true
        popup.width = context.resources.getDimensionPixelSize(R.dimen.launcher_shortcut_popup_width)
        popup.horizontalOffset = xPx + POPUP_GAP_PX
        // ListPopupWindow measures its drop from the anchor's bottom edge, and the anchor here is the whole
        // desktop canvas - so the press position is that height minus where the finger landed.
        popup.verticalOffset = yPx - anchor.height + POPUP_GAP_PX
        popup.setAdapter(LauncherAppShortcutAdapter(context, rows))
        popup.setOnItemClickListener { _, _, position, _ ->
            popup.dismiss()
            rows[position].onSelected.invoke()
        }
        popup.setOnDismissListener { window = null }
        window = popup
        popup.show()
    }

    /** Closes any open menu; the host calls this on its teardown edge. */
    fun dismiss() {
        window?.dismiss()
        window = null
    }

    private companion object {
        /** Enough to clear the fingertip without detaching the menu from the square it belongs to. */
        const val POPUP_GAP_PX = 8
    }
}
