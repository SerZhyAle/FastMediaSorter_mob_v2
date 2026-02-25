package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.logging.CorrelationContext
import com.sza.fastmediasorter.core.logging.StructuredLogger
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject

class UpdateResourceUseCase @Inject constructor(
    private val repository: ResourceRepository
) {
    suspend operator fun invoke(resource: MediaResource): Result<Unit> = withContext(CorrelationContext.asContextElement("update-resource")) {
        try {
            StructuredLogger.d("START update resource", 
                "name" to resource.name, 
                "type" to resource.type.name,
                "supportedTypesCount" to resource.supportedMediaTypes.size
            )
            repository.updateResource(resource)
            StructuredLogger.i("SUCCESS update resource")
            Result.success(Unit)
        } catch (e: Exception) {
            StructuredLogger.e(e, "FAILURE update resource")
            Result.failure(e)
        }
    }
}
