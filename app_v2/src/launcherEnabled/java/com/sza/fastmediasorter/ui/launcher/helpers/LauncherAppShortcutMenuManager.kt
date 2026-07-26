package com.sza.fastmediasorter.ui.launcher.helpers

import android.graphics.Rect
import android.view.View
import android.widget.ListPopupWindow
import android.widget.Toast
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.launcher.AppShortcut
import com.sza.fastmediasorter.domain.usecase.launcher.QueryAppShortcutsUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.StartAppShortcutUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * S0427: the long-press popup listing an installed app's published quick actions.
 *
 * Nothing is shown when the app publishes none, when this app is not the active launcher, or when the
 * platform refuses the query - a long press then behaves exactly as it did before this feature.
 *
 * Not injected: the two hosts (home Activity and Start menu) each own an instance bound to their own
 * scope, so a popup can never outlive the surface that anchored it.
 */
class LauncherAppShortcutMenuManager(
    private val scope: CoroutineScope,
    private val queryAppShortcuts: QueryAppShortcutsUseCase,
    private val startAppShortcut: StartAppShortcutUseCase,
) {

    private var window: ListPopupWindow? = null
    private var pendingQuery: Job? = null

    /** Opens the quick actions of [packageName] anchored to [anchor]; no-op when there are none. */
    fun show(anchor: View, packageName: String) {
        // A second long press must replace the first request, not stack a second window on top of it.
        dismiss()
        pendingQuery = scope.launch {
            val shortcuts = queryAppShortcuts(packageName)
            if (shortcuts.isEmpty() || !anchor.isAttachedToWindow) return@launch
            showPopup(anchor, shortcuts)
        }
    }

    /** Closes any open popup; hosts call this on their teardown edge. */
    fun dismiss() {
        pendingQuery?.cancel()
        pendingQuery = null
        window?.dismiss()
        window = null
    }

    private fun showPopup(anchor: View, shortcuts: List<AppShortcut>) {
        val minWidth = anchor.context.resources.getDimensionPixelSize(R.dimen.launcher_shortcut_popup_width)
        val popup = ListPopupWindow(anchor.context)
        popup.anchorView = anchor
        // Modal so D-pad, keyboard and mouse focus enter the list instead of staying on the desktop.
        popup.isModal = true
        popup.width = maxOf(anchor.width, minWidth)
        popup.setAdapter(LauncherAppShortcutAdapter(anchor.context, shortcuts))
        popup.setOnItemClickListener { _, _, position, _ ->
            popup.dismiss()
            start(anchor, shortcuts[position])
        }
        popup.setOnDismissListener { window = null }
        window = popup
        popup.show()
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
}
