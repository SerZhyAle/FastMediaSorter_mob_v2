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

    /**
     * S1001: targeted single-column write. Browse persists the last viewed file on every
     * file open from an in-memory resource copy that may be stale; a full-entity update
     * there clobbers freshly written statistics (fileCount/lastBrowseDate/lastSyncDate).
     */
    suspend fun saveLastViewedFile(resourceId: Long, path: String?): Result<Unit> = try {
        repository.updateLastViewedFile(resourceId, path)
        Result.success(Unit)
    } catch (e: Exception) {
        StructuredLogger.e(e, "FAILURE update lastViewedFile")
        Result.failure(e)
    }

    /** S1001: targeted single-column write - see [saveLastViewedFile]. Fires on every onPause. */
    suspend fun saveScrollPosition(resourceId: Long, position: Int): Result<Unit> = try {
        repository.updateLastScrollPosition(resourceId, position)
        Result.success(Unit)
    } catch (e: Exception) {
        StructuredLogger.e(e, "FAILURE update lastScrollPosition")
        Result.failure(e)
    }
}
