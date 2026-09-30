package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.allowsWriteOperations
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.util.VirtualPathUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetDestinationsUseCase @Inject constructor(
    private val repository: ResourceRepository,
    private val settingsRepository: com.sza.fastmediasorter.domain.repository.SettingsRepository
) {
    /**
     * The single predicate every destination query and the add-destination picker share: a
     * resource that fails it is never listed, so offering or counting it creates a slot the UI
     * can neither show nor remove.
     */
    fun canBeDestination(resource: MediaResource): Boolean =
        resource.allowsWriteOperations() && !VirtualPathUtils.isVirtualPath(resource.path)

    operator fun invoke(): Flow<List<MediaResource>> {
        return combine(
            repository.getAllResources(),
            settingsRepository.getSettings()
        ) { resources, settings ->
            val limit = settings.maxRecipients
            resources
                .filter { isActiveDestination(it) && !it.isHidden }
                .sortedBy { it.destinationOrder }
                .take(limit)
        }
    }

    suspend fun getDestinationsExcluding(excludedResourceId: Long): List<MediaResource> {
        val allResources = repository.getAllResourcesSync()
        val settings = settingsRepository.getSettings().first()
        val limit = settings.maxRecipients

        return allResources
            .filter { isActiveDestination(it) && it.id != excludedResourceId && !it.isHidden }
            .sortedBy { it.destinationOrder }
            .take(limit)
    }

    suspend fun getDestinationCount(): Int {
        val resources = repository.getAllResourcesSync()
        return resources.count { isActiveDestination(it) }
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
            .filter { isActiveDestination(it) }
            .mapNotNull { it.destinationOrder }

        if (existingOrders.size >= limit) {
            return -1
        }

        // Appending after the highest order keeps the user's manual ordering intact.
        val maxOrder = existingOrders.maxOrNull() ?: -1
        return maxOrder + 1
    }

    private fun isActiveDestination(resource: MediaResource): Boolean =
        resource.isDestination && (resource.destinationOrder ?: -1) >= 0 && canBeDestination(resource)
}
