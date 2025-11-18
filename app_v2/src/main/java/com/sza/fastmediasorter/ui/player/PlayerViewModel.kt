package com.sza.fastmediasorter.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.core.ui.BaseViewModel
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter.domain.usecase.GetMediaFilesUseCase
import com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getResourcesUseCase: GetResourcesUseCase,
    private val getMediaFilesUseCase: GetMediaFilesUseCase,
    val fileOperationUseCase: FileOperationUseCase,
    val getDestinationsUseCase: GetDestinationsUseCase,
    private val settingsRepository: SettingsRepository,
    private val resourceRepository: com.sza.fastmediasorter.domain.repository.ResourceRepository
) : BaseViewModel<PlayerViewModel.PlayerState, PlayerViewModel.PlayerEvent>() {

    data class PlayerState(
        val files: List<MediaFile> = emptyList(),
        val currentIndex: Int = 0,
        val isSlideShowActive: Boolean = false,
        val slideShowInterval: Long = 3000,
        val playToEndInSlideshow: Boolean = false,
        val showControls: Boolean = false,
        val isPaused: Boolean = false,
        val showCommandPanel: Boolean = false,
        val showSmallControls: Boolean = false,
        val allowRename: Boolean = true,
        val allowDelete: Boolean = true,
        val enableCopying: Boolean = true,
        val enableMoving: Boolean = true,
        val resource: MediaResource? = null,
        val lastOperation: UndoOperation? = null,
        val undoOperationTimestamp: Long? = null
    ) {
        val currentFile: MediaFile? get() = files.getOrNull(currentIndex)
        // Circular navigation: always allow prev/next if files.size > 1
        val hasPrevious: Boolean get() = files.size > 1
        val hasNext: Boolean get() = files.size > 1
    }

    sealed class PlayerEvent {
        data class ShowError(val message: String) : PlayerEvent()
        data class ShowMessage(val message: String) : PlayerEvent()
        // Removed: LoadingProgress event (dialog not needed for single file loads)
        object FinishActivity : PlayerEvent()
    }

    override fun getInitialState(): PlayerState {
        return PlayerState(currentIndex = initialIndex)
    }

    private val resourceId = savedStateHandle.get<Long>("resourceId")
        ?: savedStateHandle.get<String>("resourceId")?.toLongOrNull() ?: 0L
    private val initialIndex = savedStateHandle.get<Int>("initialIndex")
        ?: savedStateHandle.get<String>("initialIndex")?.toIntOrNull() ?: 0
    private val skipAvailabilityCheck: Boolean = savedStateHandle.get<Boolean>("skipAvailabilityCheck") ?: false
    private val initialFilePath: String? = savedStateHandle.get<String>("initialFilePath")
    
    private var loadingJob: Job? = null

    init {
        loadSettings()
        loadMediaFiles()
    }
    
    /**
     * Reload media files list.
     * Call when returning from background to reflect external changes.
     */
    fun reloadFiles() {
        loadMediaFiles()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val settings = settingsRepository.getSettings().first()
                val resource = state.value.resource
                
                // Determine showCommandPanel: use resource-specific setting if available, otherwise use global default
                val showCommandPanel = resource?.showCommandPanel ?: settings.defaultShowCommandPanel
                
                updateState { 
                    it.copy(
                        showCommandPanel = showCommandPanel,
                        showSmallControls = settings.showSmallControls,
                        allowRename = settings.allowRename,
                        allowDelete = settings.allowDelete,
                        enableCopying = settings.enableCopying,
                        enableMoving = settings.enableMoving,
                        // Set slideshow interval from global settings (will be overridden by resource-specific if available)
                        slideShowInterval = settings.slideshowInterval * 1000L,
                        playToEndInSlideshow = settings.playToEndInSlideshow
                    )
                }
            } catch (e: Exception) {
                // Use default value (fullscreen mode)
            }
        }
    }

    private fun loadMediaFiles() {
        loadingJob = viewModelScope.launch {
            setLoading(true)
            try {
                val resource = getResourcesUseCase.getById(resourceId)
                if (resource == null) {
                    sendEvent(PlayerEvent.ShowError("Resource not found"))
                    sendEvent(PlayerEvent.FinishActivity)
                    setLoading(false)
                    return@launch
                }

                // Check if resource is available (skip if already validated)
                if (!skipAvailabilityCheck && resource.fileCount == 0 && !resource.isWritable) {
                    sendEvent(PlayerEvent.ShowError("Resource '${resource.name}' is unavailable. Check network connection or resource settings."))
                    sendEvent(PlayerEvent.FinishActivity)
                    setLoading(false)
                    return@launch
                }

                // Get current settings for size filters
                val settings = settingsRepository.getSettings().first()
                val sizeFilter = SizeFilter(
                    imageSizeMin = settings.imageSizeMin,
                    imageSizeMax = settings.imageSizeMax,
                    videoSizeMin = settings.videoSizeMin,
                    videoSizeMax = settings.videoSizeMax,
                    audioSizeMin = settings.audioSizeMin,
                    audioSizeMax = settings.audioSizeMax
                )

                // Load from cache (instant) - BrowseActivity already loaded and cached the list
                Timber.d("Loading files from cache for resource ${resource.id}")
                val cachedFiles = MediaFilesCacheManager.getCachedList(resource.id)
                val files = if (cachedFiles != null && cachedFiles.isNotEmpty()) {
                    Timber.d("Using cached list (${cachedFiles.size} files)")
                    cachedFiles
                } else {
                    // Fallback: cache miss - load via UseCase (should rarely happen)
                    Timber.w("Cache miss! Loading files via UseCase (slow path)")
                    getMediaFilesUseCase(
                        resource = resource,
                        sizeFilter = sizeFilter,
                        useChunkedLoading = false,
                        maxFiles = Int.MAX_VALUE,
                        onProgress = null
                    ).first()
                }
                
                if (files.isEmpty()) {
                    sendEvent(PlayerEvent.ShowError("No media files found"))
                    sendEvent(PlayerEvent.FinishActivity)
                } else {
                    // If initialFilePath provided, find file by path (for pagination mode)
                    // Otherwise use initialIndex
                    val safeIndex = if (initialFilePath != null) {
                        val foundIndex = files.indexOfFirst { it.path == initialFilePath }
                        if (foundIndex >= 0) {
                            Timber.d("Found file by path: $initialFilePath at index $foundIndex")
                            foundIndex
                        } else {
                            Timber.w("File not found by path: $initialFilePath, using index 0")
                            0
                        }
                    } else {
                        initialIndex.coerceIn(0, files.size - 1)
                    }
                    // Use resource-specific slideshow interval if available (non-default), otherwise keep global settings
                    val intervalToUse = if (resource.slideshowInterval != 10) {
                        resource.slideshowInterval * 1000L
                    } else {
                        state.value.slideShowInterval // Keep global settings value
                    }
                    
                    // Update state with resource first
                    updateState { 
                        it.copy(
                            files = files, 
                            currentIndex = safeIndex, 
                            resource = resource,
                            slideShowInterval = intervalToUse
                        ) 
                    }
                }
                setLoading(false)
            } catch (e: Exception) {
                sendEvent(PlayerEvent.ShowError(e.message ?: "Failed to load media files"))
                sendEvent(PlayerEvent.FinishActivity)
                setLoading(false)
            }
        }
    }

    fun nextFile() {
        val currentState = state.value
        if (currentState.files.isEmpty()) return
        
        val nextIndex = if (currentState.currentIndex >= currentState.files.size - 1) {
            0 // Loop to first file after last
        } else {
            currentState.currentIndex + 1
        }
        updateState { it.copy(currentIndex = nextIndex) }
    }

    fun previousFile() {
        val currentState = state.value
        if (currentState.files.isEmpty()) return
        
        val prevIndex = if (currentState.currentIndex <= 0) {
            currentState.files.size - 1 // Loop to last file before first
        } else {
            currentState.currentIndex - 1
        }
        updateState { it.copy(currentIndex = prevIndex) }
    }
    
    fun cancelLoading() {
        Timber.d("Cancelling file loading")
        loadingJob?.cancel()
        loadingJob = null
    }

    fun toggleSlideShow() {
        updateState { it.copy(isSlideShowActive = !it.isSlideShowActive) }
    }

    fun setSlideShowInterval(interval: Long) {
        updateState { it.copy(slideShowInterval = interval) }
    }

    fun toggleControls() {
        updateState { it.copy(showControls = !it.showControls) }
    }

    fun togglePause() {
        updateState { it.copy(isPaused = !it.isPaused) }
    }

    fun toggleCommandPanel() {
        val newShowCommandPanel = !state.value.showCommandPanel
        updateState { it.copy(showCommandPanel = newShowCommandPanel) }
        
        // Save user preference for this resource
        val resource = state.value.resource
        if (resource != null) {
            viewModelScope.launch {
                try {
                    resourceRepository.updateResource(resource.copy(showCommandPanel = newShowCommandPanel))
                } catch (e: Exception) {
                    timber.log.Timber.e(e, "Failed to save command panel preference")
                }
            }
        }
    }
    
    /**
     * Delete the current file and navigate to next/previous file.
     * @return true if file deleted successfully and navigation occurred, false if deletion failed, null if no files remain (should finish activity)
     */
    fun deleteCurrentFile(): Boolean? {
        val currentFile = state.value.currentFile
        val resource = state.value.resource
        
        if (currentFile == null) {
            sendEvent(PlayerEvent.ShowError("No file to delete"))
            return false
        }
        
        if (resource == null) {
            sendEvent(PlayerEvent.ShowError("Resource not loaded"))
            return false
        }
        
        viewModelScope.launch {
            try {
                Timber.d("Deleting file: ${currentFile.path}")
                
                // Wrap path in File object for FileOperationUseCase
                val file = if (currentFile.path.startsWith("smb://") || 
                               currentFile.path.startsWith("sftp://") || 
                               currentFile.path.startsWith("ftp://")) {
                    // Network file - wrap with original path
                    object : java.io.File(currentFile.path) {
                        override fun getAbsolutePath(): String = currentFile.path
                        override fun getPath(): String = currentFile.path
                    }
                } else {
                    // Local file
                    java.io.File(currentFile.path)
                }
                
                // Use FileOperationUseCase for both local and network files
                val deleteOperation = FileOperation.Delete(files = listOf(file))
                
                when (val result = fileOperationUseCase.execute(deleteOperation)) {
                    is FileOperationResult.Success,
                    is FileOperationResult.PartialSuccess -> {
                        // Save undo operation if enabled in settings
                        val settings = settingsRepository.getSettings().first()
                        if (settings.enableUndo) {
                            val undoOp = UndoOperation(
                                type = com.sza.fastmediasorter.domain.model.FileOperationType.DELETE,
                                sourceFiles = listOf(currentFile.path),
                                destinationFolder = null,
                                copiedFiles = null,
                                oldNames = null
                            )
                            saveUndoOperation(undoOp)
                        }
                        
                        // Remove deleted file from the list
                        val updatedFiles = state.value.files.toMutableList()
                        val deletedIndex = state.value.currentIndex
                        updatedFiles.removeAt(deletedIndex)
                        
                        // Update cache to reflect deletion
                        MediaFilesCacheManager.removeFile(resource.id, currentFile.path)
                        
                        if (updatedFiles.isEmpty()) {
                            // No files left, close activity
                            sendEvent(PlayerEvent.ShowMessage("File deleted. Tap UNDO to restore."))
                            sendEvent(PlayerEvent.FinishActivity)
                        } else {
                            // Navigate to next file, or previous if we deleted the last one
                            val newIndex = if (deletedIndex >= updatedFiles.size) {
                                updatedFiles.size - 1 // Move to last file
                            } else {
                                deletedIndex // Stay at same index (which now points to next file)
                            }
                            
                            updateState { it.copy(files = updatedFiles, currentIndex = newIndex) }
                            sendEvent(PlayerEvent.ShowMessage("File deleted. Tap UNDO to restore."))
                            Timber.d("File deleted successfully, new list size: ${updatedFiles.size}")
                        }
                    }
                    is FileOperationResult.Failure -> {
                        sendEvent(PlayerEvent.ShowError(result.error))
                        Timber.e("Delete failed: ${result.error}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error deleting file: ${currentFile.path}")
                sendEvent(PlayerEvent.ShowError("Error deleting file: ${e.message}"))
            }
        }
        
        return null // Async operation, result via events
    }
    
    /**
     * Reload files after rename operation to update current file path
     */
    fun reloadAfterRename() {
        viewModelScope.launch {
            try {
                val resource = state.value.resource ?: return@launch
                
                // Reload files from resource
                val files = getMediaFilesUseCase(
                    resource = resource,
                    sortMode = resource.sortMode,
                    sizeFilter = null
                ).first()
                
                // Find renamed file by name (may have changed)
                // Try to locate file at same index
                val currentIndex = state.value.currentIndex
                val newIndex = if (currentIndex < files.size) {
                    currentIndex
                } else {
                    0
                }
                
                updateState { 
                    it.copy(
                        files = files,
                        currentIndex = newIndex
                    )
                }
                
                Timber.d("Files reloaded after rename, total: ${files.size}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to reload files after rename")
                sendEvent(PlayerEvent.ShowError("Failed to reload files: ${e.message}"))
            }
        }
    }
    
    /**
     * Save undo operation with timestamp
     */
    fun saveUndoOperation(operation: UndoOperation) {
        updateState { 
            it.copy(
                lastOperation = operation,
                undoOperationTimestamp = System.currentTimeMillis()
            ) 
        }
        timber.log.Timber.d("Saved undo operation: ${operation.type}, file: ${operation.sourceFiles.firstOrNull()}")
    }
    
    /**
     * Undo last delete operation
     */
    fun undoLastOperation() {
        val operation = state.value.lastOperation
        if (operation == null) {
            sendEvent(PlayerEvent.ShowMessage("No operation to undo"))
            return
        }
        
        if (operation.type != com.sza.fastmediasorter.domain.model.FileOperationType.DELETE) {
            sendEvent(PlayerEvent.ShowMessage("Can only undo delete operations"))
            return
        }
        
        try {
            // Restore file from trash folder
            operation.copiedFiles?.let { paths ->
                if (paths.size >= 2) {
                    val trashDirPath = paths[0]
                    val originalPath = paths[1]
                    
                    val trashDir = java.io.File(trashDirPath)
                    val originalFile = java.io.File(originalPath)
                    
                    if (trashDir.exists() && trashDir.isDirectory) {
                        // Find trashed file by name
                        val trashedFile = java.io.File(trashDir, originalFile.name)
                        
                        if (trashedFile.exists() && trashedFile.renameTo(originalFile)) {
                            // Remove trash directory if empty
                            if (trashDir.listFiles()?.isEmpty() == true) {
                                trashDir.delete()
                            }
                            
                            // Clear undo operation
                            updateState { it.copy(lastOperation = null, undoOperationTimestamp = null) }
                            
                            sendEvent(PlayerEvent.ShowMessage("File restored successfully"))
                            
                            // Reload files to include restored file
                            reloadFiles()
                        } else {
                            sendEvent(PlayerEvent.ShowError("Failed to restore file"))
                        }
                    } else {
                        sendEvent(PlayerEvent.ShowError("Trash folder not found"))
                    }
                } else {
                    sendEvent(PlayerEvent.ShowError("Invalid undo operation data"))
                }
            } ?: sendEvent(PlayerEvent.ShowError("No files to restore"))
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Undo operation failed")
            sendEvent(PlayerEvent.ShowError("Undo failed: ${e.message}"))
        }
    }
    
    /**
     * Clear expired undo operation (5 minutes)
     */
    fun clearExpiredUndoOperation() {
        val currentState = state.value
        val timestamp = currentState.undoOperationTimestamp
        
        if (timestamp != null && currentState.lastOperation != null) {
            val elapsed = System.currentTimeMillis() - timestamp
            val expiryTime = 5 * 60 * 1000L // 5 minutes
            
            if (elapsed > expiryTime) {
                updateState { it.copy(lastOperation = null, undoOperationTimestamp = null) }
                timber.log.Timber.d("Expired undo operation cleared")
            }
        }
    }

    suspend fun getSettings() = settingsRepository.getSettings().first()
    
    /**
     * Get adjacent files for preloading (previous + next).
     * Only returns IMAGE and GIF files for preloading.
     * Supports circular navigation.
     * 
     * @return List of MediaFile to preload (previous, next)
     */
    fun getAdjacentFiles(): List<MediaFile> {
        val currentState = state.value
        if (currentState.files.size <= 1) return emptyList()
        
        val result = mutableListOf<MediaFile>()
        
        // Calculate previous index with circular wrap
        val prevIndex = if (currentState.currentIndex <= 0) {
            currentState.files.size - 1 // Loop to last
        } else {
            currentState.currentIndex - 1
        }
        val prevFile = currentState.files.getOrNull(prevIndex)
        
        // Calculate next index with circular wrap
        val nextIndex = if (currentState.currentIndex >= currentState.files.size - 1) {
            0 // Loop to first
        } else {
            currentState.currentIndex + 1
        }
        val nextFile = currentState.files.getOrNull(nextIndex)
        
        // Add previous file if it's an image or GIF
        if (prevFile != null && (prevFile.type == MediaType.IMAGE || prevFile.type == MediaType.GIF)) {
            result.add(prevFile)
        }
        
        // Add next file if it's an image or GIF AND it's different from previous (avoid duplicates in 2-file case)
        if (nextFile != null && 
            nextFile != prevFile &&
            (nextFile.type == MediaType.IMAGE || nextFile.type == MediaType.GIF)) {
            result.add(nextFile)
        }
        
        return result
    }
    
    /**
     * Save last viewed file path to resource for position restoration
     */
    fun saveLastViewedFile(filePath: String) {
        val resource = state.value.resource ?: return
        
        viewModelScope.launch {
            try {
                resourceRepository.updateResource(resource.copy(lastViewedFile = filePath))
                Timber.d("Saved lastViewedFile=$filePath for resource: ${resource.name}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to save lastViewedFile")
            }
        }
    }
}
