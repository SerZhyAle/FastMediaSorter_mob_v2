package com.sza.fastmediasorter.ui.player.helpers

import android.net.Uri
import android.os.Handler
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import com.bumptech.glide.Glide
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.core.playback.RecentDecoderFailureTracker
import com.sza.fastmediasorter.core.util.PathUtils
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.CloudStorageClient
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.MidiPlaybackPolicy
import com.sza.fastmediasorter.domain.model.PlaybackOrderMode
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.allowsWriteOperations
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.ui.player.AudioPlaybackService
import com.sza.fastmediasorter.ui.player.ImageLoadingManager
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import com.sza.fastmediasorter.utils.SmbPathUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.File

/** Facade between PlayerActivity and specialized managers. Routes media by type (image/video/audio/PDF/EPUB/text), coordinates visibility, loading state, and image reload. */
class PlayerMediaLoaderManager(
    private val activity: PlayerActivity,
    // S1549: var, not val - the host re-inflates the layout on rotation and re-points this manager
    // at the fresh binding via [rebind]; the instance survives because its audio-service player
    // listener must not be re-registered.
    private var binding: ActivityPlayerUnifiedBinding,
    private val viewModel: PlayerViewModel,
    // S1549: vars, not vals - a re-inflate re-creates both helpers against the fresh binding and
    // [rebind] re-points this manager at them, because the audio-service listener on this instance
    // must survive while the view-bound helpers it drives are replaced.
    private var imageLoadingManager: ImageLoadingManager,
    private val videoPlayerManager: VideoPlayerManager,
    private val textViewerManagerProvider: () -> TextViewerManager,
    private var exoPlayerControlsManager: ExoPlayerControlsManager,
    private val lifecycleScope: LifecycleCoroutineScope,
    // S0704: retained for the audio-readiness feedback toast only; the spinner is owned by
    // activity.loadingIndicatorCoordinator.
    private val loadingIndicatorHandler: Handler,
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
    // S0391: source-availability gate; the Favorites mixed-source path must not play a hidden source.
    private val remoteSourceGate: com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate,
) {
    private val safeViews = PlayerBindingSafeViews(binding)
    private val viewVisibility = PlayerMediaViewVisibilityHelper(binding)
    private var servicePlaybackPlayer: Player? = null

    // S0851: guards the one-shot order-mode re-arm; reset when a new service player is bound (resume).
    private var serviceOrderModeReappliedForTimeline = false
    private var audioPrefetchJob: Job? = null
    private var audioPrefetchPath: String? = null

    // S0346 Pillar B: readiness-delay feedback. When the next network/cloud audio track takes
    // longer than the threshold to become playable (e.g. a large MP3 still downloading over SFTP),
    // the metadata/name has already changed but the sound has not - which reads as "the button did
    // nothing". A delayed toast tells the user the track is loading; it is cancelled the moment the
    // track actually starts. Scheduled on the main-looper [loadingIndicatorHandler].
    private var audioReadinessFeedbackRunnable: Runnable? = null
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
            showMidiPlaybackErrorIfNeeded(error)
            onAudioServicePlaybackError(error)
        }
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            // S0851: re-arm the persisted order mode when the reconnected controller's timeline syncs.
            reapplyServiceOrderModeOnTimelineReady()
        }
    }

    init {
        // Wire first-frame extraction to dynamic background when video is opened
        videoPlayerManager.onFirstFrameReady = { bitmap, isPlaceholder ->
            imageLoadingManager.triggerVideoBackground(bitmap, isPlaceholder)
        }
    }

    /**
     * S1549: aim every `binding.` read at the freshly inflated hierarchy after a re-inflate, and
     * re-point the two view-bound helpers that were re-created against the new binding.
     *
     * S1943: `playerView` is declared `gone` in both orientation layouts and the only code that ever
     * reveals it is the one-shot call in [playVideo] at load time, so a re-inflate resurrects the
     * surface hidden while the surviving ExoPlayer keeps decoding into it - the picture never comes
     * back. Carry the pre-rebind visibility across and re-apply the media-type configuration that
     * died with the old view. `binding` still points at the discarded tree on entry, which is what
     * makes that state readable here instead of in the caller.
     */
    fun rebind(
        newBinding: ActivityPlayerUnifiedBinding,
        newImageLoadingManager: ImageLoadingManager,
        newExoPlayerControlsManager: ExoPlayerControlsManager,
    ) {
        val playerSurfaceWasVisible = binding.playerView.isVisible
        binding = newBinding
        imageLoadingManager = newImageLoadingManager
        exoPlayerControlsManager = newExoPlayerControlsManager
        if (playerSurfaceWasVisible) {
            binding.playerView.isVisible = true
            val currentFile = viewModel.state.value.currentFile
            configurePlayerViewForMediaType(currentFile?.type == MediaType.AUDIO, currentFile)
        }
    }

    companion object {
        private const val VIDEO_CONTROLS_AUTO_HIDE_DELAY_MS = 15000L

        // S0346 Pillar B: how long the next track may take to start before the user gets a
        // "loading next track" toast. Approximate per strategic spec; tuned on the car scenario.
        private const val AUDIO_READINESS_FEEDBACK_THRESHOLD_MS = 2000L
    }

    private val stereoDetector = com.sza.fastmediasorter.ui.player.StereoDetector()

    /** Delegates to ImageLoadingManager; runs still-image stereo detection first so 3D/spherical images get correct crop/XR layer and the 3D tab reflects the mode. */
    fun displayImage(path: String) {
        Timber.d("PlayerMediaLoaderManager.displayImage: path=$path")

        val currentFile = viewModel.state.value.currentFile
        viewModel.resetStereoModeForNewFile(path)

        val detected = stereoDetector.detectForImage(
            path = path,
            width = currentFile?.width,
            height = currentFile?.height,
            config = videoPlayerManager.stereoDetectionConfig,
        )

        // Propagate to ViewModel so the 3D tab reflects the current mode and the vr-flavor OpenXR
        // renderer (DiagnosticXrRuntime) picks it up via the stereoMode flow.
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

    /** Display text file (delegates to TextViewerManager) */
    fun displayText(mediaFile: MediaFile) {
        val resource = viewModel.state.value.resource
        Timber.d("PlayerMediaLoaderManager.displayText: file=${mediaFile.name}")
        imageLoadingManager.hideAnimatedBadge()
        // S1019: text edit affordance via the shared write-policy resolver.
        textViewerManagerProvider().displayText(mediaFile, isWritable = resource?.allowsWriteOperations() == true)
    }

    /** Play video or audio file with comprehensive media type routing */
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

        // S0391: resolve the original source from the path prefix (Favorites aggregates mixed sources)
        // and turn back when that source is user-disabled, before any loading UI is shown. Guards both
        // the in-app player route and the audio-service pre-cache route below.
        if (!isPathSourceEnabled(path, resource?.type)) {
            Timber.w("PlayerMediaLoaderManager.playVideo: source disabled, refusing playback - path=$path")
            Toast.makeText(
                activity,
                activity.getString(R.string.error_resource_unavailable, path.substringAfterLast('/')),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

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

        hideTextViewerControls()
        
        hidePdfViewerControls()
        
        hideEpubViewerControls()
        
        binding.playerView.isVisible = true
        
        // Configure PlayerView based on media type
        configurePlayerViewForMediaType(isAudioFile, currentFile)
        
        // S0704: show the generic media-buffering spinner after 1s (covers in-app video/audio and
        // the brief pre-bind window for service audio). Cleared by onPlaybackReady (in-app) or
        // bindServicePlayerToView (service).
        activity.loadingIndicatorCoordinator.showDelayed(LoadingSource.VIDEO_EXOPLAYER)

        // Route audio to persistent playback service when enabled
        // S1379: the compile-time axis comes from the capability contract the host injects, never
        // from the build flag directly (CLAUDE.md Rule 14).
        val isPersistentAudioEnabled = viewModel.state.value.enablePersistentAudioPlayback
            && activity.capabilityAvailability.isPersistentAudioPlaybackAvailable()
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

    /** Route audio playback through AudioPlaybackService. Local files: builds playlist from all local audio files. Network files (SMB/SFTP/FTP): pre-caches current file to local storage, then plays via service. Cloud files: pre-caches current file to local storage via CloudStorageClient, then plays via service. */
    private fun playAudioViaService(path: String) {
        val controller = audioServiceController ?: return
        val resourceType = determineResourceType(path, null)

        if (resourceType == ResourceType.LOCAL) {
            playLocalAudioViaService(path, controller)
            return
        }

        // Network (SMB/SFTP/FTP) and cloud audio: stream directly through the background service so
        // background-continue applies the same as for local files, without a mandatory full pre-cache.
        val resource = viewModel.state.value.resource
        val currentFile = viewModel.state.value.currentFile
        val isFavorite = resource?.id == -100L && currentFile?.resourceId != null
        val credentialsId = if (isFavorite) null else resource?.credentialsId
        // S0346 Pillar B: arm readiness feedback so a slow connect is communicated instead of looking
        // like an ignored button press.
        scheduleAudioReadinessFeedback()
        lifecycleScope.launch {
            val resolvedCredentialsId = if (isFavorite) {
                viewModel.getCredentialsIdForResource(currentFile!!.resourceId)
            } else {
                credentialsId
            }
            streamNetworkAudioViaService(path, resourceType, resolvedCredentialsId, controller)
        }
    }

    /**
     * Stream a network/cloud audio source through the background service (no mandatory pre-cache),
     * so the persistent-playback service owns the track and background-continue works on exit.
     * Credentials are resolved here (DB access) and handed to the service via the controller; the
     * service cannot resolve them on its player thread. Falls back to the in-app player when the
     * source has no usable credentials.
     */
    private suspend fun streamNetworkAudioViaService(
        path: String,
        resourceType: ResourceType,
        credentialsId: String?,
        controller: AudioServiceController
    ) {
        val streamUri: Uri
        val streamCredentials: StreamCredentials?
        if (resourceType == ResourceType.CLOUD) {
            streamUri = PathUtils.safeParseUri(path)
            streamCredentials = null
        } else {
            val creds = credentialsId?.let { credentialsRepository?.getByCredentialId(it) }
            if (creds == null) {
                Timber.w("streamNetworkAudioViaService: no credentials for $path - falling back to in-app player")
                cancelAudioReadinessFeedback()
                playVideoWithResourceType(path, resourceType, viewModel.state.value.currentFile, viewModel.state.value.resource)
                return
            }
            streamUri = buildNetworkStreamUri(path, creds.server, creds.shareName)
            streamCredentials = StreamCredentials(creds.username, creds.password, creds.domain, creds.port)
        }

        // Position key is the original network/cloud path, never the stream URI (ADR-2/ADR-3).
        AudioPlaybackService.currentOriginalPath = path
        viewModel.state.value.resource?.let { AudioPlaybackService.currentResourceId = it.id }

        val title = viewModel.state.value.currentFile?.name?.substringBeforeLast('.')
            ?: path.substringAfterLast('/').substringBeforeLast('.')
        val savedPositionMs = playbackPositionRepository?.let { repo ->
            PlaybackPositionRestorer.restoreAndNotify(
                path = path,
                repository = repo,
                context = activity,
                resumedFromStringResId = R.string.playback_resumed_from
            )
        } ?: 0L

        cancelAudioReadinessFeedback()
        controller.playAudioWithMetadata(
            streamUri, title, mimeType = midiMimeTypeFor(path), streamCredentials = streamCredentials
        ) { player ->
            if (savedPositionMs > 0L) player.seekTo(savedPositionMs)
            activity.runOnUiThread { bindServicePlayerToView(player) }
        }
    }

    /** Build a properly-encoded streaming URI for a network audio path (mirrors the in-app stream helpers). */
    private fun buildNetworkStreamUri(path: String, server: String, credentialShareName: String?): Uri {
        if (path.startsWith("smb://")) {
            val pathInfo = SmbPathUtils.parseSmbPath(path)
            val share = pathInfo?.connectionInfo?.shareName?.ifEmpty { null } ?: credentialShareName ?: ""
            val remotePath = pathInfo?.remotePath ?: ""
            val encodedPath = remotePath.split("/").joinToString("/") { Uri.encode(it, "@") }
            return Uri.Builder()
                .scheme("smb")
                .authority(server)
                .encodedPath("/$share/$encodedPath")
                .build()
        }
        // sftp:// and ftp:// - the authority already carries host:port. Set it via encodedAuthority
        // so the host:port colon survives verbatim. Uri.Builder.authority() treats the value as
        // decoded and percent-encodes the ':' to %3A; the rebuilt URI's getHost() then returns the
        // whole "host:port" string and getPort() returns -1, so the background audio service hands
        // JSch "host:port" as the hostname and SFTP/FTP streaming dies with UnknownHostException.
        val parsed = PathUtils.safeParseUri(path)
        val encodedPath = parsed.path?.split("/")
            ?.joinToString("/") { segment -> if (segment.isEmpty()) "" else Uri.encode(segment, "@") } ?: ""
        return Uri.Builder()
            .scheme(parsed.scheme)
            .encodedAuthority(parsed.encodedAuthority)
            .encodedPath(encodedPath)
            .build()
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
            controller.playAudio(uri, mimeType = midiMimeTypeFor(path)) { player ->
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
        val authority = networkAuthority(path)
        val resourceKey = authority?.let { "${path.substringBefore("://")}://${it.first}:${it.second}" }
        val speedMbps = resourceKey?.let { ConnectionThrottleManager.getLastSpeedMbps(it) }
        val startupPolicy = PrefetchPolicyManager.audioStartupPolicyFor(
            path, isAudio = true, speedMbps = speedMbps, fileSizeBytes = fileSize
        )

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

        // Detect an offline server within the short connect bound so the user is not held waiting the
        // full (generous) transfer budget for a host that is simply unreachable.
        if (authority != null && !isServerReachable(authority.first, authority.second, startupPolicy.connectTimeoutMs)) {
            Timber.w(
                "preCacheNetworkAudio: server ${authority.first}:${authority.second} unreachable within " +
                    "${startupPolicy.connectTimeoutMs}ms - falling back to direct streaming"
            )
            destFile.delete()
            return@withContext null
        }

        try {
            // Transfer budget is generous (adaptive from measured speed) once the server has answered;
            // the connect probe above already bounds the dead-server case.
            withTimeout(startupPolicy.transferTimeoutMs) {
                when {
                    path.startsWith("smb://") -> downloadSmbFull(path, destFile, credentialsId)
                    path.startsWith("sftp://") -> downloadSftpFull(path, destFile, credentialsId)
                    path.startsWith("ftp://") -> downloadFtpFull(path, destFile, credentialsId)
                    else -> null
                }
            }
        } catch (e: TimeoutCancellationException) {
            Timber.w(
                "preCacheNetworkAudio: source=${startupPolicy.sourceType.logLabel} reason=${startupPolicy.reason} " +
                    "transfer timed out after ${startupPolicy.transferTimeoutMs}ms for $path - falling back to direct streaming"
            )
            destFile.delete()
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Structured cancellation (e.g. lifecycleScope destroyed while downloading) - not an error.
            destFile.delete()
            throw e
        } catch (e: Exception) {
            // Recoverable: prefetch/precache falls back to direct streaming, so this is not an error.
            Timber.w(e, "preCacheNetworkAudio: download failed for $path")
            destFile.delete()
            null
        }
    }

    /** Parse `scheme://host[:port]/..` network path into (host, port) with protocol default ports. */
    private fun networkAuthority(path: String): Pair<String, Int>? {
        val (scheme, defaultPort) = when {
            path.startsWith("smb://") -> "smb://" to 445
            path.startsWith("sftp://") -> "sftp://" to 22
            path.startsWith("ftp://") -> "ftp://" to 21
            else -> return null
        }
        val authority = path.substringAfter(scheme).substringBefore('/')
        if (authority.isEmpty()) return null
        val host = authority.substringBefore(':')
        if (host.isEmpty()) return null
        val port = authority.substringAfter(':', defaultPort.toString()).toIntOrNull() ?: defaultPort
        return host to port
    }

    /** Lightweight TCP reachability probe; any failure means treat the server as offline (safe default). */
    private suspend fun isServerReachable(host: String, port: Int, timeoutMs: Long): Boolean =
        withContext(Dispatchers.IO) {
            try {
                java.net.Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress(host, port), timeoutMs.toInt())
                    true
                }
            } catch (e: Exception) {
                Timber.d("preCacheNetworkAudio: reachability probe failed for $host:$port (${e.javaClass.simpleName})")
                false
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
                else -> { Timber.w("preCacheNetworkAudio: SMB download failed for $path"); null }
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
            if (result.isSuccess) destFile else { Timber.w("preCacheNetworkAudio: SFTP download failed for $path"); null }
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
            if (result.isSuccess) destFile else { Timber.w("preCacheNetworkAudio: FTP download failed for $path"); null }
        }
    }

    /** Download cloud audio file to UnifiedFileCache. Parses cloud:// path to determine provider and fileId, then downloads via CloudStorageClient. */
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

    private fun midiMimeTypeFor(path: String): String? {
        // Cache files use hash names, so MIDI must be identified from the original source path.
        return if (MidiPlaybackPolicy.isMidiPath(path)) MimeTypes.AUDIO_MIDI else null
    }

    private fun showMidiPlaybackErrorIfNeeded(error: androidx.media3.common.PlaybackException) {
        val path = viewModel.state.value.currentFile?.path ?: return
        if (!MidiPlaybackPolicy.isMidiPath(path)) return
        activity.showError(activity.getString(R.string.midi_playback_failed), error)
    }

    /** Build playback URI for a local MediaFile. Prefers contentUri (content:// from MediaStore) over file:// to avoid EACCES on Android 10+ scoped storage when AudioPlaybackService opens files via FileDataSource. */
    private fun buildUriForMediaFile(mediaFile: com.sza.fastmediasorter.domain.model.MediaFile): Uri {
        val contentUri = mediaFile.contentUri
        if (!contentUri.isNullOrBlank()) return Uri.parse(contentUri)
        return buildLocalUri(mediaFile.path)
    }

    internal fun bindServicePlayerToView(player: Player) {
        bindServicePlaybackListener(player)
        binding.playerView.player = player
        // S0704: service audio bound = ready. Cancel the playVideo pending show (armed as
        // VIDEO_EXOPLAYER before the audio/video branch) and drop the source.
        activity.loadingIndicatorCoordinator.reset(LoadingSource.VIDEO_EXOPLAYER)
        activity.updateTrackButtonsVisibility()
        Timber.d("PlayerMediaLoaderManager: service player bound to PlayerView")
    }

    /** Re-attach PlayerView to the service MediaController after Activity resumes. Must be called in onResume() when service audio is active. */
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
            // S0851: a new (reconnected) player starts with an unsynced timeline - re-arm the re-apply.
            serviceOrderModeReappliedForTimeline = false
        }
        onAudioServicePlaybackChanged(player.isPlaying)
    }

    /**
     * S0851: re-apply the persisted playback-order mode once the reconnected service timeline is
     * populated. On resume a fresh MediaController first reports mediaItemCount <= 1, so the order
     * mode applied during bind wrongly reads "single item" and disables ExoPlayer shuffle/loop; the
     * resumed SHUFFLE then advances in list order. Runs at most once per binding (guard flag) so the
     * shuffle order is not re-rolled on later timeline updates. Single-item timelines (S0549) never
     * satisfy the multi-item check, so their app-level order model is left untouched.
     */
    private fun reapplyServiceOrderModeOnTimelineReady() {
        val controller = audioServiceController ?: return
        val player = controller.player
        if (player == null || serviceOrderModeReappliedForTimeline || player.mediaItemCount <= 1) return
        serviceOrderModeReappliedForTimeline = true
        val mode = viewModel.state.value.playbackOrderMode
        controller.applyPlaybackOrderMode(mode)
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

    /** Play local video file (legacy method for compatibility) */
    fun playLocalVideo(path: String) {
        Timber.d("PlayerMediaLoaderManager.playLocalVideo: Delegating to VideoPlayerManager")
        videoPlayerManager.playLocalVideo(path, !viewModel.state.value.isPaused)
        syncVideoPlaybackOrderMode()
        exoPlayerControlsManager.updatePlaybackOrderButtonState()
    }

    /** Reload current image after edit operation (rotation/flip/filter/adjustments). Clears both memory and disk cache, updates MediaFilesCache, and reloads. */
    fun reloadCurrentImage() {
        val currentFile = viewModel.state.value.currentFile ?: return
        val resource = viewModel.state.value.resource
        
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                // S0704: clear the image spinner + its pending show/safety before reloading the
                // edited image (a fresh displayImage() arms them again).
                activity.loadingIndicatorCoordinator.reset(LoadingSource.IMAGE_GLIDE)

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

    /** Preload adjacent images for faster navigation */
    fun preloadNextImageIfNeeded() {
        imageLoadingManager.preloadNextImageIfNeeded()
    }

    /** Prefetch the next audio file into UnifiedFileCache. Called when the current audio reaches STATE_READY (playing). Only applies to network sources (SMB/SFTP/FTP) - local audio uses ExoPlayer playlist. */
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
                val recovery = PrefetchPolicyManager.nextTrackPrefetchRecovery(path)
                Timber.w(
                    "prefetchNextAudio: RECOVERABLE failure - ${nextFile.name}; " +
                        "source=${recovery.sourceType.logLabel} reason=${recovery.reason} " +
                        "currentTrackUnaffected=${recovery.currentTrackUnaffected} retryOnDemand=${recovery.retryOnDemand}"
                )
            }
        }
    }

    /** Cancel any in-flight audio prefetch (e.g. on navigation or activity destroy). */
    fun cancelAudioPrefetch() {
        audioPrefetchJob?.cancel()
        audioPrefetchJob = null
        audioPrefetchPath = null
        // A pending readiness toast for an abandoned load would be misleading - drop it too.
        cancelAudioReadinessFeedback()
    }

    /**
     * S0346 Pillar B: arm the readiness-delay feedback. If the next track has not started playing
     * within [AUDIO_READINESS_FEEDBACK_THRESHOLD_MS], show a brief "loading next track" toast so the
     * user gets honest feedback instead of a silent gap while the file streams/downloads. Replaces
     * any previously armed feedback (one in flight at a time).
     */
    private fun scheduleAudioReadinessFeedback() {
        cancelAudioReadinessFeedback()
        val runnable = Runnable {
            Toast.makeText(activity, activity.getString(R.string.audio_next_track_loading), Toast.LENGTH_SHORT).show()
        }
        audioReadinessFeedbackRunnable = runnable
        loadingIndicatorHandler.postDelayed(runnable, AUDIO_READINESS_FEEDBACK_THRESHOLD_MS)
    }

    /** Cancel the readiness-delay feedback - the track started, or the load was abandoned. */
    private fun cancelAudioReadinessFeedback() {
        audioReadinessFeedbackRunnable?.let { loadingIndicatorHandler.removeCallbacks(it) }
        audioReadinessFeedbackRunnable = null
    }

    /** Show audio file info overlay */
    fun showAudioFileInfo(file: MediaFile?) {
        imageLoadingManager.showAudioFileInfo(file)
    }

    /** Update audio touch zones visibility based on current state */
    fun updateAudioTouchZonesVisibility() {
        val state = viewModel.state.value
        val isAudioFile = state.currentFile?.type == MediaType.AUDIO
        val isInFullscreenMode = !state.showCommandPanel && !state.showControls
        
        // NEVER show overlay for audio - audio uses ExoPlayer UI, not touch zones
        safeViews.audioTouchZonesOverlay.isVisible = false
        Timber.d("PlayerMediaLoaderManager.updateAudioTouchZonesVisibility: Overlay hidden - audio=$isAudioFile, fullscreen=$isInFullscreenMode")
    }

    /** Update volume buttons visibility - show for audio and video files */
    fun updateVolumeButtonsVisibility() {
        val fileType = viewModel.state.value.currentFile?.type
        val isMediaWithSound = fileType == MediaType.AUDIO || fileType == MediaType.VIDEO
        binding.btnVolumeDown.isVisible = isMediaWithSound
        binding.btnVolumeUp.isVisible = isMediaWithSound
    }

    /** Adjust touch zones for video/audio (disable in command panel mode) CRITICAL: We now use TouchZoneGestureManager for ALL touch zone detection. The View-based overlays (touchZonesOverlay, touchZones3Overlay) are ALWAYS hidden to prevent conflicts with gesture-based zone detection. */
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
            
            showAudioFileInfo(currentFile)
        } else {
            // For video: auto-hide controls after 15 seconds
            binding.playerView.controllerShowTimeoutMs = VIDEO_CONTROLS_AUTO_HIDE_DELAY_MS.toInt()

            // S1005: reveal the transport controller when a video opens (mirrors the audio branch). The
            // unified player has no tap gesture that shows the controller for video (VideoTouchDelegate
            // reveal is disabled), so without this the controls never appear on open; auto-hide still
            // fires after VIDEO_CONTROLS_AUTO_HIDE_DELAY_MS and a center-tap re-reveals them.
            binding.playerView.showController()

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

    /**
     * S0391: true when the source resolved from [path] (prefix-first, so the Favorites aggregate
     * resolves each file's original source) is available per the gate. LOCAL is always available;
     * CLOUD consults the cloud group; SMB/SFTP/FTP map to their [RemoteSourceId].
     */
    private fun isPathSourceEnabled(path: String, defaultType: ResourceType?): Boolean {
        val type = determineResourceType(path, defaultType)
        return when (type) {
            ResourceType.LOCAL -> true
            ResourceType.CLOUD -> remoteSourceGate.anyCloudEnabled()
            else -> com.sza.fastmediasorter.core.capability.RemoteSourceId
                .networkFromResourceType(type)
                ?.let { remoteSourceGate.isEnabled(it) } ?: true
        }
    }

    private fun playVideoWithResourceType(
        path: String,
        resourceType: ResourceType,
        currentFile: MediaFile?,
        resource: com.sza.fastmediasorter.domain.model.MediaResource?
    ) {
        // Internet streams carry no credentials and must keep their stream type so VideoPlayerManager
        // routes them to playStreamVideo - the legacy else-branch forced LOCAL, which then failed
        // File.exists() and aborted playback (the "Local file does not exist" stream regression).
        if (resourceType == ResourceType.HTTP_STREAM || resourceType == ResourceType.RTSP_STREAM) {
            videoPlayerManager.playVideo(
                path = path,
                resourceType = resourceType,
                credentialsId = null,
                playWhenReady = !viewModel.state.value.isPaused,
                onComplete = {
                    syncVideoPlaybackOrderMode()
                    exoPlayerControlsManager.updatePlaybackOrderButtonState()
                }
            )
            return
        }

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
        if (isSlideshow) {
            Toast.makeText(activity, R.string.s0213_decoder_cooldown_skip, Toast.LENGTH_SHORT).show()
            Timber.i("cooldown skip (slideshow): path=$path remainingSec=$remainingSec")
            viewModel.nextFile(skipDocuments = true)
        } else {
            Timber.i("cooldown re-entry (manual): path=$path remainingSec=$remainingSec")
            videoPlayerManager.playerCallback.onDecoderCooldownReentry(path, remainingSec)
        }
    }
}
