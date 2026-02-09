package com.sza.fastmediasorter.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.MediaScannerFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Background worker to periodically verify network file existence
 * and update file counts for network resources (SMB, SFTP, FTP)
 */
@HiltWorker
class NetworkFilesSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val resourceRepository: ResourceRepository,
    private val settingsRepository: SettingsRepository,
    private val mediaScannerFactory: MediaScannerFactory
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("NetworkFilesSyncWorker: Starting background sync")
        
        return try {
            val resources = resourceRepository.getAllResources().first()
            val settings = settingsRepository.getSettings().first()
            
            // Build size filter from settings (use null if feature disabled)
            val sizeFilter = if (settings.supportImages || settings.supportVideos || settings.supportAudio) {
                com.sza.fastmediasorter.domain.usecase.SizeFilter(
                    imageSizeMin = settings.imageSizeMin,
                    imageSizeMax = settings.imageSizeMax,
                    videoSizeMin = settings.videoSizeMin,
                    videoSizeMax = settings.videoSizeMax,
                    audioSizeMin = settings.audioSizeMin,
                    audioSizeMax = settings.audioSizeMax
                )
            } else null
            
            // Filter only network resources
            val networkResources = resources.filter { 
                it.type == ResourceType.SMB || it.type == ResourceType.SFTP || it.type == ResourceType.FTP
            }
            
            if (networkResources.isEmpty()) {
                Timber.d("NetworkFilesSyncWorker: No network resources to sync")
                return Result.success()
            }
            
            Timber.d("NetworkFilesSyncWorker: Syncing ${networkResources.size} network resources")
            
            networkResources.forEach { resource ->
                try {
                    val scanner = mediaScannerFactory.getScanner(resource.type)
                    
                    // Get current file count
                    val fileCount = scanner.getFileCount(
                        resource.path,
                        resource.supportedMediaTypes,
                        sizeFilter,
                        resource.credentialsId,
                        scanSubdirectories = resource.scanSubdirectories
                    )
                    
                    // Update resource if count changed
                    if (fileCount != resource.fileCount) {
                        Timber.i("NetworkFilesSyncWorker: ${resource.name} file count changed: ${resource.fileCount} → $fileCount")
                        val updatedResource = resource.copy(
                            fileCount = fileCount,
                            lastSyncDate = System.currentTimeMillis()
                        )
                        resourceRepository.updateResource(updatedResource)
                    } else {
                        Timber.d("NetworkFilesSyncWorker: ${resource.name} file count unchanged: $fileCount")
                        // Update lastSyncDate even if count unchanged
                        val updatedResource = resource.copy(lastSyncDate = System.currentTimeMillis())
                        resourceRepository.updateResource(updatedResource)
                    }
                    
                } catch (e: Exception) {
                    Timber.e(e, "NetworkFilesSyncWorker: Failed to sync ${resource.name}")
                    // Continue with other resources even if one fails
                }
            }
            
            Timber.i("NetworkFilesSyncWorker: Background sync completed successfully")
            Result.success()
            
        } catch (e: Exception) {
            Timber.e(e, "NetworkFilesSyncWorker: Background sync failed")
            Result.failure()
        }
    }
    
    companion object {
        const val WORK_NAME = "network_files_sync"
    }
}
