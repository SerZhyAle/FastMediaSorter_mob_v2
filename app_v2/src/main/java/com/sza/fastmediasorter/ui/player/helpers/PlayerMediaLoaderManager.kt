package com.sza.fastmediasorter.ui.player.helpers

import android.net.Uri
import android.os.Handler
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.Player
import com.bumptech.glide.Glide
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.ui.player.ImageLoadingManager
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

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
    private val pdfViewerManager: PdfViewerManager,
    private val epubViewerManager: EpubViewerManager,
    private val textViewerManager: TextViewerManager,
    private val exoPlayerControlsManager: ExoPlayerControlsManager,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val loadingIndicatorHandler: Handler,
    private val showLoadingIndicatorRunnable: Runnable,
    private val mediaFilesCacheManager: MediaFilesCacheManager,
    private val audioServiceController: AudioServiceController? = null,
    private val onAudioServicePlaybackChanged: (Boolean) -> Unit = {}
) {
    private val safeViews = PlayerBindingSafeViews(binding)
    private var servicePlaybackPlayer: Player? = null
    private val servicePlaybackListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            onAudioServicePlaybackChanged(isPlaying)
        }
    }

    init {
        // Wire first-frame extraction to dynamic background when video is opened
        videoPlayerManager.onFirstFrameReady = { bitmap ->
            imageLoadingManager.triggerVideoBackground(bitmap)
        }
    }

    companion object {
        private const val VIDEO_CONTROLS_AUTO_HIDE_DELAY_MS = 15000L
    }

    /**
     * Display image (delegates to ImageLoadingManager)
     */
    fun displayImage(path: String) {
        Timber.d("PlayerMediaLoaderManager.displayImage: path=$path")
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
        textViewerManager.displayText(mediaFile, isWritable = resource?.isWritable == true)
    }

    /**
     * Play video or audio file with comprehensive media type routing
     */
    fun playVideo(path: String) {
        Timber.i("PlayerMediaLoaderManager.playVideo: START - path=$path")
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
        
        // Route audio to background service when enabled
        val isBackgroundAudioEnabled = viewModel.state.value.enableBackgroundAudio
        if (isAudioFile && isBackgroundAudioEnabled && audioServiceController != null) {
            Timber.d("PlayerMediaLoaderManager.playVideo: routing AUDIO through AudioPlaybackService")
            playAudioViaService(path)
            return
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
     * Connects via MediaController, sets it as PlayerView's player.
     * Only called when enableBackgroundAudio is ON and file is AUDIO.
     * Local files only for now — network audio files use the standard VideoPlayerManager path.
     */
    private fun playAudioViaService(path: String) {
        val controller = audioServiceController ?: return
        val uri = Uri.parse(path).let { parsed ->
            if (parsed.scheme == null) Uri.fromFile(java.io.File(path)) else parsed
        }

        controller.playAudio(uri) { player ->
            activity.runOnUiThread {
                bindServicePlaybackListener(player)

                // Set MediaController as PlayerView's player — controls work automatically
                binding.playerView.player = player

                // Cancel loading indicator
                loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
                binding.progressBar.isVisible = false

                Timber.d("PlayerMediaLoaderManager.playAudioViaService: service player bound to PlayerView")
            }
        }
    }

    private fun bindServicePlaybackListener(player: Player) {
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

    /** Whether audio is currently playing through the background service */
    val isServiceAudioActive: Boolean
        get() = audioServiceController?.isConnected == true
                && viewModel.state.value.enableBackgroundAudio
                && viewModel.state.value.currentFile?.type == MediaType.AUDIO

    /**
     * Play local video file (legacy method for compatibility)
     */
    fun playLocalVideo(path: String) {
        Timber.d("PlayerMediaLoaderManager.playLocalVideo: Delegating to VideoPlayerManager")
        videoPlayerManager.playLocalVideo(path, !viewModel.state.value.isPaused)
        exoPlayerControlsManager.updateRepeatButtonIcon()
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
                activity.showError(activity.getString(R.string.msg_failed_reload_image, e.message))
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

    // Private helper methods

    /**
     * Hide image-related views.
     * @param keepAudioCover If true, don't hide audioCoverArtView (for audio files to prevent flicker)
     */
    private fun hideImageViews(keepAudioCover: Boolean = false) {
        binding.imageView.isVisible = false
        binding.photoView.isVisible = false
        if (!keepAudioCover) {
            binding.audioCoverArtView.isVisible = false
        }
        safeViews.btnTranslateImage.isVisible = false
    }

    private fun hideTextViewerControls() {
        safeViews.textViewerContainer.isVisible = false
        binding.btnCopyTextCmd.isVisible = false
        binding.btnEditTextCmd.isVisible = false
        binding.btnTranslateTextCmd.isVisible = false
        binding.btnSearchTextCmd.isVisible = false
    }

    private fun hidePdfViewerControls() {
        safeViews.pdfControlsLayout.isVisible = false
        binding.btnGoogleLensPdfCmd.isVisible = false
        binding.btnOcrPdfCmd.isVisible = false
        binding.btnTranslatePdfCmd.isVisible = false
        binding.btnSearchPdfCmd.isVisible = false
    }

    private fun hideEpubViewerControls() {
        binding.epubWebView.isVisible = false
        safeViews.epubControlsLayout.isVisible = false
        binding.btnExitEpubFullscreen.isVisible = false
        binding.btnSearchEpubCmd.isVisible = false
        binding.btnTranslateEpubCmd.isVisible = false
        binding.btnOcrEpubCmd.isVisible = false
    }

    private fun configurePlayerViewForMediaType(isAudioFile: Boolean, currentFile: MediaFile?) {
        val exoContentFrame = binding.playerView.findViewById<View>(androidx.media3.ui.R.id.exo_content_frame)
        if (isAudioFile) {
            // For audio: always show controls, never hide
            // Use Integer.MAX_VALUE to prevent auto-hide (negative values don't work reliably)
            binding.playerView.controllerShowTimeoutMs = Int.MAX_VALUE
            // Force controls visible immediately — no tap required
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

    private fun determineResourceType(path: String, defaultType: ResourceType?): ResourceType {
        return when {
            path.startsWith("cloud://") -> ResourceType.CLOUD
            path.startsWith("smb://") -> ResourceType.SMB
            path.startsWith("sftp://") -> ResourceType.SFTP
            path.startsWith("ftp://") -> ResourceType.FTP
            else -> defaultType ?: ResourceType.LOCAL
        }
    }

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
                            // Update repeat button after player is created
                            exoPlayerControlsManager.updateRepeatButtonIcon()
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
                        // Update repeat button after player is created
                        exoPlayerControlsManager.updateRepeatButtonIcon()
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
                    exoPlayerControlsManager.updateRepeatButtonIcon()
                }
            )
        }
    }
}
