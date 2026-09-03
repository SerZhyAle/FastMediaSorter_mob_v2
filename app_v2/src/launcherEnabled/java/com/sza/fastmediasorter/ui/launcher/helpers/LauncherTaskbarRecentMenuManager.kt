package com.sza.fastmediasorter.ui.launcher.helpers

import android.view.View
import android.widget.ListPopupWindow
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import timber.log.Timber

/** Context actions for a command shown in the taskbar's recents strip. */
class LauncherTaskbarRecentMenuManager(
    private val launchCommand: (LauncherCellCommand) -> Unit,
    private val pinCommand: (LauncherCellCommand) -> Unit,
    private val removeFromRecents: (LauncherCellCommand) -> Unit,
) {

    private var window: ListPopupWindow? = null

    /** Shows the same modal action-list presentation as other launcher long-press menus. */
    fun show(anchor: View, command: LauncherCellCommand): Boolean {
        if (!anchor.isAttachedToWindow) return false
        Timber.d("S1901: show recent taskbar menu for %s", command)
        dismiss()
        val context = anchor.context
        val rows = listOf(
            LauncherAppMenuRow.Action(
                context.getString(R.string.launcher_app_action_launch),
                R.drawable.ic_open_in_browse,
            ) { launchCommand(command) },
            LauncherAppMenuRow.Action(
                context.getString(R.string.launcher_app_action_pin_taskbar),
                R.drawable.ic_pin,
            ) { pinCommand(command) },
            LauncherAppMenuRow.Action(
                context.getString(R.string.launcher_recent_action_remove),
                R.drawable.ic_clear,
            ) { removeFromRecents(command) },
        )
        val popup = ListPopupWindow(context).apply {
            anchorView = anchor
            isModal = true
            width = maxOf(
                anchor.width,
                context.resources.getDimensionPixelSize(R.dimen.launcher_shortcut_popup_width),
            )
            setAdapter(LauncherAppShortcutAdapter(context, rows))
            setOnItemClickListener { _, _, position, _ ->
                dismiss()
                rows[position].onSelected()
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
