package com.sza.fastmediasorter_v2.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter_v2.core.ui.BaseViewModel
import com.sza.fastmediasorter_v2.domain.model.MediaFile
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.model.MediaType
import com.sza.fastmediasorter_v2.domain.model.ResourceType
import com.sza.fastmediasorter_v2.domain.model.UndoOperation
import com.sza.fastmediasorter_v2.domain.repository.SettingsRepository
import com.sza.fastmediasorter_v2.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter_v2.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter_v2.domain.usecase.GetMediaFilesUseCase
import com.sza.fastmediasorter_v2.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter_v2.domain.usecase.SizeFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getResourcesUseCase: GetResourcesUseCase,
    private val getMediaFilesUseCase: GetMediaFilesUseCase,
    val fileOperationUseCase: FileOperationUseCase,
    val getDestinationsUseCase: GetDestinationsUseCase,
    private val settingsRepository: SettingsRepository,
    private val resourceRepository: com.sza.fastmediasorter_v2.domain.repository.ResourceRepository
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

    init {
        loadMediaFiles()
        // loadSettings() is called from loadMediaFiles() after resource is loaded
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
        viewModelScope.launch {
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

                // Use chunked loading for network resources with many files (>= 200)
                // For small folders, full scan is faster (avoids recursive traversal overhead)
                val isNetworkResource = resource.type == ResourceType.SMB || 
                                       resource.type == ResourceType.SFTP || 
                                       resource.type == ResourceType.FTP
                val useChunked = isNetworkResource && resource.fileCount >= 200
                
                val files = getMediaFilesUseCase(
                    resource = resource,
                    sizeFilter = sizeFilter,
                    useChunkedLoading = useChunked,
                    maxFiles = 200 // Load first 200 files quickly for player
                ).first()
                
                if (files.isEmpty()) {
                    sendEvent(PlayerEvent.ShowError("No media files found"))
                    sendEvent(PlayerEvent.FinishActivity)
                } else {
                    val safeIndex = initialIndex.coerceIn(0, files.size - 1)
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
                    
                    // Load settings AFTER resource is set (to apply resource-specific showCommandPanel)
                    loadSettings()
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
        if (currentFile == null) {
            sendEvent(PlayerEvent.ShowError("No file to delete"))
            return false
        }
        
        val file = java.io.File(currentFile.path)
        if (!file.exists()) {
            sendEvent(PlayerEvent.ShowError("File not found"))
            return false
        }
        
        return try {
            // Create .trash folder for soft-delete (undo support)
            val parentDir = file.parentFile
            val trashDir = java.io.File(parentDir, ".trash_${System.currentTimeMillis()}")
            
            if (!trashDir.exists() && !trashDir.mkdirs()) {
                sendEvent(PlayerEvent.ShowError("Failed to create trash folder"))
                return false
            }
            
            // Move file to trash folder
            val trashedFile = java.io.File(trashDir, file.name)
            if (file.renameTo(trashedFile)) {
                // Save undo operation
                val undoOp = UndoOperation(
                    type = com.sza.fastmediasorter_v2.domain.model.FileOperationType.DELETE,
                    sourceFiles = listOf(currentFile.path), // Original path
                    destinationFolder = null,
                    copiedFiles = listOf(trashDir.absolutePath, currentFile.path), // trash dir + original path
                    oldNames = null
                )
                saveUndoOperation(undoOp)
                
                // Remove deleted file from the list
                val updatedFiles = state.value.files.toMutableList()
                val deletedIndex = state.value.currentIndex
                updatedFiles.removeAt(deletedIndex)
                
                if (updatedFiles.isEmpty()) {
                    // No files left, close activity
                    sendEvent(PlayerEvent.ShowMessage("File deleted. Tap UNDO to restore."))
                    sendEvent(PlayerEvent.FinishActivity)
                    null
                } else {
                    // Navigate to next file, or previous if we deleted the last one
                    val newIndex = if (deletedIndex >= updatedFiles.size) {
                        updatedFiles.size - 1 // Move to last file
                    } else {
                        deletedIndex // Stay at same index (which now points to next file)
                    }
                    
                    updateState { it.copy(files = updatedFiles, currentIndex = newIndex) }
                    sendEvent(PlayerEvent.ShowMessage("File deleted. Tap UNDO to restore."))
                    true
                }
            } else {
                // Clean up empty trash folder
                trashDir.delete()
                sendEvent(PlayerEvent.ShowError("Failed to delete file"))
                false
            }
        } catch (e: Exception) {
            sendEvent(PlayerEvent.ShowError("Error deleting file: ${e.message}"))
            false
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
        
        if (operation.type != com.sza.fastmediasorter_v2.domain.model.FileOperationType.DELETE) {
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
}
