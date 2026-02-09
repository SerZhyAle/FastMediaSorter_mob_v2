package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the currently selected media file for playback.
 * 
 * This singleton provides a way to pass selected file information between 
 * the browse screen and player screens, especially for network sources 
 * where file data is not available through MediaStore.
 * 
 * For local files, the player can still query MediaStore by ID,
 * but for SMB files, this manager provides the necessary file information.
 */
@Singleton
class SelectedMediaManager @Inject constructor() {
    
    private val _selectedFile = MutableStateFlow<SelectedMedia?>(null)
    val selectedFile: StateFlow<SelectedMedia?> = _selectedFile.asStateFlow()
    
    /**
     * Set the selected file for playback.
     * 
     * @param file The media file selected for playback
     * @param isNetworkSource True if the file is from a network source (SMB)
     * @param streamUri The full URI for streaming (for SMB files)
     */
    fun selectFile(
        file: WearMediaFile, 
        isNetworkSource: Boolean,
        streamUri: String? = null
    ) {
        val effectiveStreamUri = streamUri ?: file.uri.toString()
        Timber.d("SelectedMediaManager: Selected file=${file.name}, isNetwork=$isNetworkSource, streamUri=$effectiveStreamUri")
        _selectedFile.value = SelectedMedia(
            file = file,
            isNetworkSource = isNetworkSource,
            streamUri = effectiveStreamUri
        )
    }
    
    /**
     * Get the selected file by ID.
     * Returns the file only if the ID matches the currently selected file.
     */
    fun getSelectedFileById(id: Long): SelectedMedia? {
        val current = _selectedFile.value
        return if (current?.file?.id == id) {
            Timber.d("SelectedMediaManager: Found file by id=$id: ${current.file.name}")
            current
        } else {
            Timber.d("SelectedMediaManager: No file found for id=$id (current=${current?.file?.id})")
            null
        }
    }
    
    /**
     * Clear the selection.
     */
    fun clearSelection() {
        Timber.d("SelectedMediaManager: Selection cleared")
        _selectedFile.value = null
    }
}

/**
 * Represents a selected media file with streaming information.
 */
data class SelectedMedia(
    val file: WearMediaFile,
    val isNetworkSource: Boolean,
    val streamUri: String
)
