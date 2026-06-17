package com.sza.fastmediasorter.ui.main.helpers

import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.isAllFilesPredefined
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.ui.main.ResourceTab
import timber.log.Timber

/**
 * Manages resource filtering and sorting logic.
 * Handles tab-based filters, type filters, media type filters, name search, and sort modes.
 * 
 * Responsibilities:
 * - Apply tab filters (ALL, LOCAL, SMB, FTP/SFTP, CLOUD)
 * - Apply resource type filters
 * - Apply media type filters
 * - Apply name search filter
 * - Apply sorting (MANUAL, NAME_ASC/DESC, DATE_ASC/DESC)
 */
class ResourceFilterManager {
    
    /**
     * Apply all active filters and sorting to resource list.
     * 
     * @param resources Raw resource list from database
     * @param activeTab Current active tab (ALL, LOCAL, SMB, etc.)
     * @param filterByType Explicit resource type filter (overrides tab filter)
     * @param filterByMediaType Media type filter (IMAGE, VIDEO, etc.)
     * @param filterByName Name search filter
     * @param sortMode Sort mode (MANUAL, NAME_ASC, etc.)
     * @param enableFavorites Whether favorites are enabled (currently unused)
     * @return Filtered and sorted resource list
     */
    @Suppress("UNUSED_PARAMETER")
    fun applyFiltersAndSorting(
        resources: List<MediaResource>,
        activeTab: ResourceTab,
        filterByType: Set<ResourceType>? = null,
        filterByMediaType: Set<MediaType>? = null,
        filterByName: String? = null,
        sortMode: SortMode = SortMode.MANUAL,
        enableFavorites: Boolean = false
    ): List<MediaResource> {
        var filtered = resources
        
        // Apply tab filter first
        filtered = applyTabFilter(filtered, activeTab)
        
        // Apply explicit type filter (overrides tab filter)
        filterByType?.let { types ->
            filtered = filtered.filter { types.contains(it.type) }
        }
        
        filterByMediaType?.let { mediaTypes ->
            filtered = filtered.filter { resource ->
                resource.supportedMediaTypes.any { mediaTypes.contains(it) }
            }
        }
        
        filterByName?.let { nameFilter ->
            if (nameFilter.isNotBlank()) {
                filtered = filtered.filter { it.name.contains(nameFilter, ignoreCase = true) }
            }
        }
        
        filtered = applySorting(filtered, sortMode)

        return hoistAllFilesFirst(filtered)
    }

    /**
     * Hoist the predefined All-files resource to index 0, keeping the relative order of the rest.
     * Runs after the tab and all filters, so a resource excluded by the active tab/filter is simply
     * absent here and stays hidden. Presentation-only - never persists displayOrder.
     */
    private fun hoistAllFilesFirst(resources: List<MediaResource>): List<MediaResource> {
        val index = resources.indexOfFirst { it.isAllFilesPredefined }
        if (index <= 0) return resources
        Timber.d("S0488: hoisting All-files resource from index $index to top")
        val reordered = resources.toMutableList()
        val pinned = reordered.removeAt(index)
        reordered.add(0, pinned)
        return reordered
    }
    
    /**
     * Apply tab-based resource type filter.
     */
    private fun applyTabFilter(resources: List<MediaResource>, activeTab: ResourceTab): List<MediaResource> {
        return when (activeTab) {
            ResourceTab.ALL -> resources
            ResourceTab.LOCAL -> resources.filter { it.type == ResourceType.LOCAL }
            ResourceTab.SMB -> resources.filter { it.type == ResourceType.SMB }
            ResourceTab.FTP_SFTP -> resources.filter { it.type == ResourceType.FTP || it.type == ResourceType.SFTP }
            ResourceTab.CLOUD -> resources.filter { it.type == ResourceType.CLOUD }
            ResourceTab.FAVORITES -> {
                // Favorites is a navigation action, not a filter.
                // This state should not be persisted or used for filtering.
                emptyList()
            }
        }
    }
    
    /**
     * Apply sort mode to resource list.
     */
    private fun applySorting(resources: List<MediaResource>, sortMode: SortMode): List<MediaResource> {
        return when (sortMode) {
            SortMode.MANUAL -> resources.sortedBy { it.displayOrder }
            SortMode.NAME_ASC -> resources.sortedBy { it.name.lowercase() }
            SortMode.NAME_DESC -> resources.sortedByDescending { it.name.lowercase() }
            SortMode.DATE_ASC -> resources.sortedBy { it.createdDate }
            SortMode.DATE_DESC -> resources.sortedByDescending { it.createdDate }
            else -> resources.sortedBy { it.displayOrder }
        }
    }
    
    /**
     * Convert active tab to effective resource type filter.
     * Used for database-level filtering.
     * 
     * @param activeTab Current active tab
     * @param explicitFilter Explicit filter set by user (takes precedence)
     * @return Set of resource types to filter by, or null for no filter
     */
    fun getEffectiveTypeFilter(
        activeTab: ResourceTab,
        explicitFilter: Set<ResourceType>?
    ): Set<ResourceType>? {
        // Explicit filter takes precedence
        if (explicitFilter != null) {
            return explicitFilter
        }
        
        // Otherwise, derive from active tab
        return when (activeTab) {
            ResourceTab.ALL -> null
            ResourceTab.LOCAL -> setOf(ResourceType.LOCAL)
            ResourceTab.SMB -> setOf(ResourceType.SMB)
            ResourceTab.FTP_SFTP -> setOf(ResourceType.FTP, ResourceType.SFTP)
            ResourceTab.CLOUD -> setOf(ResourceType.CLOUD)
            ResourceTab.FAVORITES -> null // No type filtering for Favorites tab
        }
    }
}
