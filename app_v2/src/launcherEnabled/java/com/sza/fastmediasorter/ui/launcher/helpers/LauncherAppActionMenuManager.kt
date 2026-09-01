package com.sza.fastmediasorter.ui.launcher.helpers

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.widget.ListPopupWindow
import android.widget.Toast
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.launcher.AppShortcut
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S1401: the one long-press menu over an installed app - the launcher's own actions first, then the
 * quick actions the app itself publishes.
 *
 * Replaces the shortcuts-only popup so the desktop and the all-apps screen share one behaviour instead
 * of two similar ones (strategic 5.1).
 *
 * The operations arrive as functions, not use cases: each host routes them through its own ViewModel
 * rather than injecting domain types into a view (CLAUDE.md Rule 3) - the same seam the popup it
 * replaces already used.
 *
 * Not injected: every host owns an instance bound to its own scope, so a popup can never outlive the
 * surface that anchored it.
 */
class LauncherAppActionMenuManager(
    private val scope: CoroutineScope,
    private val queryAppShortcuts: suspend (packageName: String) -> List<AppShortcut>,
    private val startAppShortcut: suspend (shortcut: AppShortcut, bounds: Rect) -> Boolean,
    private val launchApp: (packageName: String) -> Unit,
    private val placeOnDesktop: (packageName: String) -> Unit,
    private val pinToTaskbar: (packageName: String) -> Unit,
    private val appInfoIntent: (packageName: String) -> Intent?,
    private val uninstallIntent: (packageName: String) -> Intent?,
    private val removeDesktopCell: ((cellId: Long) -> Unit)? = null,
) {

    private var window: ListPopupWindow? = null
    private var pendingQuery: Job? = null

    /** Opens the action menu for [packageName] anchored to [anchor]. */
    fun show(anchor: View, packageName: String, desktopCellId: Long? = null) {
        Timber.d("S2316: app action menu desktopCellId=%s", desktopCellId)
        // A second long press must replace the first request, not stack a second window on top of it.
        dismiss()
        pendingQuery = scope.launch {
            val rows = buildRows(anchor, packageName, desktopCellId)
            if (rows.isEmpty() || !anchor.isAttachedToWindow) return@launch
            showPopup(anchor, rows)
        }
    }

    /** Closes any open popup; hosts call this on their teardown edge. */
    fun dismiss() {
        pendingQuery?.cancel()
        pendingQuery = null
        window?.dismiss()
        window = null
    }

    /**
     * An entry whose action cannot run on this device is left out rather than shown greyed: the user
     * never taps something that was going to refuse (strategic 5.1).
     */
    private suspend fun buildRows(
        anchor: View,
        packageName: String,
        desktopCellId: Long?,
    ): List<LauncherAppMenuRow> {
        val rows = mutableListOf<LauncherAppMenuRow>()
        rows += action(anchor, R.string.launcher_app_action_launch, R.drawable.ic_open_in_browse) {
            launchApp(packageName)
        }
        if (desktopCellId == null) {
            rows += action(anchor, R.string.launcher_app_action_to_desktop, R.drawable.ic_add) {
                placeOnDesktop(packageName)
            }
        } else {
            removeDesktopCell?.let { remove ->
                rows += action(anchor, R.string.remove_action, R.drawable.ic_delete) {
                    remove(desktopCellId)
                }
            }
        }
        rows += action(anchor, R.string.launcher_app_action_pin_taskbar, R.drawable.ic_pin) {
            pinToTaskbar(packageName)
        }
        appInfoIntent(packageName)?.let { intent ->
            rows += action(anchor, R.string.launcher_app_action_app_info, R.drawable.ic_info) {
                startSystemIntent(anchor, intent)
            }
        }
        uninstallIntent(packageName)?.let { intent ->
            rows += action(anchor, R.string.launcher_app_action_uninstall, R.drawable.ic_delete) {
                startSystemIntent(anchor, intent)
            }
        }
        rows += queryAppShortcuts(packageName).map(LauncherAppMenuRow::Shortcut)
        return rows
    }

    private fun action(
        anchor: View,
        labelRes: Int,
        iconRes: Int,
        onSelected: () -> Unit
    ) = LauncherAppMenuRow.Action(anchor.context.getString(labelRes), iconRes, onSelected)

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
            select(anchor, rows[position])
        }
        popup.setOnDismissListener { window = null }
        window = popup
        popup.show()
    }

    private fun select(anchor: View, row: LauncherAppMenuRow) {
        when (row) {
            is LauncherAppMenuRow.Action -> row.onSelected()
            is LauncherAppMenuRow.Shortcut -> start(anchor, row.shortcut)
        }
    }

    private fun start(anchor: View, shortcut: AppShortcut) {
        scope.launch {
            // The system uses the anchor's screen rect as the launch animation origin.
            val bounds = Rect().also { anchor.getGlobalVisibleRect(it) }
            if (startAppShortcut(shortcut, bounds)) return@launch
            Toast.makeText(
                anchor.context,
                R.string.launcher_app_shortcut_start_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /**
     * The handler was resolved when the row was built, but a package can be removed between the two,
     * so the launch still has to survive a missing target.
     */
    private fun startSystemIntent(anchor: View, intent: Intent) {
        try {
            anchor.context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Timber.i(e, "System action %s has no handler any more", intent.action)
            Toast.makeText(
                anchor.context,
                R.string.launcher_app_shortcut_start_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
