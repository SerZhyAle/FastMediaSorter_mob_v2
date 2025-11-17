package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.model.MediaResource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetDestinationsUseCase @Inject constructor(
    private val repository: ResourceRepository
) {
    operator fun invoke(): Flow<List<MediaResource>> {
        return repository.getAllResources().map { resources ->
            resources
                .filter { it.isDestination && (it.destinationOrder ?: -1) >= 0 }
                .sortedBy { it.destinationOrder }
                .take(10)
        }
    }
    
    suspend fun getDestinationsExcluding(excludedResourceId: Long): List<MediaResource> {
        val allResources = repository.getAllResourcesSync()
        return allResources
            .filter { it.isDestination && (it.destinationOrder ?: -1) >= 0 && it.id != excludedResourceId }
            .sortedBy { it.destinationOrder }
            .take(10)
    }
    
    suspend fun getDestinationCount(): Int {
        val resources = repository.getAllResourcesSync()
        return resources.count { it.isDestination && (it.destinationOrder ?: -1) >= 0 }
    }
    
    suspend fun isDestinationsFull(): Boolean {
        return getDestinationCount() >= 10
    }
    
    suspend fun getNextAvailableOrder(): Int {
        val resources = repository.getAllResourcesSync()
        val usedOrders = resources
            .filter { it.isDestination && (it.destinationOrder ?: -1) >= 0 }
            .mapNotNull { it.destinationOrder }
            .toSet()
        
        for (i in 0 until 10) {
            if (i !in usedOrders) {
                return i
            }
        }
        return -1
    }
}
