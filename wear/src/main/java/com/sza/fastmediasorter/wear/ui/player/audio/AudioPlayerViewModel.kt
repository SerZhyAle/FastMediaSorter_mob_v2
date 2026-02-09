package com.sza.fastmediasorter.wear.ui.player.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.wear.data.network.smb.SmbDataSource
import com.sza.fastmediasorter.wear.domain.model.MediaType
import com.sza.fastmediasorter.wear.domain.repository.AlbumArtRepository
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearMediaRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * ViewModel for the audio player screen.
 * Manages ExoPlayer instance and playback state.
 */
@HiltViewModel
class AudioPlayerViewModel @Inject constructor(
    private val mediaRepository: WearMediaRepository,
    private val albumArtRepository: AlbumArtRepository,
    private val preferencesRepository: WearPreferencesRepository,
    private val selectedMediaManager: SelectedMediaManager,
    private val smbDataSource: SmbDataSource,
    private val exoPlayer: ExoPlayer,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AudioPlayerUiState())
    val uiState: StateFlow<AudioPlayerUiState> = _uiState.asStateFlow()
    
    private val fileId: Long = savedStateHandle.get<Long>("fileId") ?: -1L
    
    private var progressUpdateJob: Job? = null
    
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressUpdates()
            } else {
                stopProgressUpdates()
            }
        }
        
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            durationMs = exoPlayer.duration.coerceAtLeast(0)
                        ) 
                    }
                }
                Player.STATE_ENDED -> {
                    _uiState.update { it.copy(isPlaying = false, currentPositionMs = 0) }
                    exoPlayer.seekTo(0)
                }
                Player.STATE_BUFFERING -> {
                    _uiState.update { it.copy(isLoading = true) }
                }
                else -> {}
            }
        }
    }
    
    init {
        Timber.d("AudioPlayerViewModel initialized with fileId: $fileId")
        exoPlayer.addListener(playerListener)
        
        // Auto-load if fileId is valid (from SavedStateHandle)
        if (fileId != -1L) {
            loadAudioFile()
        }
    }
    
    private fun loadAudioFile() {
        Timber.d("Loading audio file with fileId: $fileId")
        loadMediaFile()
    }
    
    private fun loadMediaFile() {
        viewModelScope.launch {
            // First, check if we have a selected file from SelectedMediaManager (SMB source)
            val selectedMedia = selectedMediaManager.getSelectedFileById(fileId)
            
            if (selectedMedia != null && selectedMedia.isNetworkSource) {
                // SMB file - need to download first for ExoPlayer
                Timber.d("Loading SMB audio: ${selectedMedia.file.name}")
                loadSmbAudio(selectedMedia.streamUri, selectedMedia.file.name)
            } else {
                // Local file - use MediaStore
                val file = mediaRepository.getMediaFileById(fileId, MediaType.MUSIC)
                if (file != null) {
                    _uiState.update { it.copy(mediaFile = file) }
                    
                    val mediaItem = MediaItem.fromUri(file.uri)
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "File not found") }
                }
            }
        }
    }
    
    /**
     * Load SMB audio file by downloading it to cache and playing from there.
     */
    private suspend fun loadSmbAudio(filePath: String, fileName: String) {
        _uiState.update { it.copy(isLoading = true) }
        
        withContext(Dispatchers.IO) {
            try {
                Timber.d("Downloading SMB audio: $filePath")
                
                // Get file stream from SMB
                val streamResult = smbDataSource.getFileStream(filePath)
                
                streamResult.fold(
                    onSuccess = { inputStream ->
                        // Create temp file in cache directory
                        val cacheDir = File(context.cacheDir, "smb_audio")
                        cacheDir.mkdirs()
                        
                        // Use hash of file path for unique filename
                        val tempFileName = "${filePath.hashCode()}_$fileName"
                        val tempFile = File(cacheDir, tempFileName)
                        
                        Timber.d("Saving to temp file: ${tempFile.absolutePath}")
                        
                        // Copy stream to file
                        inputStream.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        
                        Timber.d("SMB audio downloaded, size: ${tempFile.length()} bytes")
                        
                        // Play from temp file on main thread
                        withContext(Dispatchers.Main) {
                            val mediaItem = MediaItem.fromUri(android.net.Uri.fromFile(tempFile))
                            exoPlayer.setMediaItem(mediaItem)
                            exoPlayer.prepare()
                            _uiState.update { it.copy(isLoading = false) }
                            exoPlayer.playWhenReady = true
                        }
                    },
                    onFailure = { e ->
                        Timber.e(e, "Failed to download SMB audio")
                        withContext(Dispatchers.Main) {
                            _uiState.update { 
                                it.copy(isLoading = false, error = "Failed to load: ${e.message}") 
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Exception loading SMB audio")
                withContext(Dispatchers.Main) {
                    _uiState.update { 
                        it.copy(isLoading = false, error = "Error: ${e.message}") 
                    }
                }
            }
        }
    }
    
    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }
    
    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _uiState.update { it.copy(currentPositionMs = positionMs) }
    }
    
    fun seekForward() {
        val newPosition = (exoPlayer.currentPosition + 10_000).coerceAtMost(exoPlayer.duration)
        seekTo(newPosition)
    }
    
    fun seekBackward() {
        val newPosition = (exoPlayer.currentPosition - 10_000).coerceAtLeast(0)
        seekTo(newPosition)
    }
    
    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = viewModelScope.launch {
            while (isActive && exoPlayer.isPlaying) {
                _uiState.update { 
                    it.copy(currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0)) 
                }
                delay(500)
            }
        }
    }
    
    private fun stopProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }
    
    override fun onCleared() {
        super.onCleared()
        Timber.d("AudioPlayerViewModel cleared")
        stopProgressUpdates()
        exoPlayer.removeListener(playerListener)
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }
}
