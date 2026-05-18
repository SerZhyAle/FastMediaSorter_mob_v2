package com.sza.fastmediasorter.ui.player.helpers

import android.net.Uri
import android.os.Handler
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.Player
import com.bumptech.glide.Glide
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.core.playback.RecentDecoderFailureTracker
import android.widget.Toast
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.CloudStorageClient
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.PlaybackOrderMode
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.ui.player.ImageLoadingManager
import com.sza.fastmediasorter.ui.player.AudioPlaybackService
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.File

/**
 * Coordinator for media loading operations in PlayerActivity.
 * Acts as a facade between PlayerActivity and specialized managers (ImageLoadingManager, VideoPlayerManager, etc.).
 * 
 * Responsibilities:
 * - Media type routing (image/video/audio/PDF/EPUB/text)
 * - UI visibility coordination for different media types
 * - Loading state management
 * - Coordination between image and video managers
 * - Image reload operations
 * 
 * This manager centralizes media loading logic to reduce PlayerActivity size while delegating
 * actual loading operations to specialized managers.
 */
class PlayerMediaLoaderManager(
    private val activity: PlayerActivity,
    private val binding: ActivityPlayerUnifiedBinding,
    private val viewModel: PlayerViewModel,
    private val imageLoadingManager: ImageLoadingManager,
    private val videoPlayerManager: VideoPlayerManager,
    private val textViewerManagerProvider: () -> TextViewerManager,
    private val exoPlayerControlsManager: ExoPlayerControlsManager,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val loadingIndicatorHandler: Handler,
    private val showLoadingIndicatorRunnable: Runnable,
    private val mediaFilesCacheManager: MediaFilesCacheManager,
    private val audioServiceController: AudioServiceController? = null,
    private val onAudioServicePlaybackChanged: (Boolean) -> Unit = {},
    private val onAudioServiceReady: () -> Unit = {},
    private val onAudioServicePlaybackEnded: () -> Unit = {},
    private val onAudioServicePlaybackError: (androidx.media3.common.PlaybackException) -> Unit = {},
    private val unifiedCache: UnifiedFileCache? = null,
    private val smbClient: SmbClient? = null,
    private val sftpClient: SftpClient? = null,
    private val ftpClient: FtpClient? = null,
    private val credentialsRepository: NetworkCredentialsRepository? = null,
    private val cloudClients: Map<String, CloudStorageClient> = emptyMap(),
    // S0172: used to read/restore SFTP audio position before playback starts
    private val playbackPositionRepository: PlaybackPositionRepository? = null,
    // S0213 Pillar A: cooldown tracker - short-circuits replay of paths that just failed to decode.
    private val decoderFailureTracker: RecentDecoderFailureTracker,
) {
    private val safeViews = PlayerBindingSafeViews(binding)
    private val viewVisibility = PlayerMediaViewVisibilityHelper(binding)
    private var servicePlaybackPlayer: Player? = null
    private var audioPrefetchJob: Job? = null
    private var audioPrefetchPath: String? = null
    private val servicePlaybackListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            onAudioServicePlaybackChanged(isPlaying)
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> onAudioServiceReady()
                Player.STATE_ENDED -> onAudioServicePlaybackEnded()
            }
        }
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            // Sync ViewModel index whenever the service switches track:
            // - REASON_AUTO: playlist auto-advances at end of track
            // - REASON_SEEK: user pressed NEXT/PREV in the system notification or lock screen
            // - REASON_REPEAT: repeat-mode wraparound
            val shouldSync = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            if (shouldSync) {
                val newIndex = audioServiceController?.player?.currentMediaItemIndex ?: -1
                if (newIndex >= 0) {
                    Timber.d("PlayerMediaLoaderManager: media item transition reason=$reason → serviceIndex=$newIndex")
                    viewModel.syncAudioServiceIndex(newIndex)
                }
                onAudioServiceReady()
            }
        }
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Timber.e(error, "PlayerMediaLoaderManager: Audio service playback error - code=${error.errorCode}")
            onAudioServicePlaybackError(error)
        }
    }

    init {
        // Wire first-frame extraction to dynamic background when video is opened
        videoPlayerManager.onFirstFrameReady = { bitmap, isPlaceholder ->
            imageLoadingManager.triggerVideoBackground(bitmap, isPlaceholder)
        }
    }

    companion object {
        private const val VIDEO_CONTROLS_AUTO_HIDE_DELAY_MS = 15000L
        // Max time to wait for network audio pre-cache before falling back to direct streaming.
        // SMBJ can hang for ~40s after Connection reset while closing the dead session -
        // this cap ensures the user doesn't wait indefinitely.
        private const val NETWORK_AUDIO_PRECACHE_TIMEOUT_MS = 20_000L
    }

    private val stereoDetector = com.sza.fastmediasorter.ui.player.StereoDetector()

    /**
     * Display image (delegates to ImageLoadingManager).
     * Runs still-image stereo detection first so that 3D / spherical images
     * get the correct crop or XR layer selection and the 3D tab reflects the mode.
     */
    fun displayImage(path: String) {
        Timber.d("PlayerMediaLoaderManager.displayImage: path=$path")

        val currentFile = viewModel.state.value.currentFile
        viewModel.resetStereoModeForNewFile(path)

        val detected = stereoDetector.detectForImage(
            path = path,
            width = currentFile?.width,
            height = currentFile?.height,
        )

        // Propagate to ViewModel so the 3D tab reflects the current mode
        // and VrStereoRenderer picks it up via stereoMode flow.
        if (detected != com.sza.fastmediasorter.domain.model.StereoMode.UNKNOWN &&
            detected != com.sza.fastmediasorter.domain.model.StereoMode.AUTO) {
            viewModel.setAutoDetectedStereoMode(detected)
            Timber.d("PlayerMediaLoaderManager: Image stereo detected → $detected")
        }

        // Tell the renderer which crop to apply (SBS left half, OU top half, or none).
        val effectiveMode = viewModel.stereoMode.value
        imageLoadingManager.setStereoMode(effectiveMode)

        imageLoadingManager.displayImage(path)
    }

    fun isCurrentAnimatedContent(): Boolean = imageLoadingManager.isCurrentAnimatedContent()

    fun isAnimatedPlaybackPaused(): Boolean = imageLoadingManager.isAnimatedPlaybackPaused()

    fun toggleAnimatedPlayback(): Boolean? = imageLoadingManager.toggleAnimatedPlayback()

    /**
     * Display text file (delegates to TextViewerManager)
     */
    fun displayText(mediaFile: MediaFile) {
        val resource = viewModel.state.value.resource
        Timber.d("PlayerMediaLoaderManager.displayText: file=${mediaFile.name}")
        imageLoadingManager.hideAnimatedBadge()
        textViewerManagerProvider().displayText(mediaFile, isWritable = resource?.isWritable == true)
    }

    /**
     * Play video or audio file with comprehensive media type routing
     */
    fun playVideo(path: String) {
        if (decoderFailureTracker.isInCooldown(path)) {
            val remainingSec = (decoderFailureTracker.cooldownRemainingMs(path) / 1000L).toInt()
            handleCooldownReentry(path, remainingSec)
            return
        }
        Timber.w("PlayerMediaLoaderManager.playVideo: START - path=$path")
        // Only cancel prefetch if the new file differs from what is being prefetched
        if (audioPrefetchPath != path) {
            cancelAudioPrefetch()
        }
        imageLoadingManager.hideAnimatedBadge()
        
        // Skip if activity is being destroyed
        if (activity.isFinishing || activity.isDestroyed) {
            Timber.d("PlayerMediaLoaderManager.playVideo: Activity is finishing/destroyed, skipping video playback")
            return
        }
        
        // Safety check: never try to play image files with ExoPlayer
        val lowerPath = path.lowercase()
        val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".heif", ".heic", ".avif")
        if (imageExtensions.any { lowerPath.endsWith(it) }) {
            Timber.w("PlayerMediaLoaderManager.playVideo: Detected image file (${lowerPath.substringAfterLast('.')}), redirecting to displayImage()")
            displayImage(path)
            return
        }
        
        Timber.d("PlayerMediaLoaderManager.playVideo: Hiding image views and setting up video UI")
        
        val currentFile = viewModel.state.value.currentFile
        val resource = viewModel.state.value.resource
        val isAudioFile = currentFile?.type == MediaType.AUDIO
        Timber.w(
            "PlayerMediaLoaderManager: playing file=" + (currentFile?.name ?: "<unknown>") +
            " type=" + (currentFile?.type ?: "?") +
            " size=" + ((currentFile?.size ?: 0L) / 1024L) + "KB" +
            " resource=" + (resource?.name ?: "<none>") +
            " isAudio=$isAudioFile"
        )

        // Hide all image-related views (keep audio cover art for audio files)
        hideImageViews(keepAudioCover = isAudioFile)

        // Cancel stale Glide image loads and clear dynamic background.
        // This prevents in-flight Glide callbacks from re-showing the previous image's
        // blurred background (stripes) over the video player pillarbox areas.
        imageLoadingManager.clearForVideoTransition()

        // Hide text viewer controls
        hideTextViewerControls()
        
        // Hide PDF controls
        hidePdfViewerControls()
        
        // Hide EPUB controls
        hideEpubViewerControls()
        
        // Show video player
        binding.playerView.isVisible = true
        
        // Configure PlayerView based on media type
        configurePlayerViewForMediaType(isAudioFile, currentFile)
        
        // Schedule loading indicator to show after 1 second
        loadingIndicatorHandler.postDelayed(showLoadingIndicatorRunnable, 1000)
        
        // Route audio to persistent playback service when enabled
        val isPersistentAudioEnabled = viewModel.state.value.enablePersistentAudioPlayback
            && BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK
        if (isAudioFile && isPersistentAudioEnabled && audioServiceController != null) {
            Timber.d("PlayerMediaLoaderManager.playVideo: routing AUDIO through AudioPlaybackService")
            playAudioViaService(path)
            return
        }

        // Stop service if switching away from audio to non-audio
        if (!isAudioFile && isServiceAudioActive) {
            Timber.d("PlayerMediaLoaderManager.playVideo: switching from audio to non-audio, stopping service")
            audioServiceController?.player?.stop()
            unbindServicePlaybackListener()
            binding.playerView.player = null
        }

        unbindServicePlaybackListener()
        onAudioServicePlaybackChanged(false)
        
        // Determine resource type from path prefix (for Favorites with mixed sources)
        val actualResourceType = determineResourceType(path, resource?.type)
        
        // Delegate to VideoPlayerManager
        playVideoWithResourceType(path, actualResourceType, currentFile, resource)
        
        Timber.d("PlayerMediaLoaderManager.playVideo: END")
    }

    /**
     * Route audio playback through AudioPlaybackService.
     * Local files: builds playlist from all local audio files.
     * Network files (SMB/SFTP/FTP): pre-caches current file to local storage, then plays via service.
     * Cloud files: pre-caches current file to local storage via CloudStorageClient, then plays via service.
     */
    private fun playAudioViaService(path: String) {
        val controller = audioServiceController ?: return
        val resourceType = determineResourceType(path, null)

        if (resourceType == ResourceType.LOCAL) {
            playLocalAudioViaService(path, controller)
            return
        }

        // Cloud audio (Google Drive / OneDrive / Dropbox) - pre-cache current file, then play via service
        if (resourceType == ResourceType.CLOUD) {
            val currentFile = viewModel.state.value.currentFile
            if (currentFile == null) {
                Timber.w("playAudioViaService: currentFile is null for cloud path")
                return
            }
            Timber.d("playAudioViaService: CLOUD audio - downloading ${currentFile.name} (${currentFile.size / 1024} KB) to cache")
            lifecycleScope.launch {
                // If prefetch is already running for this file, await it instead of re-downloading
                if (audioPrefetchPath == path && audioPrefetchJob?.isActive == true) {
                    Timber.d("playAudioViaService: awaiting existing cloud prefetch for $path")
                    audioPrefetchJob?.join()
                }
                val cachedFile = preCacheCloudAudio(path, currentFile)
                if (cachedFile != null) {
                    Timber.d("playAudioViaService: CLOUD pre-cache OK - playing via service: ${cachedFile.absolutePath}")
                    // Set original cloud path as the position key (ADR-3, S0173)
                    AudioPlaybackService.currentOriginalPath = path
                    val uri = Uri.fromFile(cachedFile)
                    val title = currentFile.name.substringBeforeLast('.')
                    // Read saved position and show resume toast
                    val savedPositionMs = playbackPositionRepository?.let { repo ->
                        PlaybackPositionRestorer.restoreAndNotify(
                            path = path,
                            repository = repo,
                            context = activity,
                            resumedFromStringResId = R.string.playback_resumed_from
                        )
                    } ?: 0L
                    controller.playAudioWithMetadata(uri, title) { player ->
                        if (savedPositionMs > 0L) player.seekTo(savedPositionMs)
                        activity.runOnUiThread { bindServicePlayerToView(player) }
                    }
                } else {
                    Timber.w("playAudioViaService: cloud pre-cache failed, falling back to in-app player")
                    val resource = viewModel.state.value.resource
                    playVideoWithResourceType(path, resourceType, currentFile, resource)
                }
            }
            return
        }

        // Network audio (SMB/SFTP/FTP) - await prefetch or download
        val currentFile = viewModel.state.value.currentFile
        val currentFileSize = currentFile?.size ?: 0L
        // Use credentialsId from current file's resource (same lookup path as VideoPlayerManager)
        val resource = viewModel.state.value.resource
        val credentialsId = if (resource?.id == -100L && currentFile?.resourceId != null) {
            // Favorites: credentials live on the original resource, resolved asynchronously below
            null
        } else {
            resource?.credentialsId
        }
        lifecycleScope.launch {
            // For Favorites, resolve credentialsId from the original resource
            val resolvedCredentialsId = if (resource?.id == -100L && currentFile?.resourceId != null) {
                viewModel.getCredentialsIdForResource(currentFile.resourceId)
            } else {
                credentialsId
            }
            // If prefetch is already running for this file, await it instead of re-downloading
            if (audioPrefetchPath == path && audioPrefetchJob?.isActive == true) {
                Timber.d("playAudioViaService: awaiting existing prefetch for $path")
                audioPrefetchJob?.join()
            }
            val cachedFile = preCacheNetworkAudio(path, currentFileSize, resolvedCredentialsId)
            if (cachedFile != null) {
                val uri = Uri.fromFile(cachedFile)
                val netTitle = viewModel.state.value.currentFile?.name?.substringBeforeLast('.') ?: cachedFile.nameWithoutExtension

                // Set original network path as position key (ADR-2, S0172)
                AudioPlaybackService.currentOriginalPath = path

                // Read saved position and show resume toast
                val savedPositionMs = playbackPositionRepository?.let { repo ->
                    PlaybackPositionRestorer.restoreAndNotify(
                        path = path,
                        repository = repo,
                        context = activity,
                        resumedFromStringResId = R.string.playback_resumed_from
                    )
                } ?: 0L

                controller.playAudioWithMetadata(uri, netTitle) { player ->
                    if (savedPositionMs > 0L) player.seekTo(savedPositionMs)
                    activity.runOnUiThread { bindServicePlayerToView(player) }
                }
            } else {
                Timber.w("playAudioViaService: pre-cache failed, falling back to in-app player")
                val currentFile = viewModel.state.value.currentFile
                val resource = viewModel.state.value.resource
                // Re-post loading indicator: the original 1-second delayed post from playVideo() has
                // long expired. ExoPlayer will now buffer the SMB stream directly, which can take
                // several seconds - show spinner so the user sees progress instead of a frozen screen.
                loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
                loadingIndicatorHandler.post(showLoadingIndicatorRunnable)
                playVideoWithResourceType(path, resourceType, currentFile, resource)
            }
        }
    }

    /** Play local audio file via service.
     *  If NowPlayingManager is available, uses playAudioPlaylistWithMetadata() which passes
     *  title metadata for all tracks and enables native ExoPlayer next/prev for N-item queues.
     *  Falls back to single-file playAudio() if NowPlayingManager is unavailable. */
    private fun playLocalAudioViaService(path: String, controller: AudioServiceController) {
        // S0172: local files are keyed by their local path inside VideoPlayerManager;
        // reset the companion field so the service doesn't accidentally save against a stale network key.
        AudioPlaybackService.currentOriginalPath = ""

        val allFiles = viewModel.state.value.files
        val currentIndex = allFiles.indexOfFirst { it.path == path }
        if (currentIndex < 0) {
            Timber.w("playLocalAudioViaService: file not found for path=$path")
            return
        }

        // Store context in service companion so notification contentIntent (tapping
        // the notification body) can reopen the exact player screen via MainActivity.
        val resource = viewModel.state.value.resource
        if (resource != null) {
            AudioPlaybackService.currentResourceId = resource.id
            AudioPlaybackService.currentInitialIndex = currentIndex
            Timber.d("playLocalAudioViaService: stored resourceId=${resource.id} index=$currentIndex in AudioPlaybackService")
        }

        val nowPlayingManager = activity.nowPlayingManager
        if (nowPlayingManager != null) {
            Timber.d("playLocalAudioViaService: using NowPlayingManager playlist path size=${allFiles.size}")
            nowPlayingManager.startPlayback(allFiles, currentIndex) { player ->
                activity.runOnUiThread { bindServicePlayerToView(player) }
            }
        } else {
            // Fallback: single-file mode (ForwardingPlayer handles next/prev via STATE_ENDED)
            val currentFile = allFiles[currentIndex]
            val uri = buildUriForMediaFile(currentFile)
            controller.playAudio(uri) { player ->
                activity.runOnUiThread { bindServicePlayerToView(player) }
            }
        }
    }

    /**
     * Download network audio file to local cache for background playback.
     * Returns cached File on success, null on failure.
     * @param credentialsId if non-null, used for direct credential lookup (same path as VideoPlayerManager).
     *   Fallback to server/share string lookup when null.
     */
    private suspend fun preCacheNetworkAudio(path: String, fileSize: Long = 0L, credentialsId: String? = null): File? = withContext(Dispatchers.IO) {

        // Check cache first
        if (fileSize > 0) {
            val cached = unifiedCache?.getCachedFile(path, fileSize)
            if (cached != null) {
                Timber.d("preCacheNetworkAudio: cache HIT for $path")
                return@withContext cached
            }
        }

        val destFile = unifiedCache?.getCacheFile(path, fileSize)
            ?: File.createTempFile("bg_audio_", ".tmp", activity.cacheDir)

        try {
            // Timeout guards against SMBJ hanging during connection cleanup after Connection reset.
            // Without it the caller blocks ~40s waiting for the dead session to close.
            withTimeout(NETWORK_AUDIO_PRECACHE_TIMEOUT_MS) {
                when {
                    path.startsWith("smb://") -> downloadSmbFull(path, destFile, credentialsId)
                    path.startsWith("sftp://") -> downloadSftpFull(path, destFile, credentialsId)
                    path.startsWith("ftp://") -> downloadFtpFull(path, destFile, credentialsId)
                    else -> null
                }
            }
        } catch (e: TimeoutCancellationException) {
            Timber.w("preCacheNetworkAudio: timed out after ${NETWORK_AUDIO_PRECACHE_TIMEOUT_MS}ms for $path - falling back to direct streaming")
            destFile.delete()
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Structured cancellation (e.g. lifecycleScope destroyed while downloading) - not an error.
            destFile.delete()
            throw e
        } catch (e: Exception) {
            Timber.e(e, "preCacheNetworkAudio: download failed for $path")
            destFile.delete()
            null
        }
    }

    private suspend fun downloadSmbFull(path: String, destFile: File, credentialsId: String? = null): File? {
        val client = smbClient ?: return null
        val normalized = path.replace('\\', '/').substringAfter("smb://")
        val parts = normalized.split("/", limit = 3)
        if (parts.size < 3) return null

        val serverWithPort = parts[0]
        val server = serverWithPort.substringBefore(':')
        val port = serverWithPort.substringAfter(':', "445").toIntOrNull() ?: 445
        val shareName = parts[1]
        val remotePath = parts[2]

        // Primary: use credentialsId from resource (same as VideoPlayerManager) - avoids server/share string mismatch
        var creds = if (credentialsId != null) credentialsRepository?.getByCredentialId(credentialsId) else null
        // Fallback: lookup by server + share string
        if (creds == null) creds = credentialsRepository?.getByServerAndShare(server, shareName)
        if (creds == null) {
            val sub = remotePath.trimStart('/').substringBefore('/')
            if (sub.isNotEmpty()) creds = credentialsRepository?.getByServerAndShare(server, "$shareName/$sub")
        }
        if (creds == null) { Timber.e("preCacheNetworkAudio: no SMB credentials for $server/$shareName (credentialsId=$credentialsId)"); return null }

        val connInfo = SmbConnectionInfo(
            server = server, shareName = shareName,
            username = creds.username, password = creds.password,
            domain = creds.domain, port = port
        )
        return destFile.outputStream().use { out ->
            when (client.downloadFile(connInfo, remotePath, out)) {
                is SmbResult.Success -> destFile
                else -> { Timber.e("preCacheNetworkAudio: SMB download failed for $path"); null }
            }
        }
    }

    private suspend fun downloadSftpFull(path: String, destFile: File, credentialsId: String? = null): File? {
        val client = sftpClient ?: return null
        val withoutProtocol = path.substringAfter("sftp://")
        val pathStart = withoutProtocol.indexOf('/')
        if (pathStart == -1) return null

        val hostPort = withoutProtocol.substring(0, pathStart)
        val remotePath = withoutProtocol.substring(pathStart)
        val host = hostPort.substringBefore(':')
        val port = hostPort.substringAfter(':', "22").toIntOrNull() ?: 22

        // Primary: use credentialsId from resource - avoids host/port string mismatch
        var creds = if (credentialsId != null) credentialsRepository?.getByCredentialId(credentialsId) else null
        if (creds == null) creds = credentialsRepository?.getByTypeServerAndPort("SFTP", host, port)
        if (creds == null) {
            creds = credentialsRepository?.getCredentialsByHost(host)
            if (creds?.type != "SFTP") { Timber.e("preCacheNetworkAudio: no SFTP credentials for $host:$port (credentialsId=$credentialsId)"); return null }
        }

        val connInfo = SftpClient.SftpConnectionInfo(host = host, port = port, username = creds.username, password = creds.password)
        return destFile.outputStream().use { out ->
            val result = client.downloadFile(connInfo, remotePath, out)
            if (result.isSuccess) destFile else { Timber.e("preCacheNetworkAudio: SFTP download failed for $path"); null }
        }
    }

    private suspend fun downloadFtpFull(path: String, destFile: File, credentialsId: String? = null): File? {
        val client = ftpClient ?: return null
        val withoutProtocol = path.substringAfter("ftp://")
        val pathStart = withoutProtocol.indexOf('/')
        if (pathStart == -1) return null

        val hostPort = withoutProtocol.substring(0, pathStart)
        val remotePath = withoutProtocol.substring(pathStart)
        val host = hostPort.substringBefore(':')
        val port = hostPort.substringAfter(':', "21").toIntOrNull() ?: 21

        // Primary: use credentialsId from resource - avoids host/port string mismatch
        var creds = if (credentialsId != null) credentialsRepository?.getByCredentialId(credentialsId) else null
        if (creds == null) creds = credentialsRepository?.getByTypeServerAndPort("FTP", host, port)
        if (creds == null) {
            creds = credentialsRepository?.getCredentialsByHost(host)
            if (creds?.type != "FTP") { Timber.e("preCacheNetworkAudio: no FTP credentials for $host:$port (credentialsId=$credentialsId)"); return null }
        }

        client.connect(host, port, creds.username, creds.password)
        return destFile.outputStream().use { out ->
            val result = client.downloadFile(remotePath, out)
            if (result.isSuccess) destFile else { Timber.e("preCacheNetworkAudio: FTP download failed for $path"); null }
        }
    }

    /**
     * Download cloud audio file to UnifiedFileCache.
     * Parses cloud:// path to determine provider and fileId, then downloads via CloudStorageClient.
     */
    private suspend fun preCacheCloudAudio(path: String, mediaFile: MediaFile): File? = withContext(Dispatchers.IO) {
        val fileSize = mediaFile.size

        if (fileSize > 0) {
            val cached = unifiedCache?.getCachedFile(path, fileSize)
            if (cached != null) {
                Timber.d("preCacheCloudAudio: cache HIT for $path")
                return@withContext cached
            }
        }

        val destFile = unifiedCache?.getCacheFile(path, fileSize)
            ?: File.createTempFile("bg_audio_cloud_", ".tmp", activity.cacheDir)

        try {
            // Parse cloud path: cloud://provider/folderId/fileId
            val withoutProtocol = path.removePrefix("cloud://")
            val parts = withoutProtocol.split("/", limit = 3)
            if (parts.isEmpty()) return@withContext null

            val providerStr = parts[0].lowercase()
            val provider = when {
                providerStr.contains("googledrive") || providerStr.contains("google_drive") -> "googledrive"
                providerStr.contains("onedrive") -> "onedrive"
                providerStr.contains("dropbox") -> "dropbox"
                else -> providerStr
            }

            val client = cloudClients[provider]
            if (client == null) {
                Timber.w("preCacheCloudAudio: no cloud client for provider=$provider")
                return@withContext null
            }

            // Extract fileId - same logic as CloudPathParser
            val folderId = if (parts.size > 1) parts[1] else null
            val fileId = if (parts.size > 2) {
                when (provider) {
                    "dropbox" -> "/" + parts.drop(1).joinToString("/")
                    else -> {
                        val filename = parts[2].substringAfterLast('/')
                        if (folderId != null) "$folderId/$filename" else filename
                    }
                }
            } else {
                folderId ?: ""
            }

            destFile.outputStream().use { out ->
                val result = client.downloadFile(fileId, out, null)
                if (result is CloudResult.Success) {
                    Timber.d("preCacheCloudAudio: downloaded ${mediaFile.name} (${destFile.length() / 1024} KB)")
                    destFile
                } else {
                    Timber.e("preCacheCloudAudio: download failed for $path - $result")
                    destFile.delete()
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "preCacheCloudAudio: download failed for $path")
            destFile.delete()
            null
        }
    }

    private fun buildLocalUri(path: String): Uri {
        val parsed = Uri.parse(path)
        return if (parsed.scheme == null) Uri.fromFile(java.io.File(path)) else parsed
    }

    /**
     * Build playback URI for a local MediaFile.
     * Prefers contentUri (content:// from MediaStore) over file:// to avoid EACCES on
     * Android 10+ scoped storage when AudioPlaybackService opens files via FileDataSource.
     */
    private fun buildUriForMediaFile(mediaFile: com.sza.fastmediasorter.domain.model.MediaFile): Uri {
        val contentUri = mediaFile.contentUri
        if (!contentUri.isNullOrBlank()) return Uri.parse(contentUri)
        return buildLocalUri(mediaFile.path)
    }

    internal fun bindServicePlayerToView(player: Player) {
        bindServicePlaybackListener(player)
        binding.playerView.player = player
        loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
        binding.progressBar.isVisible = false
        activity.updateTrackButtonsVisibility()
        Timber.d("PlayerMediaLoaderManager: service player bound to PlayerView")
    }

    /**
     * Re-attach PlayerView to the service MediaController after Activity resumes.
     * Must be called in onResume() when service audio is active.
     */
    internal fun reattachServicePlayerToView() {
        val player = audioServiceController?.player ?: return
        if (isServiceAudioActive) {
            Timber.d("PlayerMediaLoaderManager: re-attaching service player to PlayerView after resume")
            bindServicePlayerToView(player)
        }
    }

    fun bindServicePlaybackListener(player: Player) {
        if (servicePlaybackPlayer !== player) {
            servicePlaybackPlayer?.removeListener(servicePlaybackListener)
            servicePlaybackPlayer = player
            servicePlaybackPlayer?.addListener(servicePlaybackListener)
        }
        onAudioServicePlaybackChanged(player.isPlaying)
    }

    private fun unbindServicePlaybackListener() {
        servicePlaybackPlayer?.removeListener(servicePlaybackListener)
        servicePlaybackPlayer = null
    }

    /** Whether audio is currently playing through the persistent playback service */
    val isServiceAudioActive: Boolean
        get() = audioServiceController?.isConnected == true
                && viewModel.state.value.enablePersistentAudioPlayback
                && viewModel.state.value.currentFile?.type == MediaType.AUDIO

    private fun syncVideoPlaybackOrderMode() {
        val repeatMode = if (viewModel.state.value.playbackOrderMode == PlaybackOrderMode.REPEAT_ONE) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
        videoPlayerManager.setRepeatMode(repeatMode)
    }

    /**
     * Play local video file (legacy method for compatibility)
     */
    fun playLocalVideo(path: String) {
        Timber.d("PlayerMediaLoaderManager.playLocalVideo: Delegating to VideoPlayerManager")
        videoPlayerManager.playLocalVideo(path, !viewModel.state.value.isPaused)
        syncVideoPlaybackOrderMode()
        exoPlayerControlsManager.updatePlaybackOrderButtonState()
    }

    /**
     * Reload current image after edit operation (rotation/flip/filter/adjustments).
     * Clears both memory and disk cache, updates MediaFilesCache, and reloads.
     */
    fun reloadCurrentImage() {
        val currentFile = viewModel.state.value.currentFile ?: return
        val resource = viewModel.state.value.resource
        
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                // Cancel any pending loading indicator
                loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
                if (!activity.isDestroyed) {
                    binding.progressBar.isVisible = false
                }
                
                // Clear memory cache for both views (immediate effect)
                val requestManager = Glide.get(activity)
                    .requestManagerRetriever
                    .get(activity)
                
                requestManager.clear(binding.imageView)
                requestManager.clear(binding.photoView)
                
                // Clear Glide disk cache for this specific file (in background)
                withContext(Dispatchers.IO) {
                    try {
                        // Clear all disk cache since we can't target specific key easily
                        // This ensures the edited file is reloaded fresh from network
                        Glide.get(activity).clearDiskCache()
                        Timber.d("PlayerMediaLoaderManager: Cleared Glide disk cache after image edit")
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to clear Glide disk cache")
                    }
                }
                
                // Get updated file info from ViewModel state (should have new size after refreshCurrentFileInfo)
                val updatedFile = viewModel.state.value.currentFile
                
                // Update MediaFilesCache with new file info (for BrowseActivity thumbnails)
                if (resource != null && updatedFile != null) {
                    mediaFilesCacheManager.updateFile(resource.id, currentFile.path, updatedFile)
                    Timber.d("PlayerMediaLoaderManager: Updated MediaFilesCache for edited file: ${currentFile.name}")
                }
                
                Timber.d("PlayerMediaLoaderManager: Reloading image after edit: ${currentFile.name}")
                
                // Reload image fresh from network
                displayImage(currentFile.path)
                
            } catch (e: Exception) {
                Timber.e(e, "PlayerMediaLoaderManager: Error reloading image after edit")
                activity.showError(activity.getString(R.string.msg_failed_reload_image), e)
            }
        }
    }

    /**
     * Preload adjacent images for faster navigation
     */
    fun preloadNextImageIfNeeded() {
        imageLoadingManager.preloadNextImageIfNeeded()
    }

    /**
     * Prefetch the next audio file into UnifiedFileCache.
     * Called when the current audio reaches STATE_READY (playing).
     * Only applies to network sources (SMB/SFTP/FTP) - local audio uses ExoPlayer playlist.
     */
    fun prefetchNextAudio() {
        val nextFile = viewModel.getNextAudioFile() ?: return
        val path = nextFile.path

        val isNetwork = path.startsWith("smb://") || path.startsWith("sftp://") || path.startsWith("ftp://")
        val isCloud = path.startsWith("cloud://")

        // Only prefetch network/cloud audio - local files use ExoPlayer playlist
        if (!isNetwork && !isCloud) return

        // Already cached?
        val cached = unifiedCache?.getCachedFile(path, nextFile.size)
        if (cached != null) {
            Timber.d("prefetchNextAudio: already cached - ${nextFile.name}")
            return
        }

        // Cancel previous prefetch if still running for a different file
        audioPrefetchJob?.cancel()
        audioPrefetchPath = path
        // Resolve credentialsId from the resource (or original resource for Favorites)
        val prefetchResource = viewModel.state.value.resource
        val prefetchCredentialsId = prefetchResource?.credentialsId
        audioPrefetchJob = lifecycleScope.launch {
            val resolvedPrefetchCredentialsId = if (prefetchResource?.id == -100L && nextFile.resourceId != null) {
                viewModel.getCredentialsIdForResource(nextFile.resourceId)
            } else {
                prefetchCredentialsId
            }
            Timber.d("prefetchNextAudio: START downloading ${nextFile.name} (${nextFile.size / 1024} KB)")
            val result = if (isCloud) preCacheCloudAudio(path, nextFile) else preCacheNetworkAudio(path, nextFile.size, resolvedPrefetchCredentialsId)
            if (result != null) {
                Timber.d("prefetchNextAudio: DONE - ${nextFile.name} cached")
            } else {
                Timber.w("prefetchNextAudio: FAILED - ${nextFile.name}")
            }
        }
    }

    /** Cancel any in-flight audio prefetch (e.g. on navigation or activity destroy). */
    fun cancelAudioPrefetch() {
        audioPrefetchJob?.cancel()
        audioPrefetchJob = null
        audioPrefetchPath = null
    }

    /**
     * Show audio file info overlay
     */
    fun showAudioFileInfo(file: MediaFile?) {
        imageLoadingManager.showAudioFileInfo(file)
    }

    /**
     * Update audio touch zones visibility based on current state
     */
    fun updateAudioTouchZonesVisibility() {
        val state = viewModel.state.value
        val isAudioFile = state.currentFile?.type == MediaType.AUDIO
        val isInFullscreenMode = !state.showCommandPanel && !state.showControls
        
        // NEVER show overlay for audio - audio uses ExoPlayer UI, not touch zones
        safeViews.audioTouchZonesOverlay.isVisible = false
        Timber.d("PlayerMediaLoaderManager.updateAudioTouchZonesVisibility: Overlay hidden - audio=$isAudioFile, fullscreen=$isInFullscreenMode")
    }

    /**
     * Update volume buttons visibility - show for audio and video files
     */
    fun updateVolumeButtonsVisibility() {
        val fileType = viewModel.state.value.currentFile?.type
        val isMediaWithSound = fileType == MediaType.AUDIO || fileType == MediaType.VIDEO
        binding.btnVolumeDown.isVisible = isMediaWithSound
        binding.btnVolumeUp.isVisible = isMediaWithSound
    }

    /**
     * Adjust touch zones for video/audio (disable in command panel mode)
     * 
     * CRITICAL: We now use TouchZoneGestureManager for ALL touch zone detection.
     * The View-based overlays (touchZonesOverlay, touchZones3Overlay) are ALWAYS hidden
     * to prevent conflicts with gesture-based zone detection.
     */
    fun adjustTouchZonesForVideo(isVideo: Boolean, useTouchZones: Boolean) {
        val currentFile = viewModel.state.value.currentFile
        val isEpubOrPdf = currentFile?.type == MediaType.EPUB || currentFile?.type == MediaType.PDF
        val isText = currentFile?.type == MediaType.TEXT
        
        // ALWAYS hide View-based touch zone overlays
        // TouchZoneGestureManager handles all touch zone detection via gesture listeners
        safeViews.touchZonesOverlay.isVisible = false
        safeViews.touchZones3Overlay.isVisible = false
        
        if (isVideo) {
            // For video/audio: View-based touch zones hidden - but gestures handled by PlayerGestureSetupManager
            Timber.d("PlayerMediaLoaderManager.adjustTouchZonesForVideo: View-based touch zones HIDDEN for video/audio (gestures active)")
        } else if (isEpubOrPdf || isText) {
            // For EPUB/PDF/TEXT: Touch zones disabled - document controls handle navigation
            Timber.d("PlayerMediaLoaderManager.adjustTouchZonesForVideo: Touch zones DISABLED for EPUB/PDF/TEXT (using document controls)")
        } else {
            // For images: Touch zones handled by TouchZoneGestureManager
            // - Command panel mode: 3-zone (20% left, 60% center, 20% right)
            // - Fullscreen mode: 9-zone grid
            Timber.d("PlayerMediaLoaderManager.adjustTouchZonesForVideo: Touch zones ${if (useTouchZones) "ENABLED" else "DISABLED"} for images (gesture-based)")
        }
    }

    // Private helper methods (view visibility extracted to PlayerMediaViewVisibilityHelper)

    private fun hideImageViews(keepAudioCover: Boolean = false) =
        viewVisibility.hideImageViews(keepAudioCover)

    private fun hideTextViewerControls() = viewVisibility.hideTextViewerControls()

    private fun hidePdfViewerControls() = viewVisibility.hidePdfViewerControls()

    private fun hideEpubViewerControls() = viewVisibility.hideEpubViewerControls()

    private fun configurePlayerViewForMediaType(isAudioFile: Boolean, currentFile: MediaFile?) {
        val exoContentFrame = binding.playerView.findViewById<View>(androidx.media3.ui.R.id.exo_content_frame)
        if (isAudioFile) {
            // For audio: always show controls, never hide
            // Use Integer.MAX_VALUE to prevent auto-hide (negative values don't work reliably)
            binding.playerView.controllerShowTimeoutMs = Int.MAX_VALUE
            // Force controls visible immediately - no tap required
            binding.playerView.showController()

            // Hide PlayerView's internal video/content layer for audio.
            // Otherwise its empty black TextureView sits above our external audio background views.
            exoContentFrame?.isVisible = false
            
            // Make PlayerView transparent so animation views behind it (lower z) are visible.
            // exo_bottom_bar has its own opaque background so controls remain readable.
            binding.playerView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.playerView.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            // Disable PlayerView's built-in artwork display - we use our own audioCoverArtView
            // This prevents duplicate cover art (one from PlayerView, one from audioCoverArtView)
            binding.playerView.setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_NEVER)
            binding.playerView.artworkDisplayMode = androidx.media3.ui.PlayerView.ARTWORK_DISPLAY_MODE_OFF
            
            // Show touch zones overlay for audio in fullscreen mode
            updateAudioTouchZonesVisibility()
            
            // Show audio file info
            showAudioFileInfo(currentFile)
        } else {
            // For video: auto-hide controls after 15 seconds
            binding.playerView.controllerShowTimeoutMs = VIDEO_CONTROLS_AUTO_HIDE_DELAY_MS.toInt()

            // Restore PlayerView's video/content layer for real video playback.
            exoContentFrame?.isVisible = true
            
            // Restore opaque background for video (prevents animation bleeding through)
            binding.playerView.setBackgroundColor(android.graphics.Color.BLACK)
            binding.playerView.setShutterBackgroundColor(android.graphics.Color.BLACK)
            
            // Re-enable artwork display for video (in case it was disabled for audio)
            binding.playerView.artworkDisplayMode = androidx.media3.ui.PlayerView.ARTWORK_DISPLAY_MODE_FIT
            
            // Hide touch zones overlay for video
            safeViews.audioTouchZonesOverlay.isVisible = false
            
            // Hide audio info overlay for video
            binding.audioInfoOverlay.isVisible = false
        }
    }

    private fun determineResourceType(path: String, defaultType: ResourceType?): ResourceType =
        viewVisibility.determineResourceType(path, defaultType)

    private fun playVideoWithResourceType(
        path: String,
        resourceType: ResourceType,
        currentFile: MediaFile?,
        resource: com.sza.fastmediasorter.domain.model.MediaResource?
    ) {
        if (currentFile != null && 
            (resourceType == ResourceType.SMB || resourceType == ResourceType.SFTP || 
             resourceType == ResourceType.FTP || resourceType == ResourceType.CLOUD)) {
            
            // For Favorites, get credentialsId from the file's original resource
            if (resource?.id == -100L && currentFile.resourceId != null) {
                // Launch coroutine to get credentials from original resource
                lifecycleScope.launch {
                    val resourceId = currentFile.resourceId
                    val credId = viewModel.getCredentialsIdForResource(resourceId)
                    Timber.d("PlayerMediaLoaderManager.playVideoWithResourceType: Favorites file, resolved credentialsId=$credId from resourceId=${currentFile.resourceId}")
                    
                    // Network or cloud playback via manager
                    videoPlayerManager.playVideo(
                        path = path,
                        resourceType = resourceType,
                        credentialsId = credId,
                        playWhenReady = !viewModel.state.value.isPaused,
                        onComplete = {
                            // Fresh player instances must inherit the persisted playback-order mode.
                            syncVideoPlaybackOrderMode()
                            exoPlayerControlsManager.updatePlaybackOrderButtonState()
                        }
                    )
                }
            } else {
                // Network or cloud playback via manager (non-Favorites)
                videoPlayerManager.playVideo(
                    path = path,
                    resourceType = resourceType,
                    credentialsId = resource?.credentialsId,
                    playWhenReady = !viewModel.state.value.isPaused,
                    onComplete = {
                        syncVideoPlaybackOrderMode()
                        exoPlayerControlsManager.updatePlaybackOrderButtonState()
                    }
                )
            }
        } else {
            // Local file - use playVideo() to enable position restore
            videoPlayerManager.playVideo(
                path = path,
                resourceType = ResourceType.LOCAL,
                credentialsId = null,
                playWhenReady = !viewModel.state.value.isPaused,
                onComplete = {
                    syncVideoPlaybackOrderMode()
                    exoPlayerControlsManager.updatePlaybackOrderButtonState()
                }
            )
        }
    }

    /**
     * S0213 Pillar A: a replay request landed on a path that just failed to decode and is still
     * inside [RecentDecoderFailureTracker.DECODER_COOLDOWN_MS]. Behavior is context-dependent
     * (see strategic spec ADR-4):
     * - Slideshow / playlist (auto pacing) -> short toast and auto-skip to the next file.
     * - Manual single-file replay -> hand off to UI layer via [VideoPlayerManager.PlayerCallback]
     *   so a snackbar with an explicit "Skip" action can be shown; we do NOT auto-advance.
     */
    private fun handleCooldownReentry(path: String, remainingSec: Int) {
        val isSlideshow = viewModel.state.value.isSlideShowActive
        Timber.d("S0213: Pillar A cooldown re-entry path=$path remainingSec=$remainingSec slideshow=$isSlideshow")
        if (isSlideshow) {
            Toast.makeText(activity, R.string.s0213_decoder_cooldown_skip, Toast.LENGTH_SHORT).show()
            Timber.i("S0213 cooldown skip (slideshow): path=$path remainingSec=$remainingSec")
            viewModel.nextFile(skipDocuments = true)
        } else {
            Timber.i("S0213 cooldown re-entry (manual): path=$path remainingSec=$remainingSec")
            videoPlayerManager.playerCallback.onDecoderCooldownReentry(path, remainingSec)
        }
    }
}
