package com.sza.fastmediasorter_v2.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter_v2.core.ui.BaseViewModel
import com.sza.fastmediasorter_v2.domain.model.MediaFile
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.model.ResourceType
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
    private val settingsRepository: SettingsRepository
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
        val resource: MediaResource? = null
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

    init {
        loadMediaFiles()
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val settings = settingsRepository.getSettings().first()
                // If fullScreenMode is true, showCommandPanel should be false (and vice versa)
                updateState { 
                    it.copy(
                        showCommandPanel = !settings.fullScreenMode,
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

                // Check if resource is available
                if (resource.fileCount == 0 && !resource.isWritable) {
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

                // For SMB resources: use chunked loading to show first files quickly
                val files = getMediaFilesUseCase(
                    resource = resource,
                    sizeFilter = sizeFilter,
                    useChunkedLoading = resource.type == ResourceType.SMB,
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
        updateState { it.copy(showCommandPanel = !it.showCommandPanel) }
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
            if (file.delete()) {
                // Remove deleted file from the list
                val updatedFiles = state.value.files.toMutableList()
                val deletedIndex = state.value.currentIndex
                updatedFiles.removeAt(deletedIndex)
                
                if (updatedFiles.isEmpty()) {
                    // No files left, close activity
                    sendEvent(PlayerEvent.ShowMessage("File deleted. No more files to display."))
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
                    sendEvent(PlayerEvent.ShowMessage("File deleted successfully"))
                    true
                }
            } else {
                sendEvent(PlayerEvent.ShowError("Failed to delete file"))
                false
            }
        } catch (e: Exception) {
            sendEvent(PlayerEvent.ShowError("Error deleting file: ${e.message}"))
            false
        }
    }

    suspend fun getSettings() = settingsRepository.getSettings().first()
}
