package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * UseCase for manually triggering sync of network resources (SMB, SFTP, FTP)
 * Updates file counts and last sync timestamp
 */
class SyncNetworkResourcesUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository,
    private val settingsRepository: SettingsRepository,
    private val mediaScannerFactory: MediaScannerFactory
) {
    
    /**
     * Sync all network resources
     * @return Result with count of successfully synced resources
     */
    suspend fun syncAll(): Result<Int> {
        return try {
            val resources = resourceRepository.getAllResourcesSync()
            val networkResources = resources.filter { 
                it.type == ResourceType.SMB || it.type == ResourceType.SFTP || it.type == ResourceType.FTP
            }
            
            if (networkResources.isEmpty()) {
                Timber.d("SyncNetworkResourcesUseCase: No network resources to sync")
                return Result.success(0)
            }
            
            val settings = settingsRepository.getSettings().first()
            val sizeFilter = if (settings.supportImages || settings.supportVideos || settings.supportAudio) {
                SizeFilter(
                    imageSizeMin = settings.imageSizeMin,
                    imageSizeMax = settings.imageSizeMax,
                    videoSizeMin = settings.videoSizeMin,
                    videoSizeMax = settings.videoSizeMax,
                    audioSizeMin = settings.audioSizeMin,
                    audioSizeMax = settings.audioSizeMax
                )
            } else null
            
            var successCount = 0
            
            networkResources.forEach { resource ->
                try {
                    val scanner = mediaScannerFactory.getScanner(resource.type)
                    val fileCount = scanner.getFileCount(
                        resource.path,
                        resource.supportedMediaTypes,
                        sizeFilter,
                        resource.credentialsId,
                        scanSubdirectories = resource.scanSubdirectories
                    )
                    
                    val updatedResource = resource.copy(
                        fileCount = fileCount,
                        lastSyncDate = System.currentTimeMillis()
                    )
                    resourceRepository.updateResource(updatedResource)
                    
                    Timber.i("SyncNetworkResourcesUseCase: Synced ${resource.name}, fileCount=$fileCount")
                    successCount++
                } catch (e: Exception) {
                    Timber.e(e, "SyncNetworkResourcesUseCase: Failed to sync ${resource.name}")
                }
            }
            
            Result.success(successCount)
        } catch (e: Exception) {
            Timber.e(e, "SyncNetworkResourcesUseCase: Failed to sync network resources")
            Result.failure(e)
        }
    }
    
    /**
     * Sync single network resource by ID
     * @param resourceId ID of resource to sync
     * @return Result with success status
     */
    suspend fun syncSingle(resourceId: Long): Result<Unit> {
        return try {
            val resource = resourceRepository.getResourceById(resourceId)
            
            if (resource == null) {
                Timber.w("SyncNetworkResourcesUseCase: Resource $resourceId not found")
                return Result.failure(IllegalArgumentException("Resource not found"))
            }
            
            if (resource.type != ResourceType.SMB && 
                resource.type != ResourceType.SFTP && 
                resource.type != ResourceType.FTP) {
                Timber.w("SyncNetworkResourcesUseCase: Resource ${resource.name} is not a network resource")
                return Result.failure(IllegalArgumentException("Not a network resource"))
            }
            
            val settings = settingsRepository.getSettings().first()
            val sizeFilter = if (settings.supportImages || settings.supportVideos || settings.supportAudio) {
                SizeFilter(
                    imageSizeMin = settings.imageSizeMin,
                    imageSizeMax = settings.imageSizeMax,
                    videoSizeMin = settings.videoSizeMin,
                    videoSizeMax = settings.videoSizeMax,
                    audioSizeMin = settings.audioSizeMin,
                    audioSizeMax = settings.audioSizeMax
                )
            } else null
            
            val scanner = mediaScannerFactory.getScanner(resource.type)
            val fileCount = scanner.getFileCount(
                resource.path,
                resource.supportedMediaTypes,
                sizeFilter,
                resource.credentialsId,
                scanSubdirectories = resource.scanSubdirectories
            )
            
            val updatedResource = resource.copy(
                fileCount = fileCount,
                lastSyncDate = System.currentTimeMillis()
            )
            resourceRepository.updateResource(updatedResource)
            
            Timber.i("SyncNetworkResourcesUseCase: Synced ${resource.name}, fileCount=$fileCount")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "SyncNetworkResourcesUseCase: Failed to sync resource $resourceId")
            Result.failure(e)
        }
    }
}
