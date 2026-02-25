package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.logging.CorrelationContext
import com.sza.fastmediasorter.core.logging.StructuredLogger
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DeleteResourceUseCase @Inject constructor(
    private val repository: ResourceRepository
) {
    suspend operator fun invoke(resourceId: Long): Result<Unit> = withContext(CorrelationContext.asContextElement("delete-resource")) {
        try {
            StructuredLogger.d("START delete resource", "resourceId" to resourceId)
            repository.deleteResource(resourceId)
            StructuredLogger.i("SUCCESS delete resource")
            Result.success(Unit)
        } catch (e: Exception) {
            StructuredLogger.e(e, "FAILURE delete resource")
            Result.failure(e)
        }
    }
}
