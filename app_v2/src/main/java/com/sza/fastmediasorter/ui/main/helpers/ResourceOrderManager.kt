package com.sza.fastmediasorter.ui.main.helpers

import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import timber.log.Timber

/**
 * Manages manual reordering of resources (up/down movement).
 * Handles display order manipulation and sort mode switching.
 * 
 * Responsibilities:
 * - Move resource up in list
 * - Move resource down in list
 * - Atomically swap display orders
 * - Auto-switch to MANUAL sort mode when reordering
 */
class ResourceOrderManager(
    private val resourceRepository: ResourceRepository
) {
    
    /**
     * Result of order manipulation operation.
     */
    sealed class OrderResult {
        /** Order changed successfully */
        object Success : OrderResult()
        
        /** Cannot move (already at top/bottom) */
        object CannotMove : OrderResult()
        
        /** Error during operation */
        data class Error(val exception: Exception) : OrderResult()
    }
    
    /**
     * Move resource up in the list (decrease display order).
     * 
     * @param resource Resource to move
     * @param currentList Current ordered list of resources
     * @return OrderResult indicating success or reason for failure
     */
    suspend fun moveResourceUp(
        resource: MediaResource,
        currentList: List<MediaResource>
    ): OrderResult {
        val currentIndex = currentList.indexOfFirst { it.id == resource.id }
        
        if (currentIndex <= 0) {
            // Already at top or not found
            return OrderResult.CannotMove
        }
        
        return try {
            val previousResource = currentList[currentIndex - 1]
            
            // Use position indices as authoritative display orders.
            // This handles duplicate displayOrder values (e.g. all = 0 after direct import)
            // by always deriving the swap targets from the current list position,
            // not from the potentially stale or non-unique domain model field.
            resourceRepository.swapResourceDisplayOrders(
                resource.copy(displayOrder = currentIndex),
                previousResource.copy(displayOrder = currentIndex - 1)
            )
            
            Timber.d("Moved resource up: ${resource.name}")
            OrderResult.Success
        } catch (e: Exception) {
            Timber.e(e, "Failed to move resource up: ${resource.name}")
            OrderResult.Error(e)
        }
    }
    
    /**
     * Move resource down in the list (increase display order).
     * 
     * @param resource Resource to move
     * @param currentList Current ordered list of resources
     * @return OrderResult indicating success or reason for failure
     */
    suspend fun moveResourceDown(
        resource: MediaResource,
        currentList: List<MediaResource>
    ): OrderResult {
        val currentIndex = currentList.indexOfFirst { it.id == resource.id }
        
        if (currentIndex < 0 || currentIndex >= currentList.size - 1) {
            // Already at bottom or not found
            return OrderResult.CannotMove
        }
        
        return try {
            val nextResource = currentList[currentIndex + 1]
            
            // Use position indices as authoritative display orders.
            // This handles duplicate displayOrder values (e.g. all = 0 after direct import)
            // by always deriving the swap targets from the current list position,
            // not from the potentially stale or non-unique domain model field.
            resourceRepository.swapResourceDisplayOrders(
                resource.copy(displayOrder = currentIndex),
                nextResource.copy(displayOrder = currentIndex + 1)
            )
            
            Timber.d("Moved resource down: ${resource.name}")
            OrderResult.Success
        } catch (e: Exception) {
            Timber.e(e, "Failed to move resource down: ${resource.name}")
            OrderResult.Error(e)
        }
    }
    
    /**
     * Get recommended sort mode after manual reordering.
     * Always returns MANUAL to preserve user's custom order.
     */
    fun getRecommendedSortMode(): SortMode {
        return SortMode.MANUAL
    }

    /**
     * Persist the full reordered list after a drag-to-reorder gesture.
     * Assigns displayOrder = list index for each item, then saves via repository.
     *
     * @param newOrder List in the new display order (index 0 = top).
     * @return OrderResult indicating success or error.
     */
    suspend fun saveResourceOrder(newOrder: List<MediaResource>): OrderResult {
        return try {
            resourceRepository.updateResourcesDisplayOrder(newOrder)
            Timber.d("ResourceOrderManager: saved drag order for ${newOrder.size} resources")
            OrderResult.Success
        } catch (e: Exception) {
            Timber.e(e, "ResourceOrderManager: failed to save drag order")
            OrderResult.Error(e)
        }
    }
}
