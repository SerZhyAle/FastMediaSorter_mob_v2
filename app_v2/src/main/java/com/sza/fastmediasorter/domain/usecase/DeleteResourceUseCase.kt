package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.repository.ResourceRepository
import javax.inject.Inject

class DeleteResourceUseCase @Inject constructor(
    private val repository: ResourceRepository
) {
    suspend operator fun invoke(resourceId: Long): Result<Unit> {
        return try {
            repository.deleteResource(resourceId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
