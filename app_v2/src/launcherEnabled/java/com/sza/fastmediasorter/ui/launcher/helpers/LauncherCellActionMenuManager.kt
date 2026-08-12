package com.sza.fastmediasorter.ui.launcher.helpers

import android.view.View
import android.widget.ListPopupWindow
import com.sza.fastmediasorter.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S1424: the long-press menu over a resource or stream cell of the launcher desktop.
 *
 * Presentation is a deliberate copy of [LauncherAppActionMenuManager]'s, per the owner's ruling
 * (strategic 3.3): the same gesture on two neighbouring cells has to look the same, so this reuses
 * that menu's row type and adapter rather than introducing a third way to show a list of actions
 * (strategic 5.1.3).
 *
 * It renders and nothing else - which rows exist is decided by the action catalogs, and what a row
 * does is decided by the per-kind action manager that built it.
 *
 * Not injected: every host owns an instance bound to its own scope, so a popup can never outlive the
 * surface that anchored it.
 */
class LauncherCellActionMenuManager(
    private val scope: CoroutineScope,
    private val resourceRows: suspend (resourceId: Long) -> List<LauncherAppMenuRow>,
    private val streamRows: suspend (streamId: String) -> List<LauncherAppMenuRow>,
) {

    private var window: ListPopupWindow? = null
    private var pendingQuery: Job? = null

    /** Opens the action menu for the resource behind [resourceId], anchored to [anchor]. */
    fun showForResource(anchor: View, resourceId: Long) {
        show(anchor) { resourceRows(resourceId) }
    }

    /** Opens the action menu for the channel behind [streamId], anchored to [anchor]. */
    fun showForStream(anchor: View, streamId: String) {
        show(anchor) { streamRows(streamId) }
    }

    /** Closes any open popup; hosts call this on their teardown edge. */
    fun dismiss() {
        pendingQuery?.cancel()
        pendingQuery = null
        window?.dismiss()
        window = null
    }

    private fun show(anchor: View, rowsOf: suspend () -> List<LauncherAppMenuRow>) {
        // A second long press must replace the first request, not stack a second window on top of it.
        dismiss()
        pendingQuery = scope.launch {
            val rows = rowsOf()
            Timber.d("Launcher cell action menu built with %d row(s)", rows.size)
            // An empty list means the target is gone or offers nothing here; leaving the popup shut
            // keeps such a cell behaving like an ordinary un-long-pressable one.
            if (rows.isEmpty() || !anchor.isAttachedToWindow) return@launch
            showPopup(anchor, rows)
        }
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
            select(rows[position])
        }
        popup.setOnDismissListener { window = null }
        window = popup
        popup.show()
    }

    /**
     * Only [LauncherAppMenuRow.Action] can appear here - a published app shortcut belongs to the app
     * menu, and a resource or a channel publishes none.
     */
    private fun select(row: LauncherAppMenuRow) {
        (row as? LauncherAppMenuRow.Action)?.onSelected?.invoke()
    }
}
