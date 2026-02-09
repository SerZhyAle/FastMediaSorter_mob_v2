package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.model.MediaResource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetDestinationsUseCase @Inject constructor(
    private val repository: ResourceRepository,
    private val settingsRepository: com.sza.fastmediasorter.domain.repository.SettingsRepository
) {
    operator fun invoke(): Flow<List<MediaResource>> {
        return combine(
            repository.getAllResources(),
            settingsRepository.getSettings()
        ) { resources, settings ->
            val limit = settings.maxRecipients
            resources
                .filter { it.isDestination && (it.destinationOrder ?: -1) >= 0 && !it.isReadOnly }
                .sortedBy { it.destinationOrder }
                .take(limit)
        }
    }
    
    suspend fun getDestinationsExcluding(excludedResourceId: Long): List<MediaResource> {
        val allResources = repository.getAllResourcesSync()
        val settings = settingsRepository.getSettings().first()
        val limit = settings.maxRecipients
        
        return allResources
            .filter { it.isDestination && (it.destinationOrder ?: -1) >= 0 && it.id != excludedResourceId && !it.isReadOnly }
            .sortedBy { it.destinationOrder }
            .take(limit)
    }
    
    suspend fun getDestinationCount(): Int {
        val resources = repository.getAllResourcesSync()
        return resources.count { it.isDestination && (it.destinationOrder ?: -1) >= 0 && !it.isReadOnly }
    }
    
    suspend fun isDestinationsFull(): Boolean {
        val settings = settingsRepository.getSettings().first()
        val limit = settings.maxRecipients
        return getDestinationCount() >= limit
    }
    
    suspend fun getNextAvailableOrder(): Int {
        val resources = repository.getAllResourcesSync()
        val settings = settingsRepository.getSettings().first()
        val limit = settings.maxRecipients
        
        val existingOrders = resources
            .filter { it.isDestination && (it.destinationOrder ?: -1) >= 0 && !it.isReadOnly }
            .mapNotNull { it.destinationOrder }
        
        // Check if limit reached
        if (existingOrders.size >= limit) {
            return -1
        }
        
        // Return max order + 1 to add new destination at the end
        val maxOrder = existingOrders.maxOrNull() ?: -1
        return maxOrder + 1
    }
}
