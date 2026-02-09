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
            
            // Atomically swap display orders in single transaction
            resourceRepository.swapResourceDisplayOrders(resource, previousResource)
            
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
            
            // Atomically swap display orders in single transaction
            resourceRepository.swapResourceDisplayOrders(resource, nextResource)
            
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
}
