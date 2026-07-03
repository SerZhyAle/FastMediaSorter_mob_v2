package com.sza.fastmediasorter.ui.browse

import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.FileFilter
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.UndoOperation

data class BrowseState(
    val resource: MediaResource? = null,
    val mediaFiles: List<MediaFile> = emptyList(),
    val usePagination: Boolean = false, // True if file count >= PAGINATION_THRESHOLD
    val totalFileCount: Int? = null, // Total count (null if not yet calculated)
    val selectedFiles: Set<String> = emptySet(),
    val lastSelectedPath: String? = null,
    val sortMode: SortMode = SortMode.NAME_ASC,
    val displayMode: DisplayMode = DisplayMode.LIST,
    val filter: FileFilter? = null,
    val lastOperation: UndoOperation? = null,
    val undoOperationTimestamp: Long? = null, // Timestamp when undo operation was saved (for expiry check)
    val loadingProgress: Int = 0, // Number of files found during scan (0 = not scanning)
    val isSorting: Boolean = false, // True while in-memory sort is running (shows "Sorting..")
    val isCloudResource: Boolean = false, // True for cloud resources (to show animated dots)
    val isScanCancellable: Boolean = false, // True when STOP should be visible for the active scan
    val useCompactElements: Boolean = false, // True if global compact mode is enabled
    val extractionState: ExtractionState = ExtractionState(),
    // Subfolder navigation state
    val currentPath: String? = null, // Current browsed path (null = root of resource)
    val pathStack: List<String> = emptyList(), // Stack of visited paths for back navigation
    val isSubfolderMode: Boolean = false, // True when subfolder navigation is enabled
    // S0906: real display name at each depth, parallel to currentPath/pathStack - a path
    // segment is not a folder name for cloud resources (opaque provider id), so the name
    // must be tracked alongside the path instead of re-derived from it.
    val currentFolderName: String? = null,
    val folderNameStack: List<String> = emptyList()
)

data class ExtractionState(
    val isExtracting: Boolean = false,
    val currentEntry: String = "",
    val progressPercent: Int = 0,
    val doneEntries: Int = 0,
    val totalEntries: Int = 0,
    val targetPath: String = ""
)
