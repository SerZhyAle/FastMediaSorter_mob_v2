package com.sza.fastmediasorter.ui.browse.metadata

import com.sza.fastmediasorter.domain.model.MediaResource
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
    * when ResourceEditorActivity opens immediately after browsing.
     * 
     * @param resource The resource to update
     * @param actualFileCount The actual number of files found during browsing
     */
    /**
     * @param subfolderCount Number of subdirectories found during scanning, or -1 to keep
     * the existing value stored in the resource (e.g. when loading from cache).
     * @return the persisted resource copy on success, null on failure. S1001: the caller MUST
     * merge the returned stats into the in-memory Browse state - otherwise every later
     * full-entity write built from that state (scroll/lastViewed/sort) clobbers this update.
     */
    suspend fun updateMetadata(resource: MediaResource, actualFileCount: Int, subfolderCount: Int = -1): MediaResource? {
        return withContext(ioDispatcher) {
            try {
                // S1001: isNetworkResource includes CLOUD - the statistics renderer shows
                // "Last sync" for every non-LOCAL type, so cloud browses must stamp it too.
                val isNetworkResource = resource.type.isNetworkResource

                val timestamp = System.currentTimeMillis()
                val updatedResource = resource.copy(
                    fileCount = actualFileCount,
                    lastBrowseDate = timestamp,
                    lastSyncDate = if (isNetworkResource) timestamp else resource.lastSyncDate,
                    subfolderCount = if (subfolderCount >= 0) subfolderCount else resource.subfolderCount
                )

                Timber.d(
                    "BrowseMetadataManager: Updating resource metadata: id=${resource.id}, " +
                        "name=${resource.name}, OLD lastBrowseDate=${resource.lastBrowseDate}, NEW lastBrowseDate=$timestamp"
                )

                val result = updateResourceUseCase(updatedResource)

                if (result.isSuccess) {
                    Timber.d(
                        "BrowseMetadataManager: Successfully updated resource metadata: fileCount=$actualFileCount, " +
                            "subfolderCount=${updatedResource.subfolderCount}, lastBrowseDate=$timestamp, " +
                            "lastSyncDate=${if (isNetworkResource) "updated" else "unchanged"}"
                    )
                    updatedResource
                } else {
                    Timber.e("BrowseMetadataManager: Failed to update resource metadata: ${result.exceptionOrNull()?.message}")
                    null
                }
            } catch (e: Exception) {
                Timber.e(e, "BrowseMetadataManager: Exception while updating resource metadata")
                null
            }
        }
    }
}
