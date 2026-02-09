package com.sza.fastmediasorter.ui.browse.selection

import com.sza.fastmediasorter.domain.model.MediaFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Manages file selection state for BrowseViewModel.
 * Handles single selection, range selection, select all, and clear operations.
 * 
 * Performance: O(1) for single operations, O(n) for range selection where n = range size.
 */
class BrowseSelectionManager {
    
    private val _selectionState = MutableStateFlow(SelectionState())
    val selectionState: StateFlow<SelectionState> = _selectionState.asStateFlow()
    
    data class SelectionState(
        val selectedFiles: Set<String> = emptySet(),
        val lastSelectedPath: String? = null
    )
    
    /**
     * Toggle selection for a single file.
     * If file is selected, deselect it. If not selected, select it.
     * 
     * @param filePath Path of file to toggle
     */
    fun toggleSelection(filePath: String) {
        val current = _selectionState.value
        val newSelected = current.selectedFiles.toMutableSet()
        
        if (filePath in newSelected) {
            newSelected.remove(filePath)
        } else {
            newSelected.add(filePath)
        }
        
        _selectionState.value = SelectionState(
            selectedFiles = newSelected,
            lastSelectedPath = filePath
        )
    }
    
    /**
     * Select range of files from last selected to current file.
     * Used for Shift+Click behavior.
     * 
     * @param filePath Path of current file
     * @param mediaFiles Complete list of files for index calculation
     */
    fun selectRange(filePath: String, mediaFiles: List<MediaFile>) {
        val current = _selectionState.value
        val lastPath = current.lastSelectedPath
        
        // If no file was selected before, just select this file
        if (lastPath == null || current.selectedFiles.isEmpty()) {
            _selectionState.value = SelectionState(
                selectedFiles = setOf(filePath),
                lastSelectedPath = filePath
            )
            return
        }
        
        // Find indices of last selected and current file
        val currentIndex = mediaFiles.indexOfFirst { it.path == filePath }
        val lastIndex = mediaFiles.indexOfFirst { it.path == lastPath }
        
        if (currentIndex == -1 || lastIndex == -1) {
            Timber.w("selectRange: File not found in list: current=$currentIndex, last=$lastIndex")
            return
        }
        
        // Select all files between last and current (inclusive)
        val startIndex = minOf(currentIndex, lastIndex)
        val endIndex = maxOf(currentIndex, lastIndex)
        
        val newSelected = current.selectedFiles.toMutableSet()
        for (i in startIndex..endIndex) {
            newSelected.add(mediaFiles[i].path)
        }
        
        Timber.d("selectRange: from $lastIndex to $currentIndex, selected ${newSelected.size} files")
        
        _selectionState.value = SelectionState(
            selectedFiles = newSelected,
            lastSelectedPath = filePath
        )
    }
    
    /**
     * Clear all selections.
     */
    fun clearSelection() {
        _selectionState.value = SelectionState(
            selectedFiles = emptySet(),
            lastSelectedPath = null
        )
    }
    
    /**
     * Select all files from the provided list.
     * 
     * @param mediaFiles Complete list of files to select
     */
    fun selectAll(mediaFiles: List<MediaFile>) {
        val allPaths = mediaFiles.map { it.path }.toSet()
        _selectionState.value = SelectionState(
            selectedFiles = allPaths,
            lastSelectedPath = allPaths.lastOrNull()
        )
    }
    
    /**
     * Update selection after files are removed from the list.
     * Removes deleted file paths from selection.
     * 
     * @param removedPaths Paths of files that were removed
     */
    fun onFilesRemoved(removedPaths: List<String>) {
        val current = _selectionState.value
        if (current.selectedFiles.isEmpty()) return
        
        val newSelected = current.selectedFiles - removedPaths.toSet()
        _selectionState.value = current.copy(selectedFiles = newSelected)
    }
    
    /**
     * Update selection after a file is renamed/moved.
     * Updates path in selection if the file was selected.
     * 
     * @param oldPath Original file path
     * @param newPath New file path after rename/move
     */
    fun onFilePathChanged(oldPath: String, newPath: String) {
        val current = _selectionState.value
        if (oldPath !in current.selectedFiles) return
        
        val newSelected = current.selectedFiles - oldPath + newPath
        _selectionState.value = current.copy(selectedFiles = newSelected)
    }
    
    /**
     * Check if a file is currently selected.
     * 
     * @param filePath Path to check
     * @return true if file is selected
     */
    fun isSelected(filePath: String): Boolean {
        return filePath in _selectionState.value.selectedFiles
    }
    
    /**
     * Get count of currently selected files.
     */
    fun getSelectionCount(): Int {
        return _selectionState.value.selectedFiles.size
    }
}
