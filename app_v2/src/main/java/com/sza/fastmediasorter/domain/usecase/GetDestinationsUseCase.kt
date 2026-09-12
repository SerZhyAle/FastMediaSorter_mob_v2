package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.allowsWriteOperations
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.util.VirtualPathUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import timber.log.Timber
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
            Timber.d("S2646: destinations filtered from %d resources", resources.size)
            resources
                .filter {
                    it.isDestination && (it.destinationOrder ?: -1) >= 0 &&
                        it.allowsWriteOperations() && !it.isHidden &&
                        !VirtualPathUtils.isVirtualPath(it.path)
                }
                .sortedBy { it.destinationOrder }
                .take(limit)
        }
    }

    suspend fun getDestinationsExcluding(excludedResourceId: Long): List<MediaResource> {
        val allResources = repository.getAllResourcesSync()
        val settings = settingsRepository.getSettings().first()
        val limit = settings.maxRecipients

        return allResources
            .filter {
                it.isDestination && (it.destinationOrder ?: -1) >= 0 &&
                    it.id != excludedResourceId && it.allowsWriteOperations() &&
                    !it.isHidden && !VirtualPathUtils.isVirtualPath(it.path)
            }
            .sortedBy { it.destinationOrder }
            .take(limit)
    }

    suspend fun getDestinationCount(): Int {
        val resources = repository.getAllResourcesSync()
        return resources.count {
            it.isDestination && (it.destinationOrder ?: -1) >= 0 &&
                it.allowsWriteOperations() && !VirtualPathUtils.isVirtualPath(it.path)
        }
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
            .filter { it.isDestination && (it.destinationOrder ?: -1) >= 0 && it.allowsWriteOperations() }
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
