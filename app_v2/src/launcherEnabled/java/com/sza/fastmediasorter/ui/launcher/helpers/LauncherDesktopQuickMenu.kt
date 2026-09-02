package com.sza.fastmediasorter.ui.launcher.helpers

import android.view.View
import android.widget.ListPopupWindow
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
        val adapter = LauncherAppShortcutAdapter(context, rows)
        val popupWidthPx = context.resources.getDimensionPixelSize(R.dimen.launcher_shortcut_popup_width)
        val popup = ListPopupWindow(context)
        popup.anchorView = anchor
        // Modal so D-pad, keyboard and mouse focus enter the list instead of staying on the desktop.
        popup.isModal = true
        popup.width = popupWidthPx
        popup.horizontalOffset = xPx + POPUP_GAP_PX
        popup.verticalOffset = verticalOffsetPx(
            anchor = anchor,
            yPx = yPx,
            menuHeightPx = estimateMenuHeightPx(adapter, popupWidthPx, rows.size),
        )
        popup.setAdapter(adapter)
        popup.setOnItemClickListener { _, _, position, _ ->
            popup.dismiss()
            rows[position].onSelected.invoke()
        }
        popup.setOnDismissListener { window = null }
        window = popup
        popup.show()
    }

    /**
     * Below the press point by default; above it when the system-bar safe area (Rule 17) leaves less
     * room below than the menu needs and more room above than below (S2181) - without this, a press
     * near the bottom edge opens a menu whose last row lands under the nav bar/taskbar, unreachable.
     */
    private fun verticalOffsetPx(anchor: View, yPx: Int, menuHeightPx: Int): Int {
        val insets = ViewCompat.getRootWindowInsets(anchor)?.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        val topSafePx = insets?.top ?: 0
        val bottomSafePx = insets?.bottom ?: 0
        val spaceBelowPx = anchor.height - bottomSafePx - yPx
        val spaceAbovePx = yPx - topSafePx
        val opensAbove = spaceBelowPx < menuHeightPx + POPUP_GAP_PX && spaceAbovePx > spaceBelowPx
        // ListPopupWindow measures its drop from the anchor's bottom edge, and the anchor here is the
        // whole desktop canvas - so the press position is that height minus where the finger landed.
        return if (opensAbove) {
            yPx - anchor.height - menuHeightPx - POPUP_GAP_PX
        } else {
            yPx - anchor.height + POPUP_GAP_PX
        }
    }

    /**
     * One measured row stands in for the whole list: `item_launcher_app_shortcut.xml` is a fixed
     * single-line (`maxLines="1"`) layout, so every row is the same height regardless of label length,
     * and this stays correct under the user's font-scale setting because it measures rather than
     * assuming a hardcoded row-height dimension (none exists in this repo).
     */
    private fun estimateMenuHeightPx(adapter: LauncherAppShortcutAdapter, widthPx: Int, rowCount: Int): Int {
        val sampleRow = adapter.getView(0, null, null)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        sampleRow.measure(widthSpec, heightSpec)
        return sampleRow.measuredHeight * rowCount
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
