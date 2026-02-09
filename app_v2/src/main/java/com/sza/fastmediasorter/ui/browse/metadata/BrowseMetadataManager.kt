package com.sza.fastmediasorter.ui.browse.metadata

import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.usecase.UpdateResourceUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Manages resource metadata updates after browsing.
 * Handles fileCount and lastBrowseDate synchronization with the database.
 */
class BrowseMetadataManager(
    private val updateResourceUseCase: UpdateResourceUseCase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    /**
     * Updates resource metadata (fileCount and lastBrowseDate) after successful file loading.
     * For network resources (SMB/SFTP/FTP), also updates lastSyncDate.
     * Uses withContext to ensure update completes before returning, preventing race conditions
     * when EditResourceActivity opens immediately after browsing.
     * 
     * @param resource The resource to update
     * @param actualFileCount The actual number of files found during browsing
     */
    suspend fun updateMetadata(resource: MediaResource, actualFileCount: Int) {
        withContext(ioDispatcher) {
            try {
                // For network resources (SMB/SFTP/FTP), update lastSyncDate
                val isNetworkResource = resource.type == ResourceType.SMB || 
                                        resource.type == ResourceType.SFTP || 
                                        resource.type == ResourceType.FTP
                
                val timestamp = System.currentTimeMillis()
                val updatedResource = resource.copy(
                    fileCount = actualFileCount,
                    lastBrowseDate = timestamp,
                    lastSyncDate = if (isNetworkResource) System.currentTimeMillis() else resource.lastSyncDate
                )
                
                Timber.d("BrowseMetadataManager: Updating resource metadata: id=${resource.id}, name=${resource.name}, OLD lastBrowseDate=${resource.lastBrowseDate}, NEW lastBrowseDate=$timestamp")
                
                val result = updateResourceUseCase(updatedResource)
                
                if (result.isSuccess) {
                    Timber.d("BrowseMetadataManager: Successfully updated resource metadata: fileCount=$actualFileCount, lastBrowseDate=$timestamp, lastSyncDate=${if (isNetworkResource) "updated" else "unchanged"}")
                } else {
                    Timber.e("BrowseMetadataManager: Failed to update resource metadata: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Timber.e(e, "BrowseMetadataManager: Exception while updating resource metadata")
            }
        }
    }
}
