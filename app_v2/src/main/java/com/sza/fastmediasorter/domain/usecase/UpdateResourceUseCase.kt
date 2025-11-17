package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.ResourceRepository
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
