package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.logging.CorrelationContext
import com.sza.fastmediasorter.core.logging.StructuredLogger
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import com.sza.fastmediasorter.worker.WorkManagerScheduler
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class DeleteResourceUseCase @Inject constructor(
    private val repository: ResourceRepository,
    private val scheduledOperationRepository: ScheduledOperationRepository,
    private val workManagerScheduler: WorkManagerScheduler
) {
    suspend operator fun invoke(resourceId: Long): Result<Unit> = withContext(CorrelationContext.asContextElement("delete-resource")) {
        try {
            StructuredLogger.d("START delete resource", "resourceId" to resourceId)

            // Cancel WorkManager tasks for any scheduled operations that use this resource
            // before deleting — Room FK CASCADE will remove the DB rows automatically.
            val affectedOps = scheduledOperationRepository.getAllEnabled()
                .filter { it.sourceResourceId == resourceId || it.targetResourceId == resourceId }
            affectedOps.forEach { op ->
                workManagerScheduler.cancelOperation(op.id)
                Timber.d("DeleteResourceUseCase: cancelled scheduled op ${op.id} (resource $resourceId deleted)")
            }

            repository.deleteResource(resourceId)
            StructuredLogger.i("SUCCESS delete resource")
            Result.success(Unit)
        } catch (e: Exception) {
            StructuredLogger.e(e, "FAILURE delete resource")
            Result.failure(e)
        }
    }
}
