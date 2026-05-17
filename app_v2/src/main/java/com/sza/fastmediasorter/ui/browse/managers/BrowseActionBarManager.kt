package com.sza.fastmediasorter.ui.browse.managers

import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.SearchView

/**
 * Manages ActionBar/Toolbar state and menu interactions in BrowseActivity.
 * Handles search, filter/sort icons, action mode menu, and title updates.
 */
class BrowseActionBarManager(
    private val callbacks: ActionBarCallbacks
) {

    interface ActionBarCallbacks {
        fun onSearchQueryChanged(query: String)
        fun onSearchClosed()
        fun onFilterClicked()
        fun onSortClicked()
        fun onRefreshClicked()
        fun onSelectAllClicked()
        fun onCopyClicked()
        fun onMoveClicked()
        fun onDeleteClicked()
        fun onShareClicked()
    }

    fun initialize() {
        // Initialization logic will be added in Phase 4
    }

    fun cleanup() {
        // Release resources
    }

    // S0028: tear-off to a separate window was VR-only. S0241 removed the VR stack, so this
    // action is permanently unreachable on every flavor. Kept as a no-op stub to preserve
    // the public surface until call sites are cleaned up centrally.
    fun handleSeparateWindowAction(
        allowSeparateWindow: Boolean,
        browseEventHandler: BrowseEventHandler,
        resourceId: Long,
        currentFilePath: String?,
        scrollPosition: Int
    ) {
        // No-op: VR multi-window tear-off was removed with the OpenXR stack (S0241).
    }
}
