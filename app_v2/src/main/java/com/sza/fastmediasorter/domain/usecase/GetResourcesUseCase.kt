package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

class GetResourcesUseCase @Inject constructor(
    private val repository: ResourceRepository
) {
    operator fun invoke(): Flow<List<MediaResource>> {
        // S1009: hidden ad-hoc resources (scheduled-op local folders) never render on visible surfaces.
        return repository.getAllResources().map { list -> list.filterNot { it.isHidden } }
    }

    fun getByType(type: ResourceType): Flow<List<MediaResource>> {
        return repository.getResourcesByType(type)
    }

    suspend fun getById(id: Long): MediaResource? {
        val resource = repository.getResourceById(id)
        Timber.d("🔶 SORT_DEBUG GetResourcesUseCase.getById($id): sortMode=${resource?.sortMode}, name=${resource?.name}")
        return resource
    }
    
    /**
     * Get resources with filtering and sorting applied at database level
     * More efficient than client-side filtering for large datasets
     */
    suspend fun getFiltered(
        filterByType: Set<ResourceType>? = null,
        filterByMediaType: Set<MediaType>? = null,
        filterByName: String? = null,
        sortMode: SortMode = SortMode.MANUAL
    ): List<MediaResource> {
        return repository.getFilteredResources(
            filterByType = filterByType,
            filterByMediaType = filterByMediaType,
            filterByName = filterByName,
            sortMode = sortMode
        )
    }
}
