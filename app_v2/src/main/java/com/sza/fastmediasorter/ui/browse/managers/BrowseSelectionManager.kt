package com.sza.fastmediasorter.ui.browse.managers

import android.view.ActionMode
import com.sza.fastmediasorter.domain.model.MediaFile

/**
 * Manages file selection state and multi-selection mode in BrowseActivity.
 * Handles single/long clicks, selection persistence, and ActionMode lifecycle.
 */
class BrowseSelectionManager(
    private val callbacks: SelectionCallbacks
) {
    
    interface SelectionCallbacks {
        fun onSelectionChanged(selectedCount: Int)
        fun onFileClicked(file: MediaFile, position: Int)
        fun onFileLongClicked(file: MediaFile, position: Int): Boolean
        fun onActionModeStarted(mode: ActionMode)
        fun onActionModeFinished()
    }
    
    private val selectedFiles = mutableSetOf<String>()
    private var actionMode: ActionMode? = null
    private var isInSelectionMode = false
    
    fun initialize() {
        // Initialization logic will be added in Phase 1
    }
    
    fun cleanup() {
        selectedFiles.clear()
        actionMode?.finish()
        actionMode = null
        isInSelectionMode = false
    }
}
