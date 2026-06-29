package com.sza.fastmediasorter.ui.main.helpers

import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.widget.PopupMenu

/**
 * S0770: builds the per-item context menu shared by the main-window programs panel (S0755) and streams
 * panel (S0756). Each element offers Open, an optional "Open in new window", and an optional "Remove";
 * the host decides which actions exist, this only renders them as a [PopupMenu] anchored to the item.
 */
object PanelItemContextMenu {

    /** One menu entry: a title resource and the action to run when it is chosen. */
    data class Action(@StringRes val titleRes: Int, val onClick: () -> Unit)

    /** Show [actions] in order, anchored to [anchor]. No-op when [actions] is empty. */
    fun show(anchor: View, actions: List<Action>) {
        if (actions.isEmpty()) return
        val popup = PopupMenu(anchor.context, anchor)
        actions.forEachIndexed { index, action ->
            popup.menu.add(0, index, index, action.titleRes)
        }
        popup.setOnMenuItemClickListener { item ->
            actions.getOrNull(item.itemId)?.onClick?.invoke()
            true
        }
        popup.show()
    }
}
