package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import android.view.View
import android.widget.PopupMenu
import com.sza.fastmediasorter.utils.UserActionLogger

/**
 * Renders the current browse path as a popup list of segments anchored to a bar button.
 *
 * Replaces the breadcrumb ribbon, whose width grew with nesting depth and squeezed the
 * command buttons out of the top bar.
 */
class BrowsePathMenuManager(private val context: Context) {

    /**
     * Item id equals the navigation depth: `0` is the resource root, `n` is `folders[n - 1]`.
     * That mapping is what [BrowseNavigationManager.navigateToDepth] indexes its path/name
     * stacks by, so ids must not be renumbered independently of it.
     *
     * @param folders every level below the root, current folder last.
     */
    fun showPathMenu(
        anchor: View,
        resourceName: String,
        folders: List<String>,
        onDepthSelected: (Int) -> Unit
    ) {
        val popup = PopupMenu(context, anchor)
        var deepest = popup.menu.add(0, ROOT_DEPTH, ROOT_DEPTH, resourceName)
        folders.forEachIndexed { index, folder ->
            val depth = index + 1
            deepest = popup.menu.add(0, depth, depth, folder)
        }
        // The deepest segment is the folder already open - navigating to it is a no-op.
        deepest.isEnabled = false

        popup.setOnMenuItemClickListener { menuItem ->
            UserActionLogger.logButtonClick("PathMenu_depth", "BrowseActivity")
            onDepthSelected(menuItem.itemId)
            true
        }

        popup.show()
    }

    private companion object {
        const val ROOT_DEPTH = 0
    }
}
