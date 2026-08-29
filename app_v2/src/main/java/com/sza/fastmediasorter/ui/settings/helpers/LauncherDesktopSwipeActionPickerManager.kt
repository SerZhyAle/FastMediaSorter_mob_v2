package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.LauncherDesktopSwipeAction
import com.sza.fastmediasorter.ui.dialog.ListSelectionAdapter
import com.sza.fastmediasorter.ui.dialog.ListSelectionConfig
import com.sza.fastmediasorter.ui.dialog.ListSelectionDialog

/** Presents only launcher desktop actions that have an executable route in this build. */
class LauncherDesktopSwipeActionPickerManager(
    private val systemActionsAvailable: Boolean,
) {

    fun labelFor(context: Context, action: LauncherDesktopSwipeAction): String = context.getString(
        when (action) {
            LauncherDesktopSwipeAction.OPEN_ALL_APPS -> R.string.launcher_desktop_swipe_action_all_apps
            LauncherDesktopSwipeAction.OPEN_NOTIFICATION_SHADE ->
                R.string.launcher_desktop_swipe_action_notification_shade
            LauncherDesktopSwipeAction.DO_NOT_USE -> R.string.launcher_desktop_swipe_action_do_not_use
        }
    )

    fun showPicker(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        current: LauncherDesktopSwipeAction,
        onPicked: (LauncherDesktopSwipeAction) -> Unit,
    ) {
        ListSelectionDialog(
            context = context,
            config = ListSelectionConfig(
                title = context.getString(R.string.launcher_desktop_swipe_picker_title),
                lifecycleOwner = lifecycleOwner,
                loader = { availableActions() },
                formatter = object : ListSelectionAdapter.ItemFormatter<LauncherDesktopSwipeAction> {
                    override fun getDisplayName(item: LauncherDesktopSwipeAction): String = labelFor(context, item)
                },
                hasSelection = true,
                isSelected = { it == current },
                allowClear = false,
                emptyMessageRes = R.string.launcher_desktop_swipe_action_do_not_use,
                errorMessageRes = R.string.launcher_desktop_swipe_action_do_not_use,
                onSelected = { action -> action?.let(onPicked) },
            ),
        ).show()
    }

    private fun availableActions(): List<LauncherDesktopSwipeAction> = buildList {
        add(LauncherDesktopSwipeAction.OPEN_ALL_APPS)
        if (systemActionsAvailable) add(LauncherDesktopSwipeAction.OPEN_NOTIFICATION_SHADE)
        add(LauncherDesktopSwipeAction.DO_NOT_USE)
    }
}
