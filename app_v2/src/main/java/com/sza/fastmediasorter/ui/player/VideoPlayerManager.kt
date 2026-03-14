package com.sza.fastmediasorter.ui.player

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.PathUtils
import com.sza.fastmediasorter.data.cloud.datasource.CloudDataSourceFactory
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.network.datasource.SmbDataSourceFactory
import com.sza.fastmediasorter.data.network.datasource.SftpDataSourceFactory
import com.sza.fastmediasorter.data.network.datasource.FtpDataSourceFactory
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.ui.dialog.PlayerSettingsDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import java.io.File
import android.media.MediaPlayer
import android.view.SurfaceHolder
import android.graphics.Color
import android.graphics.Typeface
import androidx.media3.ui.CaptionStyleCompat
import com.sza.fastmediasorter.domain.models.TranslationFontSize
import com.sza.fastmediasorter.domain.models.TranslationFontFamily

/**
 * Manager for video playback using ExoPlayer.
 * Handles ExoPlayer lifecycle, network streaming, retry logic, and buffering.
 * 
 * Responsibilities:
 * - ExoPlayer creation and configuration
 * - Video playback (local, network: SMB/SFTP/FTP, cloud)
 * - Network stream retry logic with exponential backoff (EOF exceptions)
 * - Buffering and loading state management
 * - Audio format information extraction
 * - Connection throttling integration
 * - Playback position save/restore (audiobook mode)
 */
