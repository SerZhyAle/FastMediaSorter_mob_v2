package com.sza.fastmediasorter.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.CleanupTrashFoldersUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.File

/**
 * Background worker for cleaning up old .trash folders
 * Runs periodically to delete trash folders older than 5 minutes
 */
@HiltWorker
class TrashCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val cleanupTrashFoldersUseCase: CleanupTrashFoldersUseCase,
    private val resourceRepository: ResourceRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "trash_cleanup_worker"
    }

    override suspend fun doWork(): Result {
        Timber.d("TrashCleanupWorker: Starting trash cleanup")
        
        return try {
            // Get all LOCAL resources to scan their directories
            val resources = resourceRepository.getAllResourcesSync()
            val localResources = resources.filter { it.type == ResourceType.LOCAL }
            
            var totalDeleted = 0
            
            // Clean up trash folders in each local resource directory
            for (resource in localResources) {
                try {
                    val directory = File(resource.path)
                    if (directory.exists() && directory.isDirectory) {
                        val deleted = cleanupTrashFoldersUseCase.cleanup(directory)
                        totalDeleted += deleted
                        Timber.d("Cleaned up $deleted trash folders in ${resource.name}")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error cleaning up trash in resource: ${resource.name}")
                }
            }
            
            Timber.i("TrashCleanupWorker: Completed successfully, deleted $totalDeleted trash folders")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "TrashCleanupWorker: Failed")
            Result.failure()
        }
    }
}
