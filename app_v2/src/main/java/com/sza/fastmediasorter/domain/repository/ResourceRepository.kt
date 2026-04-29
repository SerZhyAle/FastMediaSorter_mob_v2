package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Resource operations
 * Implementation will be in data layer
 */
interface ResourceRepository {
    
    fun getAllResources(): Flow<List<MediaResource>>
    
    suspend fun getAllResourcesSync(): List<MediaResource>
    
    suspend fun getResourceById(id: Long): MediaResource?
    
    fun getResourcesByType(type: ResourceType): Flow<List<MediaResource>>
    
    fun getDestinations(): Flow<List<MediaResource>>
    
    /**
     * Get resources with filtering and sorting applied at DB level
     * @param filterByType Filter by resource type (null = no filter)
     * @param filterByMediaType Filter by supported media types (null = no filter)
     * @param filterByName Filter by name/path substring (null = no filter)
     * @param sortMode Sort mode to apply
     */
    suspend fun getFilteredResources(
        filterByType: Set<ResourceType>?,
        filterByMediaType: Set<MediaType>?,
        filterByName: String?,
        sortMode: SortMode
    ): List<MediaResource>
    
    suspend fun addResource(resource: MediaResource): Long
    
    suspend fun updateResource(resource: MediaResource)
    
    /**
     * Atomically swap display orders of two resources.
     * Used for manual reordering (moveUp/moveDown).
     */
    suspend fun swapResourceDisplayOrders(resource1: MediaResource, resource2: MediaResource)

    /**
     * Persist new display order for an entire reordered list.
     * Assigns displayOrder = list index for each item. Used by drag-to-reorder.
     */
    suspend fun updateResourcesDisplayOrder(resources: List<MediaResource>)
    
    suspend fun deleteResource(resourceId: Long)

    suspend fun deleteAllResources()

    suspend fun testConnection(resource: MediaResource): Result<String>

    suspend fun updateIcon(resourceId: Long, iconId: String?)

    /**
     * Assigns icons to all resources that currently have [iconId] == null.
     * Called once on startup after DB migration to 26; safe to call multiple times.
     * Returns the number of resources that were updated.
     */
    suspend fun backfillMissingIcons(resolveIcon: (path: String, profileName: String, typeName: String) -> String?): Int
}
