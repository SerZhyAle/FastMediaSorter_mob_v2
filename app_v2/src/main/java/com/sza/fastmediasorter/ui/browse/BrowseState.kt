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
    val isCloudResource: Boolean = false, // True for cloud resources (to show animated dots)
    val isScanCancellable: Boolean = false, // True when scan runs >5 seconds, shows STOP button
    val showSmallControls: Boolean = false, // True if "Small controls" setting is enabled
    val useCompactElements: Boolean = false, // True if global compact mode is enabled
    val extractionState: ExtractionState = ExtractionState(),
    // Subfolder navigation state
    val currentPath: String? = null, // Current browsed path (null = root of resource)
    val pathStack: List<String> = emptyList(), // Stack of visited paths for back navigation
    val isSubfolderMode: Boolean = false // True when subfolder navigation is enabled
)

data class ExtractionState(
    val isExtracting: Boolean = false,
    val currentEntry: String = "",
    val progressPercent: Int = 0,
    val doneEntries: Int = 0,
    val totalEntries: Int = 0,
    val targetPath: String = ""
)
