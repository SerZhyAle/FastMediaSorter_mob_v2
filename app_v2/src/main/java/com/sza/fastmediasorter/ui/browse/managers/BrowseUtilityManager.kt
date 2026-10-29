package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.ui.browse.BrowseState
import com.sza.fastmediasorter.util.VirtualPathUtils
import timber.log.Timber

/**
 * Utility functions for BrowseActivity UI string formatting.
 * Handles resource info generation for the title strip. The active-filter text is built by
 * BrowseStateUiUpdater, which reads the filter dialog's own labels.
 */
class BrowseUtilityManager(
    private val context: Context
) {

    /**
     * Builds resource info string for title display.
     * Format: "{name} ({count} files) • {path} • {sortMode} • {selected}"
     * In subfolder mode: "{name} ({count} files) • {breadcrumb} • {sortMode} • {selected}"
     */
    fun buildResourceInfo(state: BrowseState): String {
        val resource = state.resource ?: return ""
        
        // Don't show loading in title when layoutProgress is visible (center of screen)
        // to avoid duplicate "Loading" text
        if (state.loadingProgress > 0) {
            return resource.name
        }

        Timber.d("S4494: title strip selected=${state.selectedFiles.size} subfolder=${state.isSubfolderMode}")
        val selected = if (state.selectedFiles.isEmpty()) {
            ""
        } else {
            " • " + context.getString(R.string.browse_title_selected_count, state.selectedFiles.size)
        }
        
        // Add file count if available
        val totalFileCount = state.totalFileCount
        val fileCount = when {
            state.loadingProgress > 0 -> " (${state.loadingProgress}...)" // Show progress during scan
            totalFileCount != null -> {
                " (" + context.resources.getQuantityString(
                    R.plurals.file_count_format_plural,
                    totalFileCount,
                    totalFileCount
                ) + ")"
            }
            else -> " (${context.getString(R.string.counting)})"
        }
        
        val sortMode = when (state.sortMode) {
            SortMode.NAME_ASC -> context.getString(R.string.sort_by_name_asc)
            SortMode.NAME_DESC -> context.getString(R.string.sort_by_name_desc)
            SortMode.DATE_ASC -> context.getString(R.string.sort_by_date_asc)
            SortMode.DATE_DESC -> context.getString(R.string.sort_by_date_desc)
            SortMode.SIZE_ASC -> context.getString(R.string.sort_by_size_asc)
            SortMode.SIZE_DESC -> context.getString(R.string.sort_by_size_desc)
            SortMode.TYPE_ASC -> context.getString(R.string.sort_by_type_asc)
            SortMode.TYPE_DESC -> context.getString(R.string.sort_by_type_desc)
            SortMode.ARTIST_ASC -> context.getString(R.string.sort_mode_artist_asc)
            SortMode.ARTIST_DESC -> context.getString(R.string.sort_mode_artist_desc)
            SortMode.TITLE_ASC -> context.getString(R.string.sort_mode_title_asc)
            SortMode.TITLE_DESC -> context.getString(R.string.sort_mode_title_desc)
            SortMode.DURATION_ASC -> context.getString(R.string.sort_mode_duration_asc)
            SortMode.DURATION_DESC -> context.getString(R.string.sort_mode_duration_desc)
            SortMode.DATE_TAKEN_ASC -> context.getString(R.string.sort_mode_date_taken_asc)
            SortMode.DATE_TAKEN_DESC -> context.getString(R.string.sort_mode_date_taken_desc)
            SortMode.MANUAL -> context.getString(R.string.sort_by_manual)
            SortMode.RANDOM -> context.getString(R.string.sort_by_random)
        }
        
        // Show breadcrumb in subfolder mode, otherwise show normalized resource path
        val pathDisplay = state.currentPath?.takeIf { state.isSubfolderMode }?.let {
            buildBreadcrumb(state)
        } ?: buildRootPathDisplay(resource.path, resource.name, state.isCloudResource)

        val modeLabel = if (resource.allFiles) {
            " • " + context.getString(R.string.all_files)
        } else {
            ""
        }

        // S0906: pathDisplay is blank for a cloud resource at root (no meaningful hierarchical
        // path to show) - omit its bullet segment instead of leaving a dangling "• •".
        val pathSegment = pathDisplay.takeIf { it.isNotBlank() }?.let { "$it • " } ?: ""
        return "${resource.name}$fileCount • $pathSegment$sortMode$modeLabel$selected"
    }

    // S0906: a cloud resource's path is an opaque provider id (e.g. "cloud://google_drive/<id>"),
    // not a hierarchical filesystem path - there is no meaningful "parent path" to show, and the
    // segment-matching logic below always misses (the last segment is an id, never the real
    // name), which used to leak the raw id string. Omit the path segment entirely for cloud.
    // S4483: a virtual collection ("virtual://all_audio") is an internal MediaStore scheme with no
    // location the user could open, so it gets the same empty segment.
    private fun buildRootPathDisplay(resourcePath: String, resourceName: String, isCloudResource: Boolean): String {
        Timber.d("S4483: root path display for $resourceName virtual=${VirtualPathUtils.isVirtualPath(resourcePath)}")
        if (isCloudResource || VirtualPathUtils.isVirtualPath(resourcePath)) return ""

        val normalizedPath = resourcePath.trimEnd('/', '\\')
        val lastSegment = normalizedPath.substringAfterLast('/').substringAfterLast('\\')
        val parentPath = normalizedPath.substringBeforeLast('/', "").substringBeforeLast('\\', "")
        return when {
            normalizedPath.isEmpty() -> resourcePath
            !lastSegment.equals(resourceName, ignoreCase = true) -> resourcePath
            parentPath.isNotBlank() -> parentPath
            else -> resourcePath
        }
    }
    
    /**
     * Builds a breadcrumb string for subfolder navigation.
     * Example: "Root > Photos > 2024"
     */
    // S0906: reads the real folder-name stack tracked on BrowseState instead of re-deriving
    // a name from currentPath - a path segment is not a folder name for cloud resources
    // (opaque provider id).
    private fun buildBreadcrumb(state: BrowseState): String {
        val rootLabel = context.getString(R.string.browse_breadcrumb_root)
        val parts = state.folderNameStack + listOfNotNull(state.currentFolderName)
        return "📁 " + listOf(rootLabel).plus(parts).joinToString(" > ")
    }
}
