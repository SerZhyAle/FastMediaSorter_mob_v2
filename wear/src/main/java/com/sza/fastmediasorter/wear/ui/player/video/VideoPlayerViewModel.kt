package com.sza.fastmediasorter.wear.ui.player.video

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.wear.data.network.smb.SmbDataSource
import com.sza.fastmediasorter.wear.data.wear.WatchPlaybackCommandEvents
import com.sza.fastmediasorter.wear.domain.model.MediaType
import com.sza.fastmediasorter.wear.domain.model.WearPlaybackCommand
import com.sza.fastmediasorter.wear.domain.model.WearPlaybackStatePayload
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearMediaRepository
import com.sza.fastmediasorter.wear.domain.usecase.PublishPlaybackStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

private const val PREFS_NAME = "wear_video_prefs"
private const val KEY_BATTERY_WARNING_SHOWN = "battery_warning_shown"

/**
 * ViewModel for the video player screen.
 * Manages ExoPlayer instance and playback state for video files.
 */
@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    private val mediaRepository: WearMediaRepository,
    private val selectedMediaManager: SelectedMediaManager,
    private val smbDataSource: SmbDataSource,
    private val exoPlayer: ExoPlayer,
    private val publishPlaybackStateUseCase: PublishPlaybackStateUseCase,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()
    
    private val fileId: Long = savedStateHandle.get<Long>("fileId") ?: -1L
    private var progressUpdateJob: Job? = null
    private var controlsHideJob: Job? = null
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Timber.d("onIsPlayingChanged: $isPlaying")
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressUpdates()
                scheduleHideControls()
            } else {
                stopProgressUpdates()
                showControls()
            }
            publishPlaybackState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            Timber.d("Playback state changed: $playbackState")
            when (playbackState) {
                Player.STATE_READY -> {
                    Timber.d("Player STATE_READY, duration: ${exoPlayer.duration}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            durationMs = exoPlayer.duration.coerceAtLeast(0)
                        )
                    }
                    publishPlaybackState()
                }
                Player.STATE_ENDED -> {
                    Timber.d("Player STATE_ENDED - video finished")
                    // Just show controls and reset position, don't auto-seek
                    _uiState.update { it.copy(isPlaying = false, showControls = true) }
                    // Seek to start for replay, but pause
                    exoPlayer.pause()
                    exoPlayer.seekTo(0)
                    // Update position after seek
                    _uiState.update { it.copy(currentPositionMs = 0) }
                    publishPlaybackState()
                }
                Player.STATE_BUFFERING -> {
                    Timber.d("Player STATE_BUFFERING")
                    _uiState.update { it.copy(isLoading = true) }
                }
                Player.STATE_IDLE -> {
                    Timber.d("Player STATE_IDLE")
                }
            }
        }
        
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Timber.e(error, "ExoPlayer error: ${error.errorCodeName}")
            _uiState.update { 
                it.copy(
                    isLoading = false, 
                    error = "Playback error: ${error.message ?: error.errorCodeName}"
                ) 
            }
        }
    }
    
    init {
        Timber.d("VideoPlayerViewModel initialized with fileId: $fileId")
        exoPlayer.addListener(playerListener)

        // Auto-load if fileId is valid (from SavedStateHandle)
        if (fileId != -1L) {
            loadVideoFile()
        }

        // Subscribe to remote playback commands from phone
        viewModelScope.launch {
            WatchPlaybackCommandEvents.commandFlow.collect { command ->
                when (command) {
                    WearPlaybackCommand.PLAY_PAUSE -> togglePlayPause()
                    WearPlaybackCommand.NEXT       -> exoPlayer.seekToNextMediaItem()
                    WearPlaybackCommand.PREVIOUS   -> exoPlayer.seekToPreviousMediaItem()
                    WearPlaybackCommand.STOP       -> exoPlayer.stop()
                }
            }
        }
    }
    
    private fun loadVideoFile() {
        Timber.d("Loading video file with fileId: $fileId")
        checkBatteryWarning()
        loadMediaFile()
    }
    
    private fun checkBatteryWarning() {
        val warningShown = prefs.getBoolean(KEY_BATTERY_WARNING_SHOWN, false)
        if (!warningShown) {
            _uiState.update { it.copy(showBatteryWarning = true) }
        }
    }
    
    fun dismissBatteryWarning() {
        prefs.edit().putBoolean(KEY_BATTERY_WARNING_SHOWN, true).apply()
        _uiState.update { it.copy(showBatteryWarning = false) }
    }
    
    private fun loadMediaFile() {
        viewModelScope.launch {
            // First, check if we have a selected file from SelectedMediaManager (SMB source)
            val selectedMedia = selectedMediaManager.getSelectedFileById(fileId)
            
            if (selectedMedia != null && selectedMedia.isNetworkSource) {
                // SMB file - need to download first for ExoPlayer
                Timber.d("Loading SMB file: ${selectedMedia.file.name}")
                loadSmbFile(selectedMedia.streamUri, selectedMedia.file.name)
            } else {
                // Local file - use MediaStore
                val file = mediaRepository.getMediaFileById(fileId, MediaType.VIDEO)
                if (file != null) {
                    _uiState.update { it.copy(mediaFile = file) }
                    
                    val mediaItem = MediaItem.fromUri(file.uri)
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    // Don't auto-play until battery warning is dismissed
                    if (!_uiState.value.showBatteryWarning) {
                        exoPlayer.playWhenReady = true
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "File not found") }
                }
            }
        }
    }
    
    /**
     * Load SMB file by downloading it to cache and playing from there.
     * This is required because ExoPlayer cannot directly stream from SMB InputStream.
     */
    private suspend fun loadSmbFile(filePath: String, fileName: String) {
        _uiState.update { it.copy(isLoading = true) }
        
        withContext(Dispatchers.IO) {
            try {
                Timber.d("Downloading SMB file: $filePath")
                
                // Get file stream from SMB
                val streamResult = smbDataSource.getFileStream(filePath)
                
                streamResult.fold(
                    onSuccess = { inputStream ->
                        // Create temp file in cache directory
                        val cacheDir = File(context.cacheDir, "smb_video")
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
                        
                        Timber.d("SMB file downloaded, size: ${tempFile.length()} bytes")
                        
                        // Play from temp file on main thread
                        withContext(Dispatchers.Main) {
                            val mediaItem = MediaItem.fromUri(android.net.Uri.fromFile(tempFile))
                            exoPlayer.setMediaItem(mediaItem)
                            exoPlayer.prepare()
                            _uiState.update { it.copy(isLoading = false) }
                            
                            // Don't auto-play until battery warning is dismissed
                            if (!_uiState.value.showBatteryWarning) {
                                exoPlayer.playWhenReady = true
                            }
                        }
                    },
                    onFailure = { e ->
                        Timber.e(e, "Failed to download SMB file")
                        withContext(Dispatchers.Main) {
                            _uiState.update { 
                                it.copy(isLoading = false, error = "Failed to load: ${e.message}") 
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Exception loading SMB file")
                withContext(Dispatchers.Main) {
                    _uiState.update { 
                        it.copy(isLoading = false, error = "Error: ${e.message}") 
                    }
                }
            }
        }
    }
    
    fun getPlayer(): ExoPlayer = exoPlayer
    
    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }
    
    fun onScreenTap() {
        if (_uiState.value.showControls) {
            _uiState.update { it.copy(showControls = false) }
        } else {
            showControls()
            if (exoPlayer.isPlaying) {
                scheduleHideControls()
            }
        }
    }
    
    private fun showControls() {
        controlsHideJob?.cancel()
        _uiState.update { it.copy(showControls = true) }
    }
    
    private fun scheduleHideControls() {
        controlsHideJob?.cancel()
        controlsHideJob = viewModelScope.launch {
            delay(3000)
            _uiState.update { it.copy(showControls = false) }
        }
    }
    
    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _uiState.update { it.copy(currentPositionMs = positionMs) }
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

    private fun publishPlaybackState() {
        val state = _uiState.value
        val selected = selectedMediaManager.getSelectedFileById(fileId)
        val sourceName = if (selected?.isNetworkSource == true) {
            selected.file.uri.host ?: ""
        } else {
            "Local"
        }
        val payload = WearPlaybackStatePayload(
            isPlaying = state.isPlaying,
            fileName = state.mediaFile?.name ?: "",
            sourceName = sourceName,
            positionMs = state.currentPositionMs,
            durationMs = state.durationMs,
            mediaType = "VIDEO"
        )
        viewModelScope.launch { publishPlaybackStateUseCase(payload) }
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("VideoPlayerViewModel cleared")
        stopProgressUpdates()
        controlsHideJob?.cancel()
        exoPlayer.removeListener(playerListener)
        // S0725: this VM owns its ExoPlayer (no longer a process singleton) - release native resources
        // instead of just stop()+clearMediaItems(); pairs with PlayerView.player = null in the screen's
        // onDispose so neither the player nor the disposed PlayerView/Context survives screen exit.
        exoPlayer.release()
    }
}
