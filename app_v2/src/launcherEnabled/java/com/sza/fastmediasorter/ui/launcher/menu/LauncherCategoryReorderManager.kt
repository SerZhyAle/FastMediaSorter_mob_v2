package com.sza.fastmediasorter.ui.launcher.menu

import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R

/**
 * S4530: the category reorder mode - entered by a long press on a category header, left with Done.
 *
 * The moved group stays selected across moves, so repeated presses walk it through the list. "Unknown"
 * cannot be selected: it is always the last group.
 */
class LauncherCategoryReorderManager(
    private val onModeChanged: () -> Unit,
    private val onMove: (categoryId: String, offset: Int) -> Unit,
) {

    var targetKey: String? = null
        private set

    val isActive: Boolean get() = targetKey != null

    fun enter(key: String): Boolean {
        val movable = LauncherCategoryAppGroupManager.isCategoryKey(key) &&
            key != LauncherCategoryAppGroupManager.KEY_UNKNOWN
        if (movable) {
            targetKey = key
            onModeChanged()
        }
        return movable
    }

    fun exit() {
        if (targetKey == null) return
        targetKey = null
        onModeChanged()
    }

    fun moveUp() = move(-1)

    fun moveDown() = move(1)

    fun bind(bar: View, title: TextView, label: String?) {
        bar.isVisible = isActive && label != null
        if (bar.isVisible) title.text = title.context.getString(R.string.launcher_app_category_reorder_title, label)
    }

    private fun move(offset: Int) {
        val key = targetKey ?: return
        onMove(key.removePrefix(LauncherCategoryAppGroupManager.KEY_PREFIX), offset)
    }
}
