package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.logging.CorrelationContext
import com.sza.fastmediasorter.core.logging.StructuredLogger
import com.sza.fastmediasorter.data.repository.wear.WearResourceTombstoneStore
import com.sza.fastmediasorter.domain.model.WearSourceTombstonePayload
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import com.sza.fastmediasorter.worker.WorkManagerScheduler
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class DeleteResourceUseCase @Inject constructor(
    private val repository: ResourceRepository,
    private val scheduledOperationRepository: ScheduledOperationRepository,
    private val workManagerScheduler: WorkManagerScheduler,
    // S2507: the record that this resource was deleted here, which must outlive the row itself.
    private val wearResourceTombstoneStore: WearResourceTombstoneStore
) {
    suspend operator fun invoke(resourceId: Long): Result<Unit> = withContext(CorrelationContext.asContextElement("delete-resource")) {
        try {
            StructuredLogger.d("START delete resource", "resourceId" to resourceId)

            // Cancel WorkManager tasks for any scheduled operations that use this resource
            // before deleting - Room FK CASCADE will remove the DB rows automatically.
            val affectedOps = scheduledOperationRepository.getAllEnabled()
                .filter { it.sourceResourceId == resourceId || it.targetResourceId == resourceId }
            affectedOps.forEach { op ->
                workManagerScheduler.cancelOperation(op.id)
                Timber.d("DeleteResourceUseCase: cancelled scheduled op ${op.id} (resource $resourceId deleted)")
            }

            // S2507: recorded before the row goes, because deleteResource also drops this resource's
            // S2502 edit stamp. A tombstone written afterwards would be lost to any failure between
            // the two, and the next exchange would hand the resource back from the watch.
            Timber.d("S2507: phone user-delete, recording a tombstone for resource $resourceId")
            wearResourceTombstoneStore.record(
                WearSourceTombstonePayload(
                    id = resourceId.toString(),
                    deletedAt = System.currentTimeMillis()
                )
            )
            repository.deleteResource(resourceId)
            StructuredLogger.i("SUCCESS delete resource")
            Result.success(Unit)
        } catch (e: Exception) {
            StructuredLogger.e(e, "FAILURE delete resource")
            Result.failure(e)
        }
    }
}