class VideoPlayerManager(
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val playerCallback: PlayerCallback,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val googleDriveClient: GoogleDriveRestClient,
    private val oneDriveClient: OneDriveRestClient,
    private val dropboxClient: DropboxClient,
    private val playbackPositionRepository: com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
) : DefaultLifecycleObserver {
    
    /**
     * Callback interface for player events
     */
    interface PlayerCallback {
        fun onPlaybackReady()
        fun onPlaybackError(error: Throwable)
        fun onBuffering(isBuffering: Boolean)
        fun onPlaybackStateChanged(isPlaying: Boolean)
        fun onPlaybackEnded()
        fun onAudioFormatChanged(format: AudioFormat?)
        fun showError(message: String)
        fun isActivityDestroyed(): Boolean
        fun showUnsupportedFormatError(message: String, filePath: String, isLocalFile: Boolean)
    }
    
    /**
     * Audio format information
     */
    data class AudioFormat(
        val codec: String,
        val sampleRate: Int,
        val channelCount: Int,
        val bitrate: Int
    )
    
    companion object {
        // Retry configuration (EOF exceptions)
        private const val MAX_EOF_RETRIES = 3
        
        // Buffer configuration (ms) - default for SMB/SFTP/FTP
        // Reduced to prevent OOM on 4K content (50-120s at 100Mbps = 625MB-1.5GB)
        private const val MIN_BUFFER_MS = 15000  // 15 seconds min
        private const val MAX_BUFFER_MS = 30000  // 30 seconds max
        private const val BUFFER_FOR_PLAYBACK_MS = 5000  // Wait 5s before starting
        private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 8000  // Buffer 8s after stall
        
        // Buffer configuration for Cloud (slower, need more buffering)
        private const val CLOUD_MIN_BUFFER_MS = 20000
        private const val CLOUD_MAX_BUFFER_MS = 45000
        private const val CLOUD_BUFFER_FOR_PLAYBACK_MS = 8000
        private const val CLOUD_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 12000
        
        // Playback position auto-save interval (5 seconds)
        private const val POSITION_SAVE_INTERVAL_MS = 5000L
    }
    
    private var exoPlayer: ExoPlayer? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayerView: PlayerView? = null
    private var isUsingMediaPlayer = false
    
    // Retry logic for EOF exceptions
    private var playbackRetryCount = 0
    private var lastPlaybackPosition = 0L
    private val retryHandler = Handler(Looper.getMainLooper())
    private var retryRunnable: Runnable? = null
    
    // Playback position saving
    private var currentFilePath: String? = null
    private var positionSaveRunnable: Runnable? = null
    private var lastSavedPosition: Long = -1L  // Track last saved position to avoid redundant saves
    
    // Playback health monitoring (detect "white noise" / stuck playback)
    private var playbackHealthCheckRunnable: Runnable? = null
    private var lastCheckedPosition = 0L
    private var playbackStuckCount = 0
    private val PLAYBACK_HEALTH_CHECK_DELAY_MS = 2000L  // Check after 2 seconds
    private val MAX_PLAYBACK_STUCK_COUNT = 2  // If stuck twice, fallback to MediaPlayer
    
    // Connection throttling tracking
    private var activeResourceKey: String? = null
    
    // Coroutine scope for async operations
    private val managerScope = CoroutineScope(Dispatchers.Main + Job())

    /**
     * Optional callback invoked with the first decoded video frame bitmap.
     * Only fires for local files (MediaMetadataRetriever does not support SMB/SFTP/FTP/cloud).
     * Consumed by PlayerMediaLoaderManager to feed DynamicBackgroundProcessor.
     */
    var onFirstFrameReady: ((android.graphics.Bitmap) -> Unit)? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playerCallback.isActivityDestroyed()) {
                Timber.w("VideoPlayerManager: Activity destroyed, ignoring state change")
                return
            }
            
            when (playbackState) {
                Player.STATE_READY -> {
                    Timber.d("VideoPlayerManager: Playback ready")
                    
                    // Log memory state AFTER video becomes ready
                    logMemoryStats("AFTER STATE_READY")
                    
                    playbackRetryCount = 0  // Reset retry counter
                    playerCallback.onBuffering(false)
                    playerCallback.onPlaybackReady()
                    
                    // Start playback health monitoring for audio files
                    // Check if playback is actually progressing (detect "white noise" issues)
                    startPlaybackHealthCheck()
                }
                Player.STATE_BUFFERING -> {
                    Timber.d("VideoPlayerManager: Buffering...")
                    playerCallback.onBuffering(true)
                    // Cancel health check during buffering
                    cancelPlaybackHealthCheck()
                }
                Player.STATE_ENDED -> {
                    Timber.d("VideoPlayerManager: Playback ended")
                    cancelPlaybackHealthCheck()
                    playerCallback.onPlaybackEnded()
                }
                Player.STATE_IDLE -> {
                    Timber.d("VideoPlayerManager: Player idle")
                    cancelPlaybackHealthCheck()
                }
            }
        }
        
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playerCallback.onPlaybackStateChanged(isPlaying)
        }
        
        override fun onPlayerError(error: PlaybackException) {
            val isEOFException = error.cause is java.io.EOFException ||
                                 error.cause?.cause is java.io.EOFException
            
            val isMediaCodecError = error.errorCode >= 4000 && error.errorCode < 5000 // Decoder errors
            
            val isSourceError = error.errorCode >= 2000 && error.errorCode < 3000 // IO errors
            
            val details = error.cause?.message ?: error.message ?: "Unknown error"

            // Handle EOF exceptions with retry logic
            if (isEOFException && playbackRetryCount < MAX_EOF_RETRIES && !playerCallback.isActivityDestroyed()) {
                playbackRetryCount++
                lastPlaybackPosition = exoPlayer?.currentPosition ?: 0L
                
                Timber.w("VideoPlayerManager: EOFException, retry attempt $playbackRetryCount/$MAX_EOF_RETRIES, position=$lastPlaybackPosition")
                
                Toast.makeText(
                    context,
                    "Network stream interrupted, retrying... ($playbackRetryCount/$MAX_EOF_RETRIES)",
                    Toast.LENGTH_SHORT
                ).show()
                
                // Cancel previous retry
                retryRunnable?.let { retryHandler.removeCallbacks(it) }
                
                // Retry after 1 second
                retryRunnable = Runnable {
                    if (!playerCallback.isActivityDestroyed()) {
                        Timber.d("VideoPlayerManager: Retrying playback from position $lastPlaybackPosition")
                        retryPlayback()
                    }
                }
                retryHandler.postDelayed(retryRunnable!!, 1000)
                return
            } else if (isEOFException) {
                Timber.e("VideoPlayerManager: EOFException - max retries exceeded")
            }
            
            // Log MediaCodec errors as warnings (often recoverable)
            if (isMediaCodecError) {
                Timber.w("VideoPlayerManager: MediaCodec error (often recoverable) - errorCode=${error.errorCode}, message=${error.message}")
                Timber.w("VideoPlayerManager: Cause: ${error.cause?.javaClass?.simpleName}")
            } else {
                Timber.e(error, "VideoPlayerManager: Playback error - errorCode=${error.errorCode}")
            }
            
            if (playerCallback.isActivityDestroyed()) {
                return
            }
            
            // Try fallback to MediaPlayer if ExoPlayer fails
            // Don't fallback for network errors or file not found, only for format issues
            val isFormatError = error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                               error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
                               error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                               error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED

            if (isFormatError && currentFilePath != null && !isUsingMediaPlayer) {
                Timber.i("VideoPlayerManager: ExoPlayer failed with format error, trying MediaPlayer fallback...")
                // Run on main thread to be safe with UI/Player updates
                Handler(Looper.getMainLooper()).post {
                    playWithMediaPlayer(currentFilePath!!)
                }
                return
            }
            
            // Hide loading indicator
            playerCallback.onBuffering(false)
            
            // Auto-skip on any non-recoverable error (format, source, network, etc.)
            // Detailed error info is already logged via Timber above
            playerCallback.onPlaybackError(error)
        }

        override fun onRenderedFirstFrame() {
            Timber.d("VideoPlayerManager: onRenderedFirstFrame")
            val path = currentFilePath ?: return
            val callback = onFirstFrameReady ?: return
            // MediaMetadataRetriever only supports local files; skip for network/cloud sources
            if (path.startsWith("smb://") || path.startsWith("sftp://") ||
                path.startsWith("ftp://") || path.startsWith("cloud://")) {
                Timber.d("VideoPlayerManager: Skipping first-frame capture for network/cloud source")
                return
            }
            managerScope.launch(Dispatchers.IO) {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(path)
                        val bitmap = retriever.getFrameAtTime(
                            0L,
                            android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )
                        if (bitmap != null) {
                            Timber.d("VideoPlayerManager: First frame extracted (${bitmap.width}x${bitmap.height})")
                            withContext(Dispatchers.Main) { callback(bitmap) }
                        } else {
                            Timber.w("VideoPlayerManager: getFrameAtTime returned null for $path")
                        }
                    } finally {
                        retriever.release()
                    }
                } catch (e: Exception) {
                    Timber.w(e, "VideoPlayerManager: Failed to extract first frame from $path")
                }
            }
        }
    }
    
    init {
        lifecycle.addObserver(this)
    }
    
    /**
     * Set PlayerView for video rendering. Must be called before playback.
     */
    fun setPlayerView(playerView: PlayerView) {
        currentPlayerView = playerView
        Timber.d("VideoPlayerManager: PlayerView set")
    }
    
    /**
     * Create and configure ExoPlayer instance with standard buffers
     */
    fun createPlayer(playerView: PlayerView): ExoPlayer {
        releasePlayer()
        
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        
        // Configure audio attributes for proper audio/music playback
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        
        // P1-1: Enable SW decoder fallback so HW codec failures (e.g., VP8 on API 28) are
        // handled transparently without surface-mode errors or startup freezes.
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true) // handleAudioFocus=true
            .build()
        
        player.addListener(playerListener)
        playerView.player = player
        currentPlayerView = playerView
        exoPlayer = player
        
        Timber.d("VideoPlayerManager: ExoPlayer created")
        return player
    }
    
    /**
     * Play video from path - handles local, network (SMB/S/FTP), and cloud
     */
    fun playVideo(
        path: String, 
        resourceType: ResourceType, 
        credentialsId: String?,
        playWhenReady: Boolean = true,
        onComplete: () -> Unit = {}
    ) {
        Timber.d("VideoPlayerManager: playVideo - path=$path, type=$resourceType")
        
        // Log memory state BEFORE video loading
        logMemoryStats("BEFORE playVideo")
        
        // Store current file path for position saving
        currentFilePath = path
        lastSavedPosition = -1L  // Reset last saved position for new file
        
        // Stop previous position saving
        stopPositionSaving()
        
        managerScope.launch {
            try {
                // Restore saved position
                val savedPosition = playbackPositionRepository.getPosition(path)
                
                // Check if this is a format best suited for MediaPlayer (MIDI)
                // MediaPlayer only supports LOCAL files - cannot handle SMB/S/FTP/Cloud
                // MIDI: ExoPlayer doesn't support MIDI natively
                val isLocalFile = resourceType == ResourceType.LOCAL
                val shouldUseMediaPlayer = isLocalFile && (
                    path.endsWith(".mid", ignoreCase = true) || 
                    path.endsWith(".midi", ignoreCase = true)
                )
                
                if (shouldUseMediaPlayer) {
                    Timber.i("VideoPlayerManager: Using MediaPlayer for ${path.substringAfterLast('/')} (forced for this format)")
                    withContext(Dispatchers.Main) {
                        playWithMediaPlayer(path)
                    }
                    onComplete()
                    return@launch
                }

                when (resourceType) {
                    ResourceType.CLOUD -> playCloudVideo(path, playWhenReady)
                    ResourceType.SMB -> playSmbVideo(path, credentialsId, playWhenReady)
                    ResourceType.SFTP -> playSftpVideo(path, credentialsId, playWhenReady)
                    ResourceType.FTP -> playFtpVideo(path, credentialsId, playWhenReady)
                    ResourceType.LOCAL -> playLocalVideoInternal(path, playWhenReady)
                }
                
                // Restore position if saved (after media is loaded)
                if (savedPosition != null && savedPosition > 0 && !isUsingMediaPlayer) {
                    withContext(Dispatchers.Main) {
                        exoPlayer?.seekTo(savedPosition)
                        Timber.d("VideoPlayerManager: Restored playback position: ${savedPosition}ms")
                        
                        // Show toast notification
                        Toast.makeText(
                            context,
                            context.getString(R.string.playback_resumed_from, formatTime(savedPosition)),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                
                // Start auto-saving position
                startPositionSaving()
                
                onComplete()
            } catch (e: Exception) {
                Timber.e(e, "VideoPlayerManager: Failed to play video")
                playerCallback.onBuffering(false)
                playerCallback.showError("Failed to play video: ${e.message}")
            }
        }
    }
    
    /**
     * Determine MIME type from file path extension
     */
    private fun getMimeTypeFromPath(path: String): String? {
        // Cloud URIs (cloud://provider/fileId) have no extension - skip detection
        if (path.startsWith("cloud://")) return null
        // Extract filename from URI path (remove query parameters and fragments)
        val cleanPath = path.substringBefore('?').substringBefore('#')
        val extension = cleanPath.substringAfterLast('.', "").lowercase()
        
        Timber.d("VideoPlayerManager.getMimeTypeFromPath: path=$path, extension=$extension")
        
        return when (extension) {
            "mp3" -> MimeTypes.AUDIO_MPEG
            "m4a", "aac", "adts" -> MimeTypes.AUDIO_AAC
            "flac" -> MimeTypes.AUDIO_FLAC
            "ogg", "oga" -> MimeTypes.AUDIO_OGG
            "opus" -> MimeTypes.AUDIO_OPUS
            "amr" -> MimeTypes.AUDIO_AMR_NB
            "awb" -> MimeTypes.AUDIO_AMR_WB
            "ac3" -> MimeTypes.AUDIO_AC3
            "ec3" -> MimeTypes.AUDIO_E_AC3
            "ac4" -> MimeTypes.AUDIO_AC4
            "mka" -> MimeTypes.AUDIO_MATROSKA
            "alac", "caf" -> MimeTypes.AUDIO_ALAC
            "mid", "midi" -> MimeTypes.AUDIO_MIDI // Note: requires extension decoder usually
            "mp4", "m4v" -> MimeTypes.VIDEO_MP4
            "webm" -> MimeTypes.VIDEO_WEBM
            "mkv" -> MimeTypes.VIDEO_MATROSKA
            "avi" -> MimeTypes.VIDEO_AVI
            "ts", "m2ts", "mts" -> MimeTypes.VIDEO_MP2T
            "ogv" -> MimeTypes.VIDEO_OGG
            "vob", "mpg", "mpeg", "m2v" -> MimeTypes.VIDEO_MPEG2
            "divx" -> MimeTypes.VIDEO_DIVX
            "flv" -> "video/x-flv"  // FLV not natively supported by ExoPlayer
            else -> {
                Timber.w("VideoPlayerManager.getMimeTypeFromPath: Unknown extension '$extension' for path: $path")
                null
            }
        }
    }

    /**
     * Create MediaItem with explicit MIME type if detectable
     */
    private fun createMediaItem(uri: String, path: String): MediaItem {
        val mimeType = getMimeTypeFromPath(path)
        
        Timber.d("VideoPlayerManager.createMediaItem: uri=$uri, path=$path, mimeType=$mimeType")
        
        return if (mimeType != null) {
            val item = MediaItem.Builder()
                .setUri(uri)
                .setMimeType(mimeType)
                .build()
            Timber.i("VideoPlayerManager.createMediaItem: Created with explicit MIME type: $mimeType")
            item
        } else {
            Timber.w("VideoPlayerManager.createMediaItem: No MIME type detected, using auto-detection")
            MediaItem.fromUri(uri)
        }
    }

    /**
     * Play local video file
     */
    fun playLocalVideo(path: String, playWhenReady: Boolean = true) {
        managerScope.launch {
            playLocalVideoInternal(path, playWhenReady)
        }
    }

    private suspend fun playLocalVideoInternal(path: String, playWhenReady: Boolean = true) {
        val normalizedPath = normalizeLocalPath(path)
        Timber.d("VideoPlayerManager: Playing local video - path=$path, normalizedPath=$normalizedPath")
        
        // Validate file exists for local files (IO thread to avoid StrictMode DiskReadViolation)
        if (!normalizedPath.startsWith("content://")) {
            val file = File(normalizedPath)
            val fileCheck = withContext(Dispatchers.IO) {
                Triple(file.exists(), file.canRead(), if (file.exists()) file.length() else 0L)
            }

            if (!fileCheck.first) {
                Timber.e("VideoPlayerManager: Local file does not exist: $normalizedPath (originalPath=$path)")
                playerCallback.showError(context.getString(R.string.file_not_found_name, file.name))
                return
            }
            if (!fileCheck.second) {
                // File exists but not readable — try content URI fallback (Android 11+ scoped storage)
                val contentUri = resolveContentUriForPath(normalizedPath)
                if (contentUri != null) {
                    Timber.i("VideoPlayerManager: File not readable via path, using content URI fallback: $contentUri")
                    playLocalVideoInternal(contentUri, playWhenReady)
                    return
                }
                Timber.e("VideoPlayerManager: Cannot read local file: $normalizedPath (originalPath=$path)")
                playerCallback.showError(context.getString(R.string.cannot_read_file, file.name))
                return
            }
            Timber.d("VideoPlayerManager: File exists and readable: ${file.name}, size=${fileCheck.third} bytes")
        }
        
        if (exoPlayer == null && currentPlayerView != null) {
            createPlayer(currentPlayerView!!)
        }
        
        exoPlayer?.apply {
            val uri = if (normalizedPath.startsWith("content://")) {
                normalizedPath
            } else {
                File(normalizedPath).toURI().toString()
            }
            
            Timber.d("VideoPlayerManager: Setting media item for URI: $uri")
            setMediaItem(createMediaItem(uri, normalizedPath))
            prepare()
            this.playWhenReady = playWhenReady
        }
        
        Timber.d("VideoPlayerManager: Local video setup complete")
    }

    private suspend fun resolveContentUriForPath(filePath: String): String? = withContext(Dispatchers.IO) {
        try {
            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(MediaStore.Files.FileColumns._ID)
            val selection = "${MediaStore.Files.FileColumns.DATA} = ?"
            val selectionArgs = arrayOf(filePath)
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    ContentUris.withAppendedId(uri, id).toString()
                } else null
            }
        } catch (e: Exception) {
            Timber.w(e, "VideoPlayerManager: Failed to resolve content URI for $filePath")
            null
        }
    }

    private fun normalizeLocalPath(path: String): String {
        if (path.startsWith("content://")) {
            return path
        }

        if (path.startsWith("file:", ignoreCase = true)) {
            return try {
                Uri.parse(path).path?.takeIf { it.isNotBlank() } ?: path
            } catch (e: Exception) {
                Timber.w(e, "VideoPlayerManager: Failed to normalize file URI, using original path")
                path
            }
        }

        return path
    }
    
    /**
     * Retry playback after error - restores position
     */
    private fun retryPlayback() {
        exoPlayer?.seekTo(lastPlaybackPosition)
        exoPlayer?.prepare()
        exoPlayer?.play()
    }
    
    /**
     * Get current audio format information
     */
    fun getAudioFormat(): AudioFormat? {
        val player = exoPlayer ?: return null
        
        // Extract audio format from current track selection
        val currentTracks = player.currentTracks
        val audioTrack = currentTracks.groups.firstOrNull { 
            it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO 
        }?.getTrackFormat(0) ?: return null
        
        return AudioFormat(
            codec = audioTrack.sampleMimeType ?: "Unknown",
            sampleRate = audioTrack.sampleRate,
            channelCount = audioTrack.channelCount,
            bitrate = audioTrack.bitrate
        )
    }
    
    /**
     * Release player resources
     */
    fun releasePlayer() {
        // Skip if already released (prevent redundant calls)
        if (exoPlayer == null && activeResourceKey == null) {
            Timber.d("VideoPlayerManager: releasePlayer() skipped - already released")
            return
        }
        
        Timber.d("VideoPlayerManager: releasePlayer() called. exoPlayer=${if(exoPlayer != null) "NOT_NULL" else "NULL"}, isActive=$activeResourceKey")
        exoPlayer?.let { player ->
            player.removeListener(playerListener)
            player.release()
            exoPlayer = null
            Timber.d("VideoPlayerManager: ExoPlayer released")
        }
        
        releaseMediaPlayer()
        
        // Cancel health monitoring
        cancelPlaybackHealthCheck()
        
        // Deactivate video player mode - resume thumbnail loading
        activeResourceKey?.let { key ->
            ConnectionThrottleManager.deactivateVideoPlayerMode(key)
            activeResourceKey = null
        }
        
        // Cancel any pending retries
        retryRunnable?.let { retryHandler.removeCallbacks(it) }
        retryRunnable = null
    }
    
    /**
     * Get current ExoPlayer instance
     */
    fun getPlayer(): ExoPlayer? = exoPlayer
    
    /**
     * Set repeat mode
     */
    fun setRepeatMode(repeatMode: Int) {
        exoPlayer?.repeatMode = repeatMode
    }
    
    /**
     * Pause playback
     */
    fun pause() {
        saveCurrentPosition()
        if (isUsingMediaPlayer) {
            if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
        } else {
            exoPlayer?.pause()
        }
    }
    
    /**
     * Play
     */
    fun play() {
        if (isUsingMediaPlayer) {
            mediaPlayer?.start()
        } else {
            exoPlayer?.play()
        }
    }
    
    /**
     * Set playback speed
     */
    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
    }

    /**
     * Apply UI PlayerSettingsDialog settings to ExoPlayer.
     */
    fun applyPlayerSettings(settings: PlayerSettingsDialog.PlayerSettings, appLanguage: String) {
        val player = exoPlayer ?: return

        setPlaybackSpeed(settings.playbackSpeed)
        Timber.d("VideoPlayerManager: Set playback speed to ${settings.playbackSpeed}x")

        setRepeatMode(
            if (settings.repeatVideo) {
                Player.REPEAT_MODE_ONE
            } else {
                Player.REPEAT_MODE_OFF
            }
        )
        Timber.d("VideoPlayerManager: Set repeat mode to ${if (settings.repeatVideo) "ONE" else "OFF"}")

        applyTrackSelection(player, settings, appLanguage)
    }

    /**
     * Apply subtitle and audio track selection based on settings.
     */
    private fun applyTrackSelection(player: ExoPlayer, settings: PlayerSettingsDialog.PlayerSettings, appLanguage: String) {
        var paramsBuilder = player.trackSelectionParameters.buildUpon()

        val preferredAudioLang = when (settings.audioLanguage) {
            PlayerSettingsDialog.LanguageOption.DEFAULT -> appLanguage
            PlayerSettingsDialog.LanguageOption.ENGLISH -> "en"
            PlayerSettingsDialog.LanguageOption.RUSSIAN -> "ru"
            PlayerSettingsDialog.LanguageOption.UKRAINIAN -> "uk"
        }
        paramsBuilder = paramsBuilder.setPreferredAudioLanguage(preferredAudioLang)
        Timber.d("VideoPlayerManager: Set preferred audio language to $preferredAudioLang")

        if (settings.showSubtitles) {
            val preferredSubtitleLang = when (settings.subtitleLanguage) {
                PlayerSettingsDialog.LanguageOption.DEFAULT -> appLanguage
                PlayerSettingsDialog.LanguageOption.ENGLISH -> "en"
                PlayerSettingsDialog.LanguageOption.RUSSIAN -> "ru"
                PlayerSettingsDialog.LanguageOption.UKRAINIAN -> "uk"
            }
            paramsBuilder = paramsBuilder
                .setPreferredTextLanguage(preferredSubtitleLang)
                .setIgnoredTextSelectionFlags(0)
            Timber.d("VideoPlayerManager: Set preferred subtitle language to $preferredSubtitleLang")
        } else {
            paramsBuilder = paramsBuilder
                .setIgnoredTextSelectionFlags(
                    C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_AUTOSELECT or C.SELECTION_FLAG_FORCED
                )
            Timber.d("VideoPlayerManager: Subtitles disabled")
        }

        player.trackSelectionParameters = paramsBuilder.build()
    }
    
    /**
     * Apply subtitle text style (font size and family) from app settings.
     * Called when subtitles are enabled.
     */
    fun applySubtitleStyle(fontSize: TranslationFontSize, fontFamily: TranslationFontFamily) {
        val subtitleView = currentPlayerView?.subtitleView ?: run {
            Timber.w("VideoPlayerManager: No subtitle view available")
            return
        }
        
        // Calculate font size: base size (24sp) * multiplier
        // For AUTO, use fractionalTextSize (fraction of view height)
        val textSizeSp = if (fontSize == TranslationFontSize.AUTO) {
            // Use default ExoPlayer sizing (fraction of view height)
            subtitleView.setFractionalTextSize(0.0533f) // ~24sp on standard screen
            null
        } else {
            // Fixed text size based on multiplier
            val baseSizeSp = 24f
            baseSizeSp * fontSize.multiplier
        }
        
        // Get typeface for font family
        val typeface = when (fontFamily) {
            TranslationFontFamily.DEFAULT -> Typeface.DEFAULT
            TranslationFontFamily.SERIF -> Typeface.SERIF
            TranslationFontFamily.MONOSPACE -> Typeface.MONOSPACE
        }
        
        // Create caption style with custom font
        // Use default colors: white text on semi-transparent black background
        val captionStyle = CaptionStyleCompat(
            Color.WHITE,                    // foregroundColor
            Color.argb(200, 0, 0, 0),       // backgroundColor (semi-transparent black)
            Color.TRANSPARENT,              // windowColor
            CaptionStyleCompat.EDGE_TYPE_OUTLINE,  // edgeType
            Color.BLACK,                    // edgeColor
            typeface                        // typeface
        )
        
        subtitleView.setStyle(captionStyle)
        
        // Apply fixed text size if not AUTO
        if (textSizeSp != null) {
            subtitleView.setFixedTextSize(
                android.util.TypedValue.COMPLEX_UNIT_SP,
                textSizeSp
            )
        }
        
        Timber.d("VideoPlayerManager: Applied subtitle style - fontSize=${fontSize.name}, fontFamily=${fontFamily.name}")
    }

    // === Track Selection Methods ===

    /**
     * Track info for display in quick-switcher dialogs.
     */
    data class TrackInfo(
        val groupIndex: Int,
        val trackIndex: Int,
        val label: String,
        val isSelected: Boolean
    )

    /**
     * Get available audio tracks with labels.
     * Returns empty list if no multiple audio tracks available.
     */
    fun getAvailableAudioTracks(): List<TrackInfo> {
        val player = exoPlayer ?: return emptyList()
        val tracks = player.currentTracks
        val result = mutableListOf<TrackInfo>()
        var trackNumber = 1

        for ((groupIndex, group) in tracks.groups.withIndex()) {
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val lang = format.language?.uppercase() ?: ""
                val codec = format.sampleMimeType?.substringAfter("/") ?: ""
                val channels = when (format.channelCount) {
                    1 -> "Mono"
                    2 -> "Stereo"
                    6 -> "5.1"
                    8 -> "7.1"
                    else -> if (format.channelCount > 0) "${format.channelCount}ch" else ""
                }
                val parts = listOfNotNull(
                    lang.ifEmpty { null },
                    codec.ifEmpty { null },
                    channels.ifEmpty { null }
                )
                val label = if (parts.isNotEmpty()) {
                    "Track $trackNumber (${parts.joinToString(", ")})"
                } else {
                    "Track $trackNumber"
                }
                result.add(TrackInfo(groupIndex, trackIndex, label, group.isTrackSelected(trackIndex)))
                trackNumber++
            }
        }
        return result
    }

    /**
     * Get available subtitle tracks with labels.
     * Returns empty list if no subtitle tracks available.
     */
    fun getAvailableSubtitleTracks(): List<TrackInfo> {
        val player = exoPlayer ?: return emptyList()
        val tracks = player.currentTracks
        val result = mutableListOf<TrackInfo>()
        var trackNumber = 1

        for ((groupIndex, group) in tracks.groups.withIndex()) {
            if (group.type != C.TRACK_TYPE_TEXT) continue
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val lang = format.language?.let { java.util.Locale(it).displayLanguage } ?: ""
                val label = if (lang.isNotEmpty()) {
                    "$lang (Track $trackNumber)"
                } else {
                    "Track $trackNumber"
                }
                result.add(TrackInfo(groupIndex, trackIndex, label, group.isTrackSelected(trackIndex)))
                trackNumber++
            }
        }
        return result
    }

    /**
     * Select specific audio track by group and track index.
     */
    fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {
        val player = exoPlayer ?: return
        val tracks = player.currentTracks
        if (groupIndex >= tracks.groups.size) return

        val group = tracks.groups[groupIndex]
        val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex))
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(override)
            .build()
        Timber.d("VideoPlayerManager: Selected audio track group=$groupIndex, track=$trackIndex")
    }

    /**
     * Select specific subtitle track by group and track index.
     * Pass trackIndex = -1 to disable subtitles.
     */
    fun selectSubtitleTrack(groupIndex: Int, trackIndex: Int) {
        val player = exoPlayer ?: return

        if (trackIndex < 0) {
            // Disable subtitles
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            Timber.d("VideoPlayerManager: Subtitles disabled")
            return
        }

        val tracks = player.currentTracks
        if (groupIndex >= tracks.groups.size) return

        val group = tracks.groups[groupIndex]
        val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex))
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(override)
            .build()
        Timber.d("VideoPlayerManager: Selected subtitle track group=$groupIndex, track=$trackIndex")
    }

    /**
     * Check if current media has multiple audio tracks.
     */
    fun hasMultipleAudioTracks(): Boolean {
        return getAvailableAudioTracks().size > 1
    }

    /**
     * Check if current media has subtitle tracks.
     */
    fun hasSubtitleTracks(): Boolean {
        return getAvailableSubtitleTracks().isNotEmpty()
    }
    
    // === Protocol-specific playback methods ===
    
    /**
     * Play video from Google Drive (Cloud)
     */
    private fun playCloudVideo(path: String, playWhenReady: Boolean) {
        val fileId = path.substringAfterLast("/")
        if (fileId.isEmpty() || fileId == path) {
            Timber.e("VideoPlayerManager: Invalid cloud path, no fileId")
            playerCallback.showError("Invalid cloud file path")
            return
        }
        
        Timber.d("VideoPlayerManager: Playing cloud video - fileId=$fileId")
        releasePlayer()
        
        val clients = mapOf(
            "googledrive" to googleDriveClient,
            "onedrive" to oneDriveClient,
            "dropbox" to dropboxClient
        )
        val dataSourceFactory = CloudDataSourceFactory(clients)
        
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                CLOUD_MIN_BUFFER_MS,
                CLOUD_MAX_BUFFER_MS,
                CLOUD_BUFFER_FOR_PLAYBACK_MS,
                CLOUD_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        
        exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(dataSourceFactory as DataSource.Factory)
            )
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .build()
        
        exoPlayer?.addListener(playerListener)
        currentPlayerView?.player = exoPlayer
        
        val cloudUri = PathUtils.safeParseUri(path)
        val mediaItem = createMediaItem(cloudUri.toString(), path)
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = playWhenReady
        
        Timber.i("VideoPlayerManager: Cloud video setup complete - fileId=$fileId")
    }
    
    /**
     * Play video from SMB share
     */
    /**
     * Play video from SMB share
     */
    private suspend fun playSmbVideo(path: String, credentialsId: String?, playWhenReady: Boolean) {
        if (credentialsId == null) {
            Timber.e("VideoPlayerManager: No credentials for SMB")
            playerCallback.showError("No credentials found")
            return
        }
        
        val credentials = credentialsRepository.getByCredentialId(credentialsId)
        if (credentials == null) {
            Timber.e("VideoPlayerManager: Credentials not found in DB")
            playerCallback.showError("Credentials not found")
            return
        }
        
        Timber.d("VideoPlayerManager: Playing SMB video - server=${credentials.server}, path=$path")
        releasePlayer()
        
        // Parse path to extract correct share name (might differ from credentials default share)
        val pathInfo = com.sza.fastmediasorter.utils.SmbPathUtils.parseSmbPath(path)
        
        // Use share from path if available, otherwise fallback to credentials share
        val targetShare = pathInfo?.connectionInfo?.shareName?.ifEmpty { null } ?: credentials.shareName ?: ""
        val targetRemotePath = pathInfo?.remotePath ?: ""
        
        if (targetShare.isEmpty()) {
            Timber.e("VideoPlayerManager: Could not determine share name for SMB playback")
            playerCallback.showError("Invalid SMB path: missing share name")
            return
        }
        
        Timber.d("VideoPlayerManager: Resolved SMB target - share='$targetShare', remotePath='$targetRemotePath'")
        
        val connectionInfo = SmbConnectionInfo(
            server = credentials.server,
            shareName = targetShare,
            username = credentials.username,
            password = credentials.password,
            domain = credentials.domain,
            port = credentials.port
        )
        
        // Activate video player priority mode
        val resourceKey = "smb://${credentials.server}:${credentials.port}"
        activeResourceKey = resourceKey
        ConnectionThrottleManager.activateVideoPlayerMode(resourceKey)
        
        val dataSourceFactory = SmbDataSourceFactory(smbClient, connectionInfo)
        
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        
        exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(dataSourceFactory as DataSource.Factory)
            )
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .build()
        
        exoPlayer?.addListener(playerListener)
        currentPlayerView?.player = exoPlayer
        
        // Construct clean SMB URI for ExoPlayer/SmbDataSource
        // We need to pass the FULL path including share so SmbDataSource can verify/log it,
        // but SmbDataSource logic handles stripping share if needed.
        // Actually SmbDataSource expects: /share/path/to/file in the URI path.
        
        // Encode path segments (preserve # and special chars)
        val encodedPath = targetRemotePath.split("/")
            .joinToString("/") { segment ->
                Uri.encode(segment, "@")
            }
        
        // The URI path should start with /shareName/path/to/file
        val finalUriPath = "/$targetShare/$encodedPath"
        
        val smbUri = Uri.Builder()
            .scheme("smb")
            .authority(credentials.server)
            .encodedPath(finalUriPath)
            .build()
        
        Timber.d("VideoPlayerManager: Created SMB URI=$smbUri")
        
        val mediaItem = createMediaItem(smbUri.toString(), path)
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = playWhenReady
        
        Timber.i("VideoPlayerManager: SMB video setup complete")
    }
    
    /**
     * Play video from SFTP server
     */
    private suspend fun playSftpVideo(path: String, credentialsId: String?, playWhenReady: Boolean) {
        if (credentialsId == null) {
            Timber.e("VideoPlayerManager: No credentials for SFTP")
            playerCallback.showError("No credentials found")
            return
        }
        
        val credentials = credentialsRepository.getByCredentialId(credentialsId)
        if (credentials == null) {
            Timber.e("VideoPlayerManager: Credentials not found in DB")
            playerCallback.showError("Credentials not found")
            return
        }
        
        Timber.d("VideoPlayerManager: Playing SFTP video - server=${credentials.server}")
        releasePlayer()
        
        // Activate video player priority mode
        val resourceKey = "sftp://${credentials.server}:${credentials.port}"
        activeResourceKey = resourceKey
        ConnectionThrottleManager.activateVideoPlayerMode(resourceKey)
        
        val dataSourceFactory = SftpDataSourceFactory(
            sftpClient,
            credentials.server,
            credentials.port,
            credentials.username,
            credentials.password
        )
        
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        
        exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(dataSourceFactory as DataSource.Factory)
            )
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .build()
        
        exoPlayer?.addListener(playerListener)
        currentPlayerView?.player = exoPlayer
        
        // Construct SFTP URI with encoded path
        val rawUri = if (path.startsWith("sftp://")) {
            path
        } else {
            "sftp://${credentials.server}:${credentials.port}$path"
        }
        
        val parsedUri = PathUtils.safeParseUri(rawUri)
        val encodedPath = parsedUri.path?.split("/")
            ?.joinToString("/") { segment ->
                if (segment.isEmpty()) "" else Uri.encode(segment, "@")
            } ?: ""
        
        val sftpUri = Uri.Builder()
            .scheme("sftp")
            .authority(parsedUri.authority)
            .encodedPath(encodedPath)
            .build()
        
        Timber.d("VideoPlayerManager: SFTP URI=$sftpUri")
        
        val mediaItem = createMediaItem(sftpUri.toString(), path)
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = playWhenReady
        
        Timber.i("VideoPlayerManager: SFTP video setup complete")
    }
    
    /**
     * Play video from FTP server
     */
    private suspend fun playFtpVideo(path: String, credentialsId: String?, playWhenReady: Boolean) {
        if (credentialsId == null) {
            Timber.e("VideoPlayerManager: No credentials for FTP")
            playerCallback.showError("No credentials found")
            return
        }
        
        val credentials = credentialsRepository.getByCredentialId(credentialsId)
        if (credentials == null) {
            Timber.e("VideoPlayerManager: Credentials not found in DB")
            playerCallback.showError("Credentials not found")
            return
        }
        
        Timber.d("VideoPlayerManager: Playing FTP video - server=${credentials.server}")
        releasePlayer()
        
        // Activate video player priority mode
        val resourceKey = "ftp://${credentials.server}:${credentials.port}"
        activeResourceKey = resourceKey
        ConnectionThrottleManager.activateVideoPlayerMode(resourceKey)
        
        val dataSourceFactory = FtpDataSourceFactory(
            ftpClient,
            credentials.server,
            credentials.port,
            credentials.username,
            credentials.password
        )
        
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                2500,  // FTP: Lower initial buffer
                5000   // FTP: Lower rebuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        
        exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(dataSourceFactory as DataSource.Factory)
            )
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .build()
        
        exoPlayer?.addListener(playerListener)
        currentPlayerView?.player = exoPlayer
        
        // Construct FTP URI with encoded path
        val parsedUri = PathUtils.safeParseUri(path)
        val encodedPath = parsedUri.path?.split("/")
            ?.joinToString("/") { segment ->
                if (segment.isEmpty()) "" else Uri.encode(segment, "@")
            } ?: ""
        
        val ftpUri = Uri.Builder()
            .scheme("ftp")
            .authority(parsedUri.authority)
            .encodedPath(encodedPath)
            .build()
        
        Timber.d("VideoPlayerManager: FTP URI=$ftpUri")
        
        val mediaItem = createMediaItem(ftpUri.toString(), path)
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = playWhenReady
        
        Timber.i("VideoPlayerManager: FTP video setup complete")
    }
    
    // === Playback Position Management ===
    
    /**
     * Start auto-saving playback position every 5 seconds
     */
    private fun startPositionSaving() {
        stopPositionSaving()
        
        positionSaveRunnable = object : Runnable {
            override fun run() {
                saveCurrentPosition()
                retryHandler.postDelayed(this, POSITION_SAVE_INTERVAL_MS)
            }
        }
        
        retryHandler.postDelayed(positionSaveRunnable!!, POSITION_SAVE_INTERVAL_MS)
        Timber.d("VideoPlayerManager: Started position auto-save")
    }
    
    /**
     * Stop auto-saving playback position
     */
    private fun stopPositionSaving() {
        positionSaveRunnable?.let {
            retryHandler.removeCallbacks(it)
            positionSaveRunnable = null
            Timber.d("VideoPlayerManager: Stopped position auto-save")
        }
    }
    
    /**
     * Save current playback position to database
     */
    private fun saveCurrentPosition() {
        val path = currentFilePath ?: return
        val player = exoPlayer ?: return
        
        val position = player.currentPosition
        val duration = player.duration
        
        // Skip save if position hasn't changed (e.g., paused)
        if (position == lastSavedPosition) {
            return
        }
        
        if (duration > 0 && position >= 0) {
            lastSavedPosition = position
            managerScope.launch(Dispatchers.IO) {
                try {
                    playbackPositionRepository.savePosition(path, position, duration)
                } catch (e: Exception) {
                    Timber.e(e, "VideoPlayerManager: Failed to save position")
                }
            }
        }
    }
    
    /**
     * Seek forward by specified seconds
     */
    fun seekForward(seconds: Int) {
        exoPlayer?.let { player ->
            val newPosition = (player.currentPosition + seconds * 1000).coerceAtMost(player.duration)
            player.seekTo(newPosition)
            Timber.d("VideoPlayerManager: Seek forward ${seconds}s to ${newPosition}ms")
        }
    }
    
    /**
     * Seek backward by specified seconds
     */
    fun seekBackward(seconds: Int) {
        exoPlayer?.let { player ->
            val newPosition = (player.currentPosition - seconds * 1000).coerceAtLeast(0)
            player.seekTo(newPosition)
            Timber.d("VideoPlayerManager: Seek backward ${seconds}s to ${newPosition}ms")
        }
    }
    
    /**
     * Format time in milliseconds to MM:SS or HH:MM:SS
     */
    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }
    
    /**
     * Start monitoring playback health to detect "white noise" / stuck playback issues
     * This checks if playback position is actually progressing after STATE_READY
     */
    private fun startPlaybackHealthCheck() {
        // Cancel any existing check
        cancelPlaybackHealthCheck()
        
        // Only check for audio files (FLAC, AC3, etc.)
        val isAudioFile = currentFilePath?.let { path ->
            path.endsWith(".flac", ignoreCase = true) ||
            path.endsWith(".ac3", ignoreCase = true) ||
            path.endsWith(".eac3", ignoreCase = true) ||
            path.endsWith(".wv", ignoreCase = true)
        } ?: false
        
        if (!isAudioFile || isUsingMediaPlayer) {
            return
        }
        
        // Record initial position
        lastCheckedPosition = exoPlayer?.currentPosition ?: 0L
        
        playbackHealthCheckRunnable = Runnable {
            checkPlaybackHealth()
        }
        
        // Schedule check after delay
        retryHandler.postDelayed(playbackHealthCheckRunnable!!, PLAYBACK_HEALTH_CHECK_DELAY_MS)
        Timber.d("VideoPlayerManager: Started playback health monitoring for audio file")
    }
    
    /**
     * Check if playback is progressing or stuck
     */
    private fun checkPlaybackHealth() {
        val player = exoPlayer ?: return
        
        // Check if player is actually playing
        if (!player.isPlaying || player.playbackState != Player.STATE_READY) {
            Timber.d("VideoPlayerManager: Health check skipped - player not playing or not ready")
            return
        }
        
        val currentPosition = player.currentPosition
        val positionDelta = currentPosition - lastCheckedPosition
        
        Timber.d("VideoPlayerManager: Health check - position delta: ${positionDelta}ms (from ${lastCheckedPosition}ms to ${currentPosition}ms)")
        
        // If position hasn't changed significantly (< 500ms progress in 2 seconds)
        // this indicates playback is stuck (white noise scenario)
        if (positionDelta < 500) {
            playbackStuckCount++
            Timber.w("VideoPlayerManager: Playback appears stuck! Stuck count: $playbackStuckCount/$MAX_PLAYBACK_STUCK_COUNT")
            
            if (playbackStuckCount >= MAX_PLAYBACK_STUCK_COUNT && currentFilePath != null) {
                Timber.e("VideoPlayerManager: Playback confirmed stuck (white noise), falling back to MediaPlayer")
                
                // Show toast to user
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        "Audio playback issue detected, switching to alternative player...",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // Switch to MediaPlayer
                    playWithMediaPlayer(currentFilePath!!)
                }
                return
            }
            
            // Schedule another check
            lastCheckedPosition = currentPosition
            retryHandler.postDelayed(playbackHealthCheckRunnable!!, PLAYBACK_HEALTH_CHECK_DELAY_MS)
        } else {
            // Playback is healthy, reset stuck counter
            playbackStuckCount = 0
            Timber.d("VideoPlayerManager: Playback is healthy")
            cancelPlaybackHealthCheck()
        }
    }
    
    /**
     * Cancel playback health monitoring
     */
    private fun cancelPlaybackHealthCheck() {
        playbackHealthCheckRunnable?.let {
            retryHandler.removeCallbacks(it)
            playbackHealthCheckRunnable = null
            playbackStuckCount = 0
            Timber.d("VideoPlayerManager: Cancelled playback health monitoring")
        }
    }
    
    /**
     * Fallback player using Android MediaPlayer for formats ExoPlayer cannot handle
     */
    private fun playWithMediaPlayer(path: String) {
        try {
            // Cancel any health monitoring
            cancelPlaybackHealthCheck()
            
            isUsingMediaPlayer = true
            releaseMediaPlayer()
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                setOnPreparedListener {
                    playerCallback.onPlaybackReady()
                    playerCallback.onBuffering(false)
                    start()
                }
                setOnCompletionListener {
                    playerCallback.onPlaybackEnded()
                }
                setOnErrorListener { _, what, extra ->
                    Timber.e("MediaPlayer error: what=$what, extra=$extra, file=$path")
                    
                    // MediaPlayer error codes (what):
                    // MEDIA_ERROR_UNKNOWN = 1 - Unspecified media player error
                    // MEDIA_ERROR_SERVER_DIED = 100 - Media server died
                    //
                    // MediaPlayer extra error codes (extra):
                    // MEDIA_ERROR_IO = -1004 - File or network related I/O error
                    // MEDIA_ERROR_MALFORMED = -1007 - Bitstream is not conforming to spec
                    // MEDIA_ERROR_UNSUPPORTED = -1010 - Format not supported
                    // MEDIA_ERROR_TIMED_OUT = -110 - Operation timed out
                    
                    val errorMessage = buildString {
                        append("MediaPlayer fallback failed\n\n")
                        
                        // Decode 'what' error code
                        append("Error type: ")
                        when (what) {
                            1 -> append("Unknown error")
                            100 -> append("Media server died")
                            else -> append("Code $what")
                        }
                        append("\n\n")
                        
                        // Decode 'extra' error code (more specific)
                        when (extra) {
                            -1004 -> {
                                append("Issue: I/O error - file may be corrupted, incomplete, or inaccessible")
                                append("\n\nTroubleshooting:")
                                append("\n• Check if file is complete (not partially downloaded)")
                                append("\n• Verify file is not corrupted")
                                append("\n• For network files: check connection stability")
                            }
                            -1007 -> {
                                append("Issue: File format is malformed or corrupted")
                                append("\n\nThe file structure doesn't conform to media specifications.")
                                append("\n\nTroubleshooting:")
                                append("\n• File may be damaged or incomplete")
                                append("\n• Try re-downloading or re-encoding the file")
                                append("\n• Check if file was properly transferred")
                            }
                            -1010 -> {
                                append("Issue: Format not supported")
                                append("\n\nThis file format/codec is not supported by MediaPlayer.")
                                append("\n\nTroubleshooting:")
                                append("\n• Try opening in external media player")
                                append("\n• Consider converting to a standard format (MP4, H.264)")
                            }
                            -110 -> {
                                append("Issue: Operation timed out")
                                append("\n\nTroubleshooting:")
                                append("\n• Check network connection")
                                append("\n• Verify server is responsive")
                                append("\n• Try again later")
                            }
                            else -> {
                                append("Details: Extra error code: $extra")
                                append("\n\nFile may be corrupted, incomplete, or in unsupported format.")
                            }
                        }
                        
                        append("\n\nFile: ${path.substringAfterLast('/')}")
                    }
                    
                    playerCallback.showError(errorMessage)
                    true
                }
                prepareAsync()
            }
            
            playerCallback.onBuffering(true)
            Timber.i("VideoPlayerManager: Using MediaPlayer fallback for: $path")
        } catch (e: Exception) {
            Timber.e(e, "VideoPlayerManager: MediaPlayer fallback failed")
            playerCallback.showError("Failed to play with MediaPlayer: ${e.message}")
            isUsingMediaPlayer = false
        }
    }
    
    /**
     * Release MediaPlayer resources
     */
    private fun releaseMediaPlayer() {
        mediaPlayer?.apply {
            try {
                if (isPlaying) stop()
                release()
            } catch (e: Exception) {
                Timber.e(e, "Error releasing MediaPlayer")
            }
        }
        mediaPlayer = null
        isUsingMediaPlayer = false
    }
    
    // === Lifecycle callbacks ===
    
    override fun onPause(owner: LifecycleOwner) {
        // Save position before pausing
        pause()
        stopPositionSaving()
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        // Final position save
        saveCurrentPosition()
        stopPositionSaving()

        val playerToRelease = exoPlayer
        exoPlayer = null

        val mediaPlayerToRelease = mediaPlayer
        mediaPlayer = null
        isUsingMediaPlayer = false

        try {
            playerToRelease?.removeListener(playerListener)
        } catch (e: Exception) {
            Timber.w(e, "VideoPlayerManager: Failed to remove listener")
        }

        try {
            currentPlayerView?.player = null
        } catch (e: Exception) {
            Timber.w(e, "VideoPlayerManager: Failed to detach PlayerView")
        }

        cancelPlaybackHealthCheck()

        activeResourceKey?.let { key ->
            ConnectionThrottleManager.deactivateVideoPlayerMode(key)
            activeResourceKey = null
        }

        retryRunnable?.let { retryHandler.removeCallbacks(it) }
        retryRunnable = null

        // Release ExoPlayer on main thread to avoid IllegalStateException
        try {
            playerToRelease?.release()
        } catch (e: Exception) {
            Timber.e(e, "VideoPlayerManager: Error releasing ExoPlayer")
        }

        // Release MediaPlayer in background thread (safe)
        Thread {
            try {
                mediaPlayerToRelease?.apply {
                    try {
                        if (isPlaying) stop()
                    } catch (_: Exception) {
                    }
                    release()
                }
            } catch (e: Exception) {
                Timber.e(e, "VideoPlayerManager: Error releasing MediaPlayer")
            }
        }.start()

        lifecycle.removeObserver(this)
    }
    
    /**
     * Logs current memory usage for debugging memory leaks during video playback.
     * Tracks heap memory, native memory, and provides context for log filtering.
     * 
     * @param context Contextual description (e.g., "BEFORE playVideo", "AFTER first frame")
     */
    private fun logMemoryStats(context: String) {
        try {
            val runtime = Runtime.getRuntime()
            
            // Heap memory stats (in MB)
            val totalMemory = runtime.totalMemory() / (1024 * 1024)
            val freeMemory = runtime.freeMemory() / (1024 * 1024)
            val maxMemory = runtime.maxMemory() / (1024 * 1024)
            val usedMemory = totalMemory - freeMemory
            val percentUsed = (usedMemory * 100) / maxMemory
            
            // Native memory (approximate)
            val nativeHeapSize = android.os.Debug.getNativeHeapSize() / (1024 * 1024)
            val nativeHeapAllocated = android.os.Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
            val nativeHeapFree = android.os.Debug.getNativeHeapFreeSize() / (1024 * 1024)
            
            Timber.i(
                "MEMORY_DEBUG [$context] | " +
                "Heap: ${usedMemory}MB/${maxMemory}MB (${percentUsed}%) | " +
                "Native: ${nativeHeapAllocated}MB/${nativeHeapSize}MB (free: ${nativeHeapFree}MB)"
            )
        } catch (e: Exception) {
            Timber.w(e, "MEMORY_DEBUG: Failed to log memory stats (VideoPlayer)")
        }
    }
}
