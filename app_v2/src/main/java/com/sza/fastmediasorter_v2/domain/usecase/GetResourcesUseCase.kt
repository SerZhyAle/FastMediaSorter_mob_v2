package com.sza.fastmediasorter_v2.domain.usecase

import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.model.ResourceType
import com.sza.fastmediasorter_v2.domain.repository.ResourceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetResourcesUseCase @Inject constructor(
    private val repository: ResourceRepository
) {
    operator fun invoke(): Flow<List<MediaResource>> {
        return repository.getAllResources()
    }

    fun getByType(type: ResourceType): Flow<List<MediaResource>> {
        return repository.getResourcesByType(type)
    }

    suspend fun getById(id: Long): MediaResource? {
        return repository.getResourceById(id)
    }
}
