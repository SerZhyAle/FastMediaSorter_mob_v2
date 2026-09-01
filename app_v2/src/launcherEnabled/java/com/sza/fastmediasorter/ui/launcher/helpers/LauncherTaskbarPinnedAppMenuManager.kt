package com.sza.fastmediasorter.ui.launcher.helpers

import android.view.View
import android.widget.ListPopupWindow
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand

/** Context actions for an installed app pinned to the launcher taskbar. */
class LauncherTaskbarPinnedAppMenuManager(
    private val launchCommand: (LauncherCellCommand) -> Unit,
    private val unpin: (position: Int) -> Unit,
) {

    private var window: ListPopupWindow? = null

    /** Keeps the two taskbar-specific actions separate from the fuller all-apps menu. */
    fun show(anchor: View, command: LauncherCellCommand.App, position: Int): Boolean {
        if (!anchor.isAttachedToWindow || position < 0) return false
        dismiss()
        val context = anchor.context
        val rows = listOf(
            LauncherAppMenuRow.Action(
                context.getString(R.string.launcher_taskbar_pinned_action_launch),
                R.drawable.ic_open_in_browse,
            ) { launchCommand(command) },
            LauncherAppMenuRow.Action(
                context.getString(R.string.launcher_taskbar_pinned_action_unpin),
                R.drawable.ic_clear,
            ) { unpin(position) },
        )
        val popup = ListPopupWindow(context).apply {
            anchorView = anchor
            isModal = true
            width = maxOf(
                anchor.width,
                context.resources.getDimensionPixelSize(R.dimen.launcher_shortcut_popup_width),
            )
            setAdapter(LauncherAppShortcutAdapter(context, rows))
            setOnItemClickListener { _, _, rowPosition, _ ->
                dismiss()
                rows[rowPosition].onSelected()
            }
            setOnDismissListener { window = null }
        }
        window = popup
        popup.show()
        return true
    }

    fun dismiss() {
        window?.dismiss()
        window = null
    }
}
