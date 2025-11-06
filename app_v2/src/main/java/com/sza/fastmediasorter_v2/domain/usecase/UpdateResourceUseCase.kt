package com.sza.fastmediasorter_v2.domain.usecase

import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.repository.ResourceRepository
import javax.inject.Inject

class UpdateResourceUseCase @Inject constructor(
    private val repository: ResourceRepository
) {
    suspend operator fun invoke(resource: MediaResource): Result<Unit> {
        return try {
            repository.updateResource(resource)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
