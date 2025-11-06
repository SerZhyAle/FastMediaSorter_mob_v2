package com.sza.fastmediasorter_v2.domain.usecase

import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.repository.ResourceRepository
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
            val id = repository.addResource(resource)
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
            var nextDestinationOrder = existingResources
                .filter { it.isDestination }
                .maxOfOrNull { it.destinationOrder ?: 0 } ?: 0
            
            var skippedDestinations = 0
            val resourcesToAdd = resources.map { resource ->
                if (resource.isDestination && availableDestinationSlots > 0) {
                    nextDestinationOrder++
                    availableDestinationSlots--
                    resource.copy(destinationOrder = nextDestinationOrder)
                } else if (resource.isDestination && availableDestinationSlots <= 0) {
                    skippedDestinations++
                    resource.copy(isDestination = false, destinationOrder = null)
                } else {
                    resource
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
