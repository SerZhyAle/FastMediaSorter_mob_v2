package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.DownloadNetworkFileUseCase
import com.sza.fastmediasorter.domain.usecase.GetMediaFilesUseCase
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundMusicManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resourceRepository: ResourceRepository,
    private val getMediaFilesUseCase: GetMediaFilesUseCase,
    private val downloadNetworkFileUseCase: DownloadNetworkFileUseCase,
    private val fileCache: UnifiedFileCache
) {
    private var musicPlayer: ExoPlayer? = null
    private var isPlaying = false

    // State tracking
    private var currentMusicResourceId: Long? = null
    private var currentPlaylist: List<MediaItem> = emptyList()
    private var availableAudioFiles: List<com.sza.fastmediasorter.domain.model.MediaFile> = emptyList()
    private var currentTrackPath: String? = null
    private var currentTrackName: String? = null // Track name for display (without extension)
    
    // Track failed files to skip corrupted/problematic tracks
    private val failedFiles = mutableSetOf<String>()
    
    // Track name callback for UI display
    private var onTrackChangedListener: ((trackName: String?) -> Unit)? = null
    
    // Error callback for music loading failures
    private var onMusicErrorListener: ((errorMessage: String) -> Unit)? = null
    
    /**
     * Flag to prevent background music during audio slideshow photo mode.
     * Set by PlayerActivity when entering/exiting audio slideshow photo mode.
     */
    var isAudioSlideshowPhotoMode: Boolean = false
    
    // Scope for background file loading
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loadPlaylistJob: Job? = null
    private var healthCheckJob: Job? = null
    
    /**
     * Set listener for track name changes (for UI display during slideshow)
     */
    fun setOnTrackChangedListener(listener: ((trackName: String?) -> Unit)?) {
        this.onTrackChangedListener = listener
    }
    
    /**
     * Set listener for music loading errors (for toast notifications)
     */
    fun setOnMusicErrorListener(listener: ((errorMessage: String) -> Unit)?) {
        this.onMusicErrorListener = listener
    }

    fun initialize() {
        if (musicPlayer == null) {
            musicPlayer = ExoPlayer.Builder(context).build().apply {
                // Don't set repeatMode or shuffle here - will be set when loading playlist
                volume = 0.5f 
                prepare()
                
                // Add listener for auto-advance on track completion and error recovery
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        // Prevent operations after release
                        if (musicPlayer == null) {
                            Timber.d("BackgroundMusic: Player released, ignoring state change")
                            return
                        }
                        if (playbackState == Player.STATE_ENDED) {
                            Timber.d("BackgroundMusic: Track ended, auto-advancing to next random track")
                            skipToNextRandomTrack()
                        }
                    }
                    
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Timber.e(error, "BackgroundMusic: ExoPlayer ERROR detected")
                        Timber.e("BackgroundMusic: Error code: ${error.errorCode}, name: ${error.errorCodeName}")
                        Timber.e("BackgroundMusic: Message: ${error.message}")
                        Timber.e("BackgroundMusic: Current track: $currentTrackPath")
                        
                        // Mark current file as failed to avoid replaying it
                        currentTrackPath?.let { path ->
                            failedFiles.add(path)
                            Timber.w("BackgroundMusic: Marked file as failed: $path (total failed: ${failedFiles.size})")
                        }
                        
                        // Attempt recovery by skipping to next track
                        scope.launch {
                            try {
                                delay(500) // Small delay to let error state settle
                                Timber.w("BackgroundMusic: Attempting auto-recovery - skipping to next track")
                                
                                withContext(Dispatchers.Main) {
                                    // Stop current playback
                                    musicPlayer?.stop()
                                }
                                
                                delay(200)
                                
                                // Skip to next track (excluding failed files)
                                skipToNextRandomTrack()
                                
                                Timber.i("BackgroundMusic: Auto-recovery successful - playing next track")
                                
                            } catch (e: Exception) {
                                Timber.e(e, "BackgroundMusic: Auto-recovery failed, attempting full reinitialization")
                                
                                // Last resort: reinitialize player
                                withContext(Dispatchers.Main) {
                                    try {
                                        this@BackgroundMusicManager.musicPlayer?.release()
                                        this@BackgroundMusicManager.musicPlayer = null
                                        this@BackgroundMusicManager.isPlaying = false
                                        
                                        // Reinitialize
                                        this@BackgroundMusicManager.initialize()
                                        
                                        // Notify user
                                        this@BackgroundMusicManager.onMusicErrorListener?.invoke("Музыка перезапущена после ошибки")
                                        
                                        Timber.i("BackgroundMusic: Player reinitialized after recovery failure")
                                        
                                    } catch (reinitError: Exception) {
                                        Timber.e(reinitError, "BackgroundMusic: Reinitialization failed - music playback disabled")
                                        this@BackgroundMusicManager.onMusicErrorListener?.invoke("Не удалось восстановить воспроизведение музыки")
                                    }
                                }
                            }
                        }
                    }
                })
            }
            Timber.d("BackgroundMusic: Player initialized with auto-advance and error recovery")
            
            // Start health check watchdog
            startHealthCheck()
        }
    }

    fun updateState(state: PlayerViewModel.PlayerState) {
        val resourceId = state.slideshowMusicResourceId
        val enableBackgroundMusic = state.enableSlideshowBackgroundMusic
        val slideshowActive = state.isSlideShowActive
        val paused = state.isPaused
        val currentFile = state.currentFile
        
        // CRITICAL: Only load playlist when slideshow is actually active or about to start
        // Prevents unnecessary network operations when just viewing images
        // Also skip loading in audio slideshow photo mode (user listens to their own audio)
        val shouldLoadPlaylist = slideshowActive && enableBackgroundMusic && resourceId != null && !isAudioSlideshowPhotoMode
        
        // 1. Check if playlist resource changed - load playlist data ONLY if slideshow is active
        if (shouldLoadPlaylist && resourceId != null && resourceId != currentMusicResourceId) {
             currentMusicResourceId = resourceId
             loadAndSetPlaylist(resourceId) // Safe: resourceId is non-null here
        } else if (!shouldLoadPlaylist && currentMusicResourceId != null) {
             // Clear playlist when slideshow stops or music is disabled
             Timber.d("BackgroundMusic: Clearing playlist - slideshow stopped or music disabled")
             loadPlaylistJob?.cancel() // Cancel ongoing download
             loadPlaylistJob = null
             currentMusicResourceId = null
             musicPlayer?.clearMediaItems()
             currentPlaylist = emptyList()
             currentTrackName = null // Clear current track name
             // Notify UI to hide track name
             onTrackChangedListener?.invoke(null)
        }
        
        // 2. Determine if we should be playing
        val shouldPlay = if (enableBackgroundMusic && slideshowActive && !paused && resourceId != null && !isAudioSlideshowPhotoMode) {
            // Only play if current file does not have its own audio
            when (currentFile?.type) {
                MediaType.VIDEO, MediaType.AUDIO -> false
                else -> true
            }
        } else {
            false
        }

        // 3. Apply playback state
        if (shouldPlay) {
            // Lazy initialization only when needed
            if (musicPlayer == null) initialize()
            
            // Ensure playlist is set if player was just initialized or verified empty
            if (currentPlaylist.isNotEmpty() && musicPlayer?.mediaItemCount == 0) {
                 Timber.d("BackgroundMusic: Setting playlist for active player")
                 musicPlayer?.setMediaItems(currentPlaylist)
                 musicPlayer?.prepare()
            }
            
            if (musicPlayer?.playbackState == Player.STATE_IDLE) {
                 musicPlayer?.prepare()
            }
            
            if (!isPlaying) {
                musicPlayer?.play()
                isPlaying = true
                // Show track name when music resumes (e.g., after video ends)
                onTrackChangedListener?.invoke(currentTrackName)
                Timber.d("BackgroundMusic: Started/Resumed, showing track: $currentTrackName")
            }
        } else if (isPlaying) {
            musicPlayer?.pause()
            isPlaying = false
            // Hide track name when music stops
            onTrackChangedListener?.invoke(null)
            Timber.d("BackgroundMusic: Paused and track name hidden")
        }
    }
    
    private fun loadAndSetPlaylist(resourceId: Long) {
        loadPlaylistJob?.cancel()
        loadPlaylistJob = scope.launch {
            try {
                Timber.d("BackgroundMusic: Loading playlist for resource $resourceId")
                val resource = resourceRepository.getResourceById(resourceId)
                if (resource == null) {
                    Timber.w("BackgroundMusic: Resource not found: $resourceId")
                    withContext(Dispatchers.Main) {
                        onMusicErrorListener?.invoke("Ресурс с музыкой недоступен")
                    }
                    return@launch
                }
                
                // Get audio files (default sort, let ExoPlayer shuffle)
                val files = getMediaFilesUseCase(
                     resource = resource,
                     sortMode = SortMode.NAME_ASC,
                     showHiddenFiles = false
                ).first()
                
                val audioFiles = files.filter { it.type == MediaType.AUDIO }
                
                if (audioFiles.isNotEmpty()) {
                    // Store available files for skip functionality
                    availableAudioFiles = audioFiles
                    
                    // Determine adaptive repeat mode
                    val isSingleFile = audioFiles.size == 1
                    
                    // CRITICAL FIX: Select ONE random file instead of loading entire playlist
                    val randomFile = audioFiles.random()
                    currentTrackPath = randomFile.path
                    Timber.d("BackgroundMusic: Selected random file: ${randomFile.name} from ${audioFiles.size} files")
                    
                    // Check if job was cancelled before starting network operation
                    if (!coroutineContext.isActive) {
                        Timber.d("BackgroundMusic: Job cancelled before download")
                        return@launch
                    }
                    
                    val mediaItem = try {
                        val playableUri = if (randomFile.path.startsWith("smb:", ignoreCase = true) ||
                                                randomFile.path.startsWith("sftp:", ignoreCase = true) ||
                                                randomFile.path.startsWith("ftp:", ignoreCase = true)) {
                            // Network file - download to cache
                            val cacheFile = fileCache.getCacheFile(randomFile.path, randomFile.size)
                            if (!cacheFile.exists() || cacheFile.length() != randomFile.size) {
                                val success = downloadNetworkFileUseCase.execute(
                                    remotePath = randomFile.path,
                                    targetFile = cacheFile,
                                    progressCallback = null
                                )
                                if (!success) {
                                    Timber.w("BackgroundMusic: Failed to download ${randomFile.name}")
                                }
                            }
                            if (cacheFile.exists()) {
                                val result = Uri.fromFile(cacheFile)
                                Timber.d("BackgroundMusic: Mapped network file ${randomFile.name} to $result")
                                result
                            } else {
                                null
                            }
                        } else {
                            // Local file - use as is
                            val result = Uri.parse(randomFile.path)
                            Timber.d("BackgroundMusic: Use local/other uri for ${randomFile.name}: $result")
                            result
                        }
                        
                        if (playableUri != null) {
                            MediaItem.fromUri(playableUri)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "BackgroundMusic: Failed to prepare ${randomFile.name}")
                        null
                    }
                    
                    if (mediaItem != null) {
                        // Extract track name without path and extension
                        val trackName = randomFile.name.substringBeforeLast(".")
                        currentTrackName = trackName // Save for resume display
                        
                        withContext(Dispatchers.Main) {
                            currentPlaylist = listOf(mediaItem)
                            // Only set to player if player exists
                            musicPlayer?.setMediaItem(mediaItem)
                            // Adaptive repeat mode: loop single file, auto-advance for multiple files
                            musicPlayer?.repeatMode = if (isSingleFile) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                            musicPlayer?.prepare()
                            Timber.d("BackgroundMusic: Set single track: ${randomFile.name} (${if (isSingleFile) "LOOP" else "AUTO-ADVANCE"})")
                            
                            // Notify UI to show track name
                            onTrackChangedListener?.invoke(trackName)
                        }
                    } else {
                        Timber.w("BackgroundMusic: Failed to prepare selected track")
                    }
                } else {
                    Timber.w("BackgroundMusic: No audio files found in resource")
                    withContext(Dispatchers.Main) {
                        onMusicErrorListener?.invoke("Нет музыкальных файлов")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "BackgroundMusic: Failed to load playlist")
            }
        }
    }

    /**
     * Skip to a different random track from the same music resource.
     * Called when user taps on the track name display.
     */
    fun skipToNextRandomTrack() {
        if (availableAudioFiles.isEmpty()) {
            Timber.d("BackgroundMusic: No audio files available for skip")
            return
        }
        
        val isSingleFile = availableAudioFiles.size == 1
        
        // Select a different random file (avoid same track and failed files)
        val candidateFiles = if (availableAudioFiles.size > 1 && currentTrackPath != null) {
            val filtered = availableAudioFiles.filter { 
                it.path != currentTrackPath && it.path !in failedFiles 
            }
            // If all files failed, clear failed list and try again
            if (filtered.isEmpty() && failedFiles.isNotEmpty()) {
                Timber.w("BackgroundMusic: All files marked as failed (${failedFiles.size}), clearing failed list")
                failedFiles.clear()
                availableAudioFiles.filter { it.path != currentTrackPath }
            } else {
                filtered
            }
        } else {
            availableAudioFiles.filter { it.path !in failedFiles }
        }
        
        val randomFile = candidateFiles.random()
        currentTrackPath = randomFile.path
        
        Timber.d("BackgroundMusic: Skipping to random track: ${randomFile.name}")
        
        loadPlaylistJob?.cancel()
        loadPlaylistJob = scope.launch {
            try {
                val mediaItem = prepareMediaItem(randomFile)
                
                if (mediaItem != null) {
                    val trackName = randomFile.name.substringBeforeLast(".")
                    currentTrackName = trackName // Save for resume display
                    
                    withContext(Dispatchers.Main) {
                        currentPlaylist = listOf(mediaItem)
                        musicPlayer?.setMediaItem(mediaItem)
                        // Adaptive repeat mode: loop single file, auto-advance for multiple files
                        musicPlayer?.repeatMode = if (isSingleFile) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                        musicPlayer?.prepare()
                        musicPlayer?.play()
                        isPlaying = true
                        Timber.d("BackgroundMusic: Now playing: ${randomFile.name} (${if (isSingleFile) "LOOP" else "AUTO-ADVANCE"})")
                        onTrackChangedListener?.invoke(trackName)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "BackgroundMusic: Failed to skip track")
            }
        }
    }
    
    /**
     * Prepare a media item from a MediaFile (handles network file download).
     */
    private suspend fun prepareMediaItem(file: com.sza.fastmediasorter.domain.model.MediaFile): MediaItem? {
        return try {
            val playableUri = if (file.path.startsWith("smb:", ignoreCase = true) ||
                                    file.path.startsWith("sftp:", ignoreCase = true) ||
                                    file.path.startsWith("ftp:", ignoreCase = true)) {
                // Network file - download to cache
                val cacheFile = fileCache.getCacheFile(file.path, file.size)
                if (!cacheFile.exists() || cacheFile.length() != file.size) {
                    val success = downloadNetworkFileUseCase.execute(
                        remotePath = file.path,
                        targetFile = cacheFile,
                        progressCallback = null
                    )
                    if (!success) {
                        Timber.w("BackgroundMusic: Failed to download ${file.name}")
                    }
                }
                if (cacheFile.exists()) {
                    Uri.fromFile(cacheFile)
                } else {
                    null
                }
            } else {
                // Local file - use as is
                Uri.parse(file.path)
            }
            
            if (playableUri != null) {
                MediaItem.fromUri(playableUri)
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "BackgroundMusic: Failed to prepare ${file.name}")
            null
        }
    }

    /**
     * Health check watchdog - monitors player state and auto-recovers if stuck.
     * Runs every 60 seconds while music should be playing.
     */
    private fun startHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = scope.launch {
            while (isActive) {
                delay(60_000) // Check every 60 seconds
                
                if (isPlaying) {
                    val player = musicPlayer
                    if (player == null) {
                        Timber.w("BackgroundMusic: Health check - player is null while isPlaying=true")
                        isPlaying = false
                        continue
                    }
                    
                    val playbackState = player.playbackState
                    val isActuallyPlaying = player.isPlaying
                    
                    if (playbackState == Player.STATE_IDLE && isPlaying) {
                        Timber.w("BackgroundMusic: Health check FAILED - player in IDLE state while should be playing")
                        Timber.w("BackgroundMusic: Attempting recovery via skip to next track")
                        
                        try {
                            skipToNextRandomTrack()
                        } catch (e: Exception) {
                            Timber.e(e, "BackgroundMusic: Health check recovery failed")
                        }
                    } else if (!isActuallyPlaying && playbackState != Player.STATE_BUFFERING) {
                        Timber.w("BackgroundMusic: Health check - player not playing (state: $playbackState)")
                        Timber.w("BackgroundMusic: Attempting to resume playback")
                        
                        withContext(Dispatchers.Main) {
                            try {
                                player.play()
                            } catch (e: Exception) {
                                Timber.e(e, "BackgroundMusic: Failed to resume playback")
                            }
                        }
                    } else {
                        Timber.d("BackgroundMusic: Health check OK - playbackState=$playbackState, isPlaying=$isActuallyPlaying")
                    }
                }
            }
        }
        Timber.d("BackgroundMusic: Health check watchdog started (60s interval)")
    }
    
    fun release() {
        healthCheckJob?.cancel()
        loadPlaylistJob?.cancel()
        musicPlayer?.release()
        musicPlayer = null
        isPlaying = false
        
        // Reset state to ensure fresh load next time
        currentMusicResourceId = null
        currentPlaylist = emptyList()
        availableAudioFiles = emptyList()
        currentTrackPath = null
        currentTrackName = null
        failedFiles.clear()
        
        Timber.d("BackgroundMusic: Released all resources")
    }
    
    fun setVolume(volume: Float) {
        musicPlayer?.volume = volume
    }
}
