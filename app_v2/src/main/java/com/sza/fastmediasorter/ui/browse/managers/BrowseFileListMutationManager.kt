package com.sza.fastmediasorter.ui.browse.managers

import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.data.repository.CachedFileListRepository
import com.sza.fastmediasorter.domain.model.MediaExtensions
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.ui.browse.BrowseState
import com.sza.fastmediasorter.ui.browse.filelist.BrowseFileListManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manages in-memory and cached file-list mutations in the Browse screen:
 * - Remove files after delete/move operations ([removeFiles], [removeFilesFromList]).
 * - Add files back after undo operations ([addFiles]).
 * - Update a single file entry after rename ([updateFile]).
 * - Handle a file that disappeared from disk ([onFileMissingFromDisk]).
 * - Create a [MediaFile] from a [java.io.File] reference ([createMediaFileFromFile]).
 *
 * Extracted from BrowseViewModel (Wave 1 decomposition - IV.1).
 */
class BrowseFileListMutationManager(
    private val fileListManager: BrowseFileListManager,
    private val cachedFileListRepository: CachedFileListRepository,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val stateFlow: StateFlow<BrowseState>,
    private val updateState: ((BrowseState) -> BrowseState) -> Unit,
    private val resourceId: Long,
    private val invalidateDirectoryCache: () -> Unit,
    private val onFilesRemoved: (List<String>) -> Unit,
    private val onFilePathChanged: (oldPath: String, newPath: String) -> Unit
) {
    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Remove [filePaths] from the in-memory list, RAM cache, and DB cache.
     * Notifies the selection manager via [onFilesRemoved].
     */
    fun removeFiles(filePaths: List<String>) {
        if (filePaths.isEmpty()) return

        onFilesRemoved(filePaths)

        val pathsSet = filePaths.toSet()
        val updatedFiles = fileListManager.removeFiles(stateFlow.value.mediaFiles, filePaths)

        updateState { it.copy(mediaFiles = updatedFiles, totalFileCount = updatedFiles.size) }

        // Keep RAM cache in sync so that cache-serve paths never return deleted files.
        MediaFilesCacheManager.setCachedList(resourceId, updatedFiles)

        val resource = stateFlow.value.resource
        if (resource?.rememberFileList == true) {
            // S1307: one decompress/recompress for the whole batch instead of one per path.
            scope.launch(ioDispatcher) {
                cachedFileListRepository.deleteFiles(resource.id, pathsSet)
            }
        }

        invalidateDirectoryCache()
    }

    /**
     * Add [newFiles] to the list and re-sort (used by undo operations).
     */
    fun addFiles(newFiles: List<MediaFile>) {
        if (newFiles.isEmpty()) return

        updateState { state ->
            val updatedFiles = fileListManager.addFiles(state.mediaFiles, newFiles, state.sortMode)
            state.copy(mediaFiles = updatedFiles)
        }

        invalidateDirectoryCache()
    }

    /**
     * Replace the file at [oldPath] with [newFile] (used after rename).
     * Notifies the selection manager about the path change.
     */
    fun updateFile(oldPath: String, newFile: MediaFile) {
        val resource = stateFlow.value.resource
        onFilePathChanged(oldPath, newFile.path)

        updateState { state ->
            val updatedFiles = fileListManager.updateFile(state.mediaFiles, oldPath, newFile, state.sortMode)
            state.copy(mediaFiles = updatedFiles)
        }

        invalidateDirectoryCache()

        if (resource?.rememberFileList == true) {
            scope.launch(ioDispatcher) {
                cachedFileListRepository.updateFile(resource.id, oldPath, newFile)
            }
        }
    }

    /**
     * Remove [paths] from the in-memory list and caches without touching the selection manager.
     * Called when returning from PlayerActivity with a list of moved/deleted files.
     */
    fun removeFilesFromList(paths: List<String>) {
        if (paths.isEmpty()) return

        val pathsSet = paths.toSet()
        val currentFiles = stateFlow.value.mediaFiles
        val updatedFiles = currentFiles.filterNot { it.path in pathsSet }

        if (updatedFiles.size != currentFiles.size) {
            Timber.d("BrowseFileListMutationManager.removeFilesFromList: removed ${currentFiles.size - updatedFiles.size} files")
            updateState { it.copy(mediaFiles = updatedFiles, totalFileCount = updatedFiles.size) }
            MediaFilesCacheManager.setCachedList(resourceId, updatedFiles)

            val resource = stateFlow.value.resource
            if (resource?.rememberFileList == true) {
                scope.launch(ioDispatcher) {
                    cachedFileListRepository.deleteFiles(resource.id, paths)
                }
            }
        }
    }

    /**
     * Remove a file that no longer exists on disk from the list and all caches.
     */
    suspend fun onFileMissingFromDisk(mediaFile: MediaFile) {
        val resource = stateFlow.value.resource ?: return

        Timber.w("BrowseFileListMutationManager: file missing from disk: ${mediaFile.path}")

        updateState { current ->
            val updatedFiles = current.mediaFiles.filterNot { it.path == mediaFile.path }
            current.copy(mediaFiles = updatedFiles, totalFileCount = updatedFiles.size)
        }

        MediaFilesCacheManager.removeFile(resource.id, mediaFile.path)

        if (resource.rememberFileList) {
            cachedFileListRepository.deleteFile(resource.id, mediaFile.path)
        }
    }

    /**
     * Build a [MediaFile] from a local [java.io.File] reference.
     * Used for instant list updates (e.g., after rename) to avoid a full network reload.
     */
    fun createMediaFileFromFile(file: java.io.File): MediaFile {
        val extension = file.extension.lowercase()
        val mediaType = MediaExtensions.getMediaType(extension)
        return MediaFile(
            name = file.name,
            path = file.absolutePath,
            size = file.length(),
            createdDate = file.lastModified(),
            type = mediaType,
            duration = null, width = null, height = null,
            exifOrientation = null, exifDateTime = null,
            exifLatitude = null, exifLongitude = null,
            videoCodec = null, videoBitrate = null,
            videoFrameRate = null, videoRotation = null
        )
    }
}
