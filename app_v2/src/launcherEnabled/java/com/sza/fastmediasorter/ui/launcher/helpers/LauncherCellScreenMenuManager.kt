package com.sza.fastmediasorter.ui.launcher.helpers

import android.view.View
import android.widget.ListPopupWindow
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellUi

/**
 * S2301: the edit-mode menu of one desktop object - today, which screen to move it to.
 *
 * The tap that opens it was a deliberate no-op before this ticket: while editing, a scrim over the cell
 * swallows every touch so the cell cannot launch. Long press stays the drag, so the tap is the one
 * gesture free to carry an edit action.
 *
 * Presentation is the launcher's existing popup, row type and adapter, for the reason strategic §3.1
 * gives: a fourth way to show a list of actions would look like a different product on the same screen.
 *
 * Not injected: the host owns an instance bound to its own surface, so the popup can never outlive the
 * cell that anchored it.
 */
class LauncherCellScreenMenuManager(
    private val screenCount: () -> Int,
    private val onMoveToScreen: (cellId: Long, screenIndex: Int) -> Unit,
) {

    private var window: ListPopupWindow? = null

    /**
     * Opens the menu for [cell], anchored to [anchor]. A desktop of one screen has nothing to offer, so
     * it opens nothing rather than an empty popup - and neither does the row for the screen the cell
     * already sits on, which the repository would refuse anyway.
     */
    fun show(anchor: View, cell: LauncherCellUi) {
        dismiss()
        val count = screenCount()
        if (count <= SINGLE_SCREEN) return
        val rows = (0 until count)
            .filter { it != cell.cell.screenIndex }
            .map { screenIndex ->
                LauncherAppMenuRow.Action(
                    // Screens are numbered from 1 for the user; the stored index counts from 0.
                    label = anchor.context.getString(
                        R.string.launcher_edit_move_to_screen,
                        screenIndex + 1,
                    ),
                    iconRes = R.drawable.ic_arrow_forward,
                    onSelected = { onMoveToScreen(cell.cell.id, screenIndex) },
                )
            }
        if (rows.isEmpty() || !anchor.isAttachedToWindow) return
        showPopup(anchor, rows)
    }

    /** Closes any open popup; the host calls this on its teardown edge. */
    fun dismiss() {
        window?.dismiss()
        window = null
    }

    private fun showPopup(anchor: View, rows: List<LauncherAppMenuRow>) {
        val minWidth = anchor.context.resources.getDimensionPixelSize(R.dimen.launcher_shortcut_popup_width)
        val popup = ListPopupWindow(anchor.context)
        popup.anchorView = anchor
        // Modal so D-pad, keyboard and mouse focus enter the list instead of staying on the desktop.
        popup.isModal = true
        popup.width = maxOf(anchor.width, minWidth)
        popup.setAdapter(LauncherAppShortcutAdapter(anchor.context, rows))
        popup.setOnItemClickListener { _, _, position, _ ->
            popup.dismiss()
            (rows[position] as? LauncherAppMenuRow.Action)?.onSelected?.invoke()
        }
        popup.setOnDismissListener { window = null }
        window = popup
        popup.show()
    }

    private companion object {
        const val SINGLE_SCREEN = 1
    }
}
