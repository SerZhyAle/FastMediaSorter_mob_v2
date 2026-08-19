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
     * @param isNetworkSource True if the file is from a network source
     * @param streamUri The full URI for streaming (for network files)
     * @param sourceId Id of the network source the file was browsed from
     */
    fun selectFile(
        file: WearMediaFile,
        isNetworkSource: Boolean,
        streamUri: String? = null,
        sourceId: String? = null,
        isDirectStream: Boolean = false
    ) {
        val effectiveStreamUri = streamUri ?: file.uri.toString()
        Timber.d(
            "SelectedMediaManager: Selected file=${file.name}, isNetwork=$isNetworkSource, " +
                "sourceId=$sourceId, streamUri=$effectiveStreamUri, isDirectStream=$isDirectStream"
        )
        _selectedFile.value = SelectedMedia(
            file = file,
            isNetworkSource = isNetworkSource,
            streamUri = effectiveStreamUri,
            sourceId = sourceId,
            isDirectStream = isDirectStream
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
 *
 * S1687: [sourceId] is what lets a player read the file over the protocol it actually came from.
 * Without it the handover carried only "is this network", every player assumed SMB, and nothing but
 * SMB ever played. The protocol enum is deliberately not duplicated here - the id resolves to the
 * whole [com.sza.fastmediasorter.wear.domain.model.NetworkSource], credentials included.
 *
 * S1708: [isDirectStream] indicates a live network stream (e.g. radio/video) played directly by URL
 * without downloading to a temporary file.
 */
data class SelectedMedia(
    val file: WearMediaFile,
    val isNetworkSource: Boolean,
    val streamUri: String,
    val sourceId: String? = null,
    val isDirectStream: Boolean = false
)
