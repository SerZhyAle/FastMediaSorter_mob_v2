package com.sza.fastmediasorter_v2.domain.usecase

import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.repository.ResourceRepository
import javax.inject.Inject

class AddResourceUseCase @Inject constructor(
    private val repository: ResourceRepository
) {
    suspend operator fun invoke(resource: MediaResource): Result<Long> {
        return try {
            val id = repository.addResource(resource)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addMultiple(resources: List<MediaResource>): Result<List<Long>> {
        return try {
            val ids = resources.map { repository.addResource(it) }
            Result.success(ids)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
