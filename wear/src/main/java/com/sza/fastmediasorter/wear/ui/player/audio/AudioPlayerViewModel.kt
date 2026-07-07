package com.sza.fastmediasorter.wear.ui.player.audio

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
import com.sza.fastmediasorter.wear.domain.repository.WearFavoritesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearMediaRepository
import com.sza.fastmediasorter.wear.domain.usecase.PublishPlaybackStateUseCase
import com.sza.fastmediasorter.wear.domain.usecase.SendFavoritesDeltaUseCase
import com.sza.fastmediasorter.wear.util.SmbCacheEvictor
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
    private val selectedMediaManager: SelectedMediaManager,
    private val smbDataSource: SmbDataSource,
    private val exoPlayer: ExoPlayer,
    private val publishPlaybackStateUseCase: PublishPlaybackStateUseCase,
    private val favoritesRepository: WearFavoritesRepository,
    private val sendFavoritesDeltaUseCase: SendFavoritesDeltaUseCase,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val SMB_AUDIO_CACHE_CAP_BYTES = 100L * 1024 * 1024
    }

    private val _uiState = MutableStateFlow(AudioPlayerUiState())
    val uiState: StateFlow<AudioPlayerUiState> = _uiState.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()
    
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
            publishPlaybackState()
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
                    publishPlaybackState()
                }
                Player.STATE_ENDED -> {
                    Timber.d("S0902: audio STATE_ENDED - pause+seekTo(0), no auto-restart")
                    _uiState.update { it.copy(isPlaying = false, currentPositionMs = 0) }
                    // S0902: pause before seeking - playWhenReady stays true otherwise and the
                    // track auto-restarts from 0, looping indefinitely (mirrors VideoPlayerViewModel).
                    exoPlayer.pause()
                    exoPlayer.seekTo(0)
                    publishPlaybackState()
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
                    checkFavoriteState(sourceId = "local", filePath = file.uri.toString())
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

                        // S0902: bound unbounded cache growth - each distinct SMB file adds a
                        // new temp file that was never deleted before this fix.
                        SmbCacheEvictor.evictOldestUntilUnderCap(
                            cacheDir = cacheDir,
                            keep = tempFile,
                            capBytes = SMB_AUDIO_CACHE_CAP_BYTES
                        )

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

    /**
     * S0902: called from the screen's onStop lifecycle effect - without this, playback
     * keeps running while the host activity is stopped (screen off / app backgrounded);
     * onCleared was the only prior teardown edge.
     */
    fun onHostStopped() {
        Timber.d("S0902: audio host stopped - pausing player")
        exoPlayer.pause()
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
            mediaType = "AUDIO"
        )
        viewModelScope.launch { publishPlaybackStateUseCase(payload) }
    }

    fun toggleFavorite() {
        val selected = selectedMediaManager.getSelectedFileById(fileId)
        val sourceId = if (selected?.isNetworkSource == true) selected.file.uri.host ?: "network" else "local"
        val filePath = selected?.streamUri ?: _uiState.value.mediaFile?.uri?.toString() ?: return
        viewModelScope.launch {
            if (_isFavorite.value) {
                favoritesRepository.removeFavorite(sourceId, filePath)
            } else {
                favoritesRepository.addFavorite(sourceId, filePath)
            }
            _isFavorite.value = !_isFavorite.value
            sendFavoritesDeltaUseCase()
        }
    }

    private fun checkFavoriteState(sourceId: String, filePath: String) {
        viewModelScope.launch {
            _isFavorite.value = favoritesRepository.isFavorite(sourceId, filePath)
        }
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("AudioPlayerViewModel cleared")
        stopProgressUpdates()
        exoPlayer.removeListener(playerListener)
        // S0725: this VM owns its ExoPlayer (no longer a process singleton) - release native resources
        // (HandlerThread, AudioTrack/audio-focus, codecs) instead of just stop()+clearMediaItems().
        exoPlayer.release()
    }
}
