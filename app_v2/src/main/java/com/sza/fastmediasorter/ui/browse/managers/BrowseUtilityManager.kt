package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.FileFilter
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.ui.browse.BrowseState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility functions for BrowseActivity UI string formatting.
 * Handles resource info and filter description generation.
 */
class BrowseUtilityManager(
    private val context: Context
) {
    
    private val dateFormatter = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    
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
        
        val selected = if (state.selectedFiles.isEmpty()) {
            ""
        } else {
            " • ${state.selectedFiles.size} selected"
        }
        
        // Add file count if available
        val fileCount = when {
            state.loadingProgress > 0 -> " (${state.loadingProgress}...)" // Show progress during scan
            state.totalFileCount != null -> " (${state.totalFileCount} files)"
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
        val pathDisplay = state.currentPath?.takeIf { state.isSubfolderMode }?.let { currentPath ->
            // Build breadcrumb: "Root > Folder1 > Folder2"
            buildBreadcrumb(resource.path, currentPath)
        } ?: buildRootPathDisplay(resource.path, resource.name)
        
        val modeLabel = if (resource.allFiles) {
            " • " + context.getString(R.string.all_files)
        } else {
            ""
        }
        
        return "${resource.name}$fileCount • $pathDisplay • $sortMode$modeLabel$selected"
    }

    private fun buildRootPathDisplay(resourcePath: String, resourceName: String): String {
        val normalizedPath = resourcePath.trimEnd('/', '\\')
        if (normalizedPath.isEmpty()) {
            return resourcePath
        }

        val lastSegment = normalizedPath.substringAfterLast('/').substringAfterLast('\\')
        if (!lastSegment.equals(resourceName, ignoreCase = true)) {
            return resourcePath
        }

        val parentPath = normalizedPath.substringBeforeLast('/', "").substringBeforeLast('\\', "")
        return if (parentPath.isNotBlank()) parentPath else resourcePath
    }
    
    /**
     * Builds a breadcrumb string for subfolder navigation.
     * Example: "Root > Photos > 2024"
     */
    private fun buildBreadcrumb(rootPath: String, currentPath: String): String {
        if (!currentPath.startsWith(rootPath)) {
            return currentPath
        }
        
        val relativePath = currentPath.removePrefix(rootPath).trimStart('/', '\\')
        if (relativePath.isEmpty()) {
            return "📁 Root"
        }
        
        val parts = relativePath.split("/", "\\").filter { it.isNotEmpty() }
        return "📁 " + listOf("Root").plus(parts).joinToString(" > ")
    }
    
    /**
     * Builds human-readable filter description.
     * Format: "⚠ Filter active: {types}, {name}, {dates}, {sizes}"
     */
    fun buildFilterDescription(filter: FileFilter): String {
        val parts = mutableListOf<String>()
        
        // Media types filter
        filter.mediaTypes?.let { types ->
            if (types.isNotEmpty()) {
                val typeNames = types.map { it.name.lowercase().replaceFirstChar { char -> char.uppercase() } }.sorted()
                parts.add(typeNames.joinToString(", "))
            }
        }
        
        // Name filter
        filter.nameContains?.let {
            parts.add("name contains '$it'")
        }
        
        // Date filters
        if (filter.minDate != null && filter.maxDate != null) {
            parts.add("created ${formatDate(Date(filter.minDate))} - ${formatDate(Date(filter.maxDate))}")
        } else if (filter.minDate != null) {
            parts.add("created after ${formatDate(Date(filter.minDate))}")
        } else if (filter.maxDate != null) {
            parts.add("created before ${formatDate(Date(filter.maxDate))}")
        }
        
        // Size filters
        if (filter.minSizeMb != null && filter.maxSizeMb != null) {
            parts.add("size ${filter.minSizeMb} - ${filter.maxSizeMb} MB")
        } else if (filter.minSizeMb != null) {
            parts.add("size >= ${filter.minSizeMb} MB")
        } else if (filter.maxSizeMb != null) {
            parts.add("size <= ${filter.maxSizeMb} MB")
        }
        
        return "⚠ Filter active: " + parts.joinToString(", ")
    }
    
    /**
     * Formats date for filter descriptions.
     */
    private fun formatDate(date: Date): String {
        return dateFormatter.format(date)
    }
}
