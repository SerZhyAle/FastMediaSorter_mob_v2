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

    /**
     * Show [actions] in order, anchored to [anchor]. No-op when [actions] is empty. When [title] is set
     * (S0810), it renders as a disabled (greyed, non-clickable) header at the top - a name label so an
     * icon-only element is understandable, not another action.
     */
    fun show(anchor: View, actions: List<Action>, title: CharSequence? = null) {
        if (actions.isEmpty()) return
        val popup = PopupMenu(anchor.context, anchor)
        var order = 0
        if (!title.isNullOrBlank()) {
            popup.menu.add(0, TITLE_ITEM_ID, order++, title).isEnabled = false
        }
        actions.forEachIndexed { index, action ->
            popup.menu.add(0, index, order++, action.titleRes)
        }
        popup.setOnMenuItemClickListener { item ->
            actions.getOrNull(item.itemId)?.onClick?.invoke()
            true
        }
        popup.show()
    }

    // S0810: itemId for the disabled title header; never matches an action index, so clicks no-op.
    private const val TITLE_ITEM_ID = -1
}
