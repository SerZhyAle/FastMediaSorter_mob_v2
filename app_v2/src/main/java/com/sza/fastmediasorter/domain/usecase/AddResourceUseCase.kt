package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.util.DestinationColors
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class AddMultipleResult(
    val addedCount: Int,
    val destinationsFull: Boolean,
    val skippedDestinations: Int
)

class AddResourceUseCase @Inject constructor(
    private val repository: ResourceRepository
) {
    companion object {
        const val MAX_DESTINATIONS = 10
    }
    
    suspend operator fun invoke(resource: MediaResource): Result<Long> {
        return try {
            // Set displayOrder to max + 1
            val existingResources = repository.getAllResources().first()
            val maxDisplayOrder = existingResources.maxOfOrNull { it.displayOrder } ?: -1
            val resourceWithOrder = resource.copy(displayOrder = maxDisplayOrder + 1)
            
            val id = repository.addResource(resourceWithOrder)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addMultiple(resources: List<MediaResource>): Result<AddMultipleResult> {
        return try {
            val existingResources = repository.getAllResources().first()
            val currentDestinations = existingResources.count { it.isDestination }
            var availableDestinationSlots = MAX_DESTINATIONS - currentDestinations
            // Use -1 as initial value so first destination gets order 0 after increment
            // Treat null destinationOrder as -1 to ensure first destination gets order 0
            var nextDestinationOrder = existingResources
                .filter { it.isDestination }
                .maxOfOrNull { it.destinationOrder ?: -1 } ?: -1
            
            // Calculate max displayOrder for new resources
            var nextDisplayOrder = existingResources.maxOfOrNull { it.displayOrder } ?: -1
            
            var skippedDestinations = 0
            val resourcesToAdd = resources.map { resource ->
                nextDisplayOrder++
                
                if (resource.isDestination && availableDestinationSlots > 0) {
                    nextDestinationOrder++
                    availableDestinationSlots--
                    val color = DestinationColors.getColorForDestination(nextDestinationOrder)
                    resource.copy(
                        destinationOrder = nextDestinationOrder,
                        destinationColor = color,
                        displayOrder = nextDisplayOrder
                    )
                } else if (resource.isDestination && availableDestinationSlots <= 0) {
                    skippedDestinations++
                    resource.copy(isDestination = false, destinationOrder = null, displayOrder = nextDisplayOrder)
                } else {
                    resource.copy(displayOrder = nextDisplayOrder)
                }
            }
            
            resourcesToAdd.forEach { repository.addResource(it) }
            
            Result.success(
                AddMultipleResult(
                    addedCount = resourcesToAdd.size,
                    destinationsFull = skippedDestinations > 0,
                    skippedDestinations = skippedDestinations
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
