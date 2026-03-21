package com.sza.fastmediasorter.ui.player

import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.view.View
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LifecycleCoroutineScope
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.core.util.MemoryTier
import com.sza.fastmediasorter.data.cloud.glide.CloudThumbnailData
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.cloud.CloudPathParser
import com.sza.fastmediasorter.data.network.glide.NetworkFileData
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.data.repository.AudioMetadataCacheRepository
import com.sza.fastmediasorter.data.repository.AudioMetadataSaveData
import com.sza.fastmediasorter.data.repository.CachedAudioData
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.SearchAudioCoverUseCase
import com.sza.fastmediasorter.ui.image.ImageDisplayUtils
import com.sza.fastmediasorter.ui.player.render.DualSurfaceStaticImageRenderer
import com.sza.fastmediasorter.ui.player.render.PrefetchQueue
import com.sza.fastmediasorter.ui.player.render.PrefetchQueueConfig
import com.sza.fastmediasorter.ui.player.render.PriorityPrefetchQueue
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.ui.player.render.RenderModeHint
import com.sza.fastmediasorter.ui.player.render.RenderPriority
import com.sza.fastmediasorter.ui.player.render.RenderTarget
import com.sza.fastmediasorter.ui.player.render.StaticImageRenderer
import com.sza.fastmediasorter.ui.player.helpers.AnimatedImageController
import com.sza.fastmediasorter.ui.player.helpers.AudioEmptyStateController
import com.sza.fastmediasorter.ui.player.helpers.PlayerBindingSafeViews
import com.sza.fastmediasorter.ui.player.helpers.WindowMetricsCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Manages image loading in PlayerActivity:
 * - Display images (Cloud/Network/Local) with Glide
 * - Preload adjacent images for faster navigation
 * - Show audio file info overlay
 * - Update audio format info from ExoPlayer
 */
class ImageLoadingManager(
    private val binding: ActivityPlayerUnifiedBinding,
    private val settingsRepository: SettingsRepository,
    private val searchAudioCoverUseCase: SearchAudioCoverUseCase,
    private val audioMetadataCacheRepository: AudioMetadataCacheRepository,
    private val okHttpClient: okhttp3.OkHttpClient,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val loadingIndicatorHandler: Handler,
    private val showLoadingIndicatorRunnable: Runnable,
    private val preloadJobs: MutableList<Job>,
    private val callback: ImageLoadingCallback
) {
    private val safeViews = PlayerBindingSafeViews(binding)
    
    // Detect memory tier for optimization strategy
    private val memoryTier: MemoryTier = MemoryTier.detect(binding.root.context)
    
    init {
        Timber.i("ImageLoadingManager: Initialized with memoryTier=$memoryTier")
    }
    
    interface ImageLoadingCallback {
        fun isFinishing(): Boolean
        fun isDestroyed(): Boolean
        fun releasePlayer()
        fun showError(message: String, exception: Throwable? = null)
        fun showToast(message: String)
        fun getWindowManager(): android.view.WindowManager
        fun onAudioMetadataLoaded(metadata: com.sza.fastmediasorter.domain.model.AudioMetadata)
        fun updateSlideShow()
        fun getAdjacentFiles(): List<MediaFile>
        fun getCurrentFile(): MediaFile?
        fun getCurrentResource(): com.sza.fastmediasorter.domain.model.MediaResource?
        fun getExoPlayer(): androidx.media3.exoplayer.ExoPlayer?
        fun getString(resId: Int): String
        fun isShowingCommandPanel(): Boolean
        fun isSlideshowActive(): Boolean
        fun setAnimatedBadgeVisible(visible: Boolean)
    }
    
    // Context for scale type determination (set before loading image)
    private var currentCropSetting: Boolean = true
    private var currentIsFullscreenOrSlideshow: Boolean = false
    private var currentDeviceWidth: Int = 0
    private var currentDeviceHeight: Int = 0
    private var currentTargetView: android.widget.ImageView? = null
    private var currentIsAnimatedContent: Boolean = false
    /** True while image mode is active; false when video/audio took over via [clearForVideoTransition]. */
    private var isInImageDisplayMode: Boolean = false
    private val animatedImageController = AnimatedImageController()

    /** Injected after construction; controls empty-state animations for audio files. */
    private var audioEmptyStateController: AudioEmptyStateController? = null

    /** Tracks the active cover-art loading coroutine so stale jobs can be cancelled on track change. */
    private var coverArtJob: Job? = null

    /**
     * Path of the audio file whose cover art is currently displayed in audioCoverArtView.
     * Used to skip redundant reloads on seek (ExoPlayer re-fires STATE_READY after every seek).
     */
    private var coverArtDisplayedForPath: String? = null

    // Cached size/duration for single-line info assembly in updateAudioFormatInfo()
    private var audioFileSizeStr: String = ""
    private var audioDurationStr: String = ""

    fun setAudioEmptyStateController(controller: AudioEmptyStateController) {
        audioEmptyStateController = controller
    }
    private var dynamicBackgroundProcessor: DynamicBackgroundProcessor? = null
    private var isDynamicBackgroundEnabled: Boolean = false
    private val staticImageRenderer: StaticImageRenderer = DualSurfaceStaticImageRenderer(
        surfaceA = binding.photoView,
        surfaceB = binding.photoViewSurfaceB
    )
    private val prefetchQueue: PriorityPrefetchQueue = PriorityPrefetchQueue(
        isCongested = { getResourceKey()?.let { ConnectionThrottleManager.isCongested(it) } ?: false }
    ).apply {
        updateConfig(PrefetchQueueConfig(maxDepth = 6, throttleMs = 0L))
    }

    /**
     * Build resource key for ConnectionThrottleManager from current resource.
     */
    private fun getResourceKey(): String? {
        val resource = callback.getCurrentResource() ?: return null
        return when {
            resource.path.startsWith("smb://") -> resource.path.substringBefore("/", resource.path)
            resource.path.startsWith("ftp://") -> "ftp://" + resource.path.substringAfter("://").substringBefore("/")
            resource.path.startsWith("sftp://") -> "sftp://" + resource.path.substringAfter("://").substringBefore("/")
            else -> resource.path
        }
    }

    /**
     * Update prefetch queue slideshow bias.
     * When slideshow is active, forward targets get higher priority.
     */
    fun setSlideshowBias(enabled: Boolean) {
        prefetchQueue.slideshowBias = enabled
        Timber.d("ImageLoadingManager: Slideshow bias set to $enabled")
    }

    /**
     * Enable or disable the dynamic background extension effect.
     * When enabled, a [DynamicBackgroundProcessor] is created and attached to [ivDynamicBackground].
     * When disabled, the processor is cleared and the background view is hidden.
     *
     * NOTE: [binding.ivDynamicBackground] must exist in the layout (it is always added, just gone by default).
     */
    fun setDynamicBackgroundEnabled(enabled: Boolean) {
        isDynamicBackgroundEnabled = enabled
        Timber.d("ImageLoadingManager: Dynamic background enabled=$enabled")
        if (enabled) {
            if (dynamicBackgroundProcessor == null) {
                dynamicBackgroundProcessor = DynamicBackgroundProcessor(
                    backgroundView = binding.ivDynamicBackground,
                    coroutineScope = lifecycleScope
                )
            }
        } else {
            dynamicBackgroundProcessor?.clear()
            // Keep the processor instance to avoid re-creation churn on rapid toggles
        }
    }
    
    /**
     * Explicitly clear dynamic background lines.
     * Called when switching to video playback so stale image lines don't persist
     * until the first video frame is ready.
     */
    fun clearDynamicBackground() {
        dynamicBackgroundProcessor?.clear()
    }

    /**
     * Cancel all in-flight image loads and clear image views immediately.
     * Must be called when transitioning FROM image TO video/audio so that:
     * 1. Stale Glide callbacks cannot re-apply the dynamic background after clear.
     * 2. photoView / imageView don't hold large bitmaps while video is playing.
     */
    fun clearForVideoTransition() {
        isInImageDisplayMode = false

        // Cancel any in-flight Glide requests to prevent stale onResourceReady callbacks
        // from re-showing the dynamic background after we clear it.
        try {
            val appContext = binding.root.context.applicationContext
            Glide.with(appContext).clear(binding.imageView)
            Glide.with(appContext).clear(binding.photoView)
        } catch (e: Exception) {
            Timber.w(e, "ImageLoadingManager.clearForVideoTransition: Error clearing Glide requests")
        }
        binding.imageView.setImageDrawable(null)
        binding.photoView.setImageDrawable(null)

        // Clear blurred background so stripes from the previous image are not visible
        // in the video player pillarbox areas.
        dynamicBackgroundProcessor?.clear()

        // Cancel pending loading indicators
        loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
        loadingIndicatorHandler.removeCallbacks(hideLoadingSafetyRunnable)
        binding.progressBar.isVisible = false

        Timber.d("ImageLoadingManager.clearForVideoTransition: Glide cleared, dynamic background cleared")
    }

    /**
     * Trigger dynamic background processing from a decoded video frame bitmap.
     * Called by [VideoPlayerManager] (via [PlayerMediaLoaderManager]) on first frame rendered.
     * No-op when dynamic background is disabled or screen dimensions not yet resolved.
     */
    fun triggerVideoBackground(bitmap: android.graphics.Bitmap) {
        if (!isDynamicBackgroundEnabled) return
        val w = currentDeviceWidth.takeIf { it > 0 } ?: return
        val h = currentDeviceHeight.takeIf { it > 0 } ?: return
        Timber.d("ImageLoadingManager: triggerVideoBackground frame=${bitmap.width}x${bitmap.height} screen=${w}x${h}")
        dynamicBackgroundProcessor?.processFromBitmap(bitmap, w, h)
    }

    /**
     * Cleanup all resources - cancel Glide requests and pending handlers.
     * Called from PlayerLifecycleManager.onDestroy() to prevent memory leaks.
     */
    fun cleanup() {
        Timber.d("ImageLoadingManager: Cleaning up resources")
        
        // Cancel any dynamic background processing
        dynamicBackgroundProcessor?.clear()
        
        // Cancel all pending handlers
        loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
        loadingIndicatorHandler.removeCallbacks(hideLoadingSafetyRunnable)
        
        // Cancel all Glide requests (use applicationContext to avoid destroyed activity error)
        try {
            val appContext = binding.root.context.applicationContext
            Glide.with(appContext).clear(binding.imageView)
            Glide.with(appContext).clear(binding.photoView)
        } catch (e: Exception) {
            Timber.w(e, "ImageLoadingManager: Error clearing Glide requests")
        }
        
        // Cancel all preload jobs
        preloadJobs.forEach { it.cancel() }
        preloadJobs.clear()
        prefetchQueue.clear()
        animatedImageController.release()
        currentIsAnimatedContent = false
        callback.setAnimatedBadgeVisible(false)
        staticImageRenderer.release()
        dynamicBackgroundProcessor = null
        
        Timber.d("ImageLoadingManager: Cleanup complete")
    }
    
    /**
     * Pause renderer - called from Activity onPause().
     * Pauses any pending prefetch operations.
     */
    fun onPause() {
        Timber.d("ImageLoadingManager: onPause - pausing renderer")
        animatedImageController.onPause()
        staticImageRenderer.onPause()
    }
    
    /**
     * Resume renderer - called from Activity onResume().
     * Resumes prefetch operations.
     */
    fun onResume() {
        Timber.d("ImageLoadingManager: onResume - resuming renderer")
        animatedImageController.onResume()
        staticImageRenderer.onResume()
    }

    fun isCurrentAnimatedContent(): Boolean = currentIsAnimatedContent && animatedImageController.hasAnimatedDrawable()

    fun isAnimatedPlaybackPaused(): Boolean = animatedImageController.isPlaybackPaused()

    fun toggleAnimatedPlayback(): Boolean? {
        val paused = animatedImageController.togglePlayback()
        if (paused != null) {
            callback.setAnimatedBadgeVisible(currentIsAnimatedContent)
        }
        return paused
    }

    fun hideAnimatedBadge() {
        currentIsAnimatedContent = false
        callback.setAnimatedBadgeVisible(false)
    }
    
    /**
     * Re-evaluate and apply scale type after device rotation.
     * Called when configuration changes (portrait ↔ landscape) to update the scale type
     * for the currently displayed image without reloading it.
     */
    fun reEvaluateScaleTypeOnRotation() {
        lifecycleScope.launch {
            try {
                // Get current settings
                val settings = settingsRepository.getSettings().first()
                
                // Get current device dimensions (API 28+ compatible)
                val (deviceWidth, deviceHeight) = WindowMetricsCompat.getScreenSize(
                    callback.getWindowManager()
                )
                
                // Determine which view is currently visible
                val targetView = when {
                    binding.imageView.isVisible -> binding.imageView
                    binding.photoView.isVisible -> binding.photoView
                    else -> null
                }
                
                targetView?.let { view ->
                    val drawable = view.drawable
                    if (drawable != null) {
                        val imageWidth = drawable.intrinsicWidth
                        val imageHeight = drawable.intrinsicHeight
                        
                        if (imageWidth > 0 && imageHeight > 0) {
                            val isFullscreenOrSlideshow = !callback.isShowingCommandPanel() || callback.isSlideshowActive()
                            
                            val scaleType = ImageDisplayUtils.determineImageScaleType(
                                cropImagesToFullscreen = settings.cropImagesToFullscreen,
                                isFullscreenOrSlideshow = isFullscreenOrSlideshow,
                                imageWidth = imageWidth,
                                imageHeight = imageHeight,
                                deviceWidth = deviceWidth,
                                deviceHeight = deviceHeight
                            )
                            
                            view.scaleType = scaleType
                            Timber.d("ImageLoadingManager: Re-evaluated scale type on rotation: $scaleType (image: ${imageWidth}x${imageHeight}, device: ${deviceWidth}x${deviceHeight})")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "ImageLoadingManager: Error re-evaluating scale type on rotation")
            }
        }
    }
    
    // Safety timeout to hide spinner if Glide hangs or cancels silently
    private val hideLoadingSafetyRunnable = Runnable {
        Timber.w("ImageLoadingManager.safetyTimeout: Loading took too long, hiding spinner")
        if (!callback.isDestroyed()) {
            binding.progressBar.isVisible = false
            callback.showToast(binding.root.context.getString(R.string.msg_loading_timeout))
        }
    }
    
    /**
     * Display image in ImageView or PhotoView based on settings
     */
    fun displayImage(path: String) {
        isInImageDisplayMode = true
        Timber.i("ImageLoadingManager.displayImage: START - path=$path")
        
        // Log memory state BEFORE loading new image
        logMemoryStats("BEFORE displayImage")
        
        // NOTE: Do NOT clear imageView/photoView before loading new image!
        // This causes a brief black screen flash between slides.
        // Glide will automatically replace the image when the new one is ready.
        // Memory cleanup happens when the new request completes and replaces the old bitmap.
        
        // Cancel all preload jobs to prevent memory accumulation
        synchronized(preloadJobs) {
            preloadJobs.forEach { it.cancel() }
            preloadJobs.clear()
        }
        Timber.d("ImageLoadingManager.displayImage: Cancelled all preload jobs")
        
        // Ensure any pending loading indicator from previous request is cancelled immediately
        loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
        loadingIndicatorHandler.removeCallbacks(hideLoadingSafetyRunnable)
        if (!callback.isDestroyed()) {
            binding.progressBar.isVisible = false
        }
        
        // Skip if activity is being destroyed
        if (callback.isFinishing() || callback.isDestroyed()) {
            Timber.d("ImageLoadingManager.displayImage: Activity is finishing/destroyed, skipping image display")
            return
        }
        
        callback.releasePlayer()
        animatedImageController.prepareForNewContent()
        binding.playerView.isVisible = false
        
        // Always clear stale background at transition start so old strips never
        // appear before the new image. New strips will be recomputed in onResourceReady.
        dynamicBackgroundProcessor?.clear()
        
        // Hide audio-related views (including any active empty-state animation)
        Timber.d("displayImage: HIDING audioCoverArtView (was ${binding.audioCoverArtView.isVisible})")
        audioEmptyStateController?.hide()
        binding.audioCoverArtView.isVisible = false
        safeViews.audioTouchZonesOverlay.isVisible = false
        binding.audioInfoOverlay.isVisible = false
        safeViews.pdfControlsLayout.isVisible = false
        safeViews.textViewerContainer.isVisible = false
        
        // Hide text action buttons (they are for TXT files only)
        binding.btnCopyTextCmd.isVisible = false
        binding.btnEditTextCmd.isVisible = false
        binding.btnTranslateTextCmd.isVisible = false
        binding.btnSearchTextCmd.isVisible = false
        
        // Hide PDF action buttons (they are for PDF files only)
        binding.btnGoogleLensPdfCmd.isVisible = false
        binding.btnOcrPdfCmd.isVisible = false
        binding.btnTranslatePdfCmd.isVisible = false
        binding.btnSearchPdfCmd.isVisible = false
        
        // Hide EPUB action buttons (they are for EPUB files only)
        binding.btnSearchEpubCmd.isVisible = false
        binding.btnTranslateEpubCmd.isVisible = false
        
        // Hide EPUB WebView and controls (they are for EPUB files only)
        binding.epubWebView.isVisible = false
        safeViews.epubControlsLayout.isVisible = false
        binding.btnExitEpubFullscreen.isVisible = false

        // Schedule loading indicator to show after 1 second
        loadingIndicatorHandler.postDelayed(showLoadingIndicatorRunnable, 1000)
        // Schedule safety timeout (30 seconds)
        loadingIndicatorHandler.postDelayed(hideLoadingSafetyRunnable, 30000)

        val currentFile = callback.getCurrentFile()
        val resource = callback.getCurrentResource()
        
        // Get settings to determine which view to use
        lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            if (settings.rendererMigrationEnabled) {
                Timber.i("ImageLoadingManager: rendererMigrationEnabled=true (boundary active, legacy rendering path remains in use)")
            }
            // During slideshow, force size limit to prevent OOM crashes
            val isSlideshowActive = callback.isSlideshowActive()
            val isAnimatedContent = animatedImageController.isAnimatedContent(currentFile, path)
            currentIsAnimatedContent = isAnimatedContent
            callback.setAnimatedBadgeVisible(isAnimatedContent)
            val usePhotoView = isAnimatedContent || (settings.loadFullSizeImages && currentFile != null && !isSlideshowActive)
            
            Timber.d("ImageLoadingManager.displayImage: enableTranslation=${settings.enableTranslation}")
            Timber.d("TOUCH_DEBUG: ImageLoadingManager.displayImage - usePhotoView=$usePhotoView, loadFullSizeImages=${settings.loadFullSizeImages}")
            
            // Switch visibility between ImageView and PhotoView
            binding.imageView.isVisible = !usePhotoView
                binding.photoDualSurfaceContainer?.isVisible = usePhotoView
            binding.photoView.isVisible = usePhotoView
            binding.photoViewSurfaceB?.isVisible = false
            Timber.d(
                "TOUCH_DEBUG: View visibility set - imageView.isVisible=${binding.imageView.isVisible}, " +
                    "photoDualSurfaceContainer.isVisible=${binding.photoDualSurfaceContainer?.isVisible == true}, " +
                    "photoView.isVisible=${binding.photoView.isVisible}, " +
                    "photoViewSurfaceB.isVisible=${binding.photoViewSurfaceB?.isVisible == true}"
            )
            
            // Configure PhotoView gestures based on loadFullSizeImages setting
            if (usePhotoView) {
                binding.photoView.apply {
                    if (settings.loadFullSizeImages) {
                        // Full gestures mode: zoom, pan, rotation, double-tap
                        // PhotoView supports all gestures by default when scale limits allow it
                        minimumScale = 1.0f  // Original size
                        mediumScale = 2.0f   // Not used (we override tap gestures)
                        maximumScale = 5.0f  // Maximum zoom
                        
                        Timber.d("GESTURE_CONFIG: PhotoView configured for CUSTOM GESTURES mode")
                        Timber.d("GESTURE_CONFIG: - Zoom: ENABLED (min=1.0x, max=5.0x)")
                        Timber.d("GESTURE_CONFIG: - Double-tap: Command Panel=2x (REG-3100), Fullscreen=3x")
                        Timber.d("GESTURE_CONFIG: - Long press: Command Panel=2x, Fullscreen=3x")
                        Timber.d("GESTURE_CONFIG: - Rotation: ENABLED (two-finger rotate)")
                        Timber.d("GESTURE_CONFIG: - Pan: ENABLED (after zoom)")
                        Timber.d("GESTURE_CONFIG: - Pinch zoom: ENABLED (gradual zoom)")
                        
                        // NOTE: OnDoubleTapListener is set once in setupGestureDetector()
                        // Do NOT reset it here - it handles touch zones + custom zoom logic
                    } else {
                        // Rotation-only mode: disable zoom by setting all scales to 1.0f
                        // PhotoView rotation is always available (two-finger gesture)
                        minimumScale = 1.0f
                        mediumScale = 1.0f  // Disable zoom on double-tap
                        maximumScale = 1.0f // Disable pinch zoom
                        
                        Timber.d("GESTURE_CONFIG: PhotoView configured for ROTATION-ONLY mode")
                        Timber.d("GESTURE_CONFIG: - Zoom: DISABLED (all scales locked to 1.0x)")
                        Timber.d("GESTURE_CONFIG: - Rotation: ENABLED (two-finger rotate)")
                        Timber.d("GESTURE_CONFIG: - Pan: DISABLED (no zoom = no pan)")
                        Timber.d("GESTURE_CONFIG: - Double-tap: DISABLED (mediumScale = 1.0x)")
                    }
                    
                    // Add matrix change listener for debug logging
                    setOnMatrixChangeListener { rect ->
                        val currentScale = scale
                        val currentRotation = rotation
                        Timber.d("GESTURE_DEBUG: Matrix changed - scale=${"%.2f".format(currentScale)}x, rotation=${"%.1f".format(currentRotation)}°")
                        Timber.d("GESTURE_DEBUG: Display rect: $rect")
                    }
                    
                    // Add scale change listener for debug logging
                    setOnScaleChangeListener { scaleFactor, focusX, focusY ->
                        Timber.d("GESTURE_DEBUG: Scale change - factor=${"%.2f".format(scaleFactor)}, focus=(${"%.0f".format(focusX)}, ${"%.0f".format(focusY)})")
                    }
                }
            }
            
            // NOTE: Button visibility (btnTranslateImageCmd, btnOcrImageCmd, etc.) is now managed 
            // by CommandPanelController.updateCommandAvailability() to ensure proper landscape/portrait handling
            
            // Hide deprecated overlay buttons (moved to command panel)
            safeViews.btnTranslateImage.isVisible = false
            safeViews.btnGoogleLensImage.isVisible = false
            safeViews.btnOcrImage.isVisible = false
            
            Timber.d("ImageLoadingManager.displayImage: btnTranslateImage.isVisible=${safeViews.btnTranslateImage.isVisible}, btnTranslateImage.visibility=${safeViews.btnTranslateImage.visibility}")
            
            // ALWAYS hide View-based touch zone overlays
            // TouchZoneGestureManager handles ALL touch detection via gesture listeners
            // - Command panel mode: 3-zone via handleCommandPanelTouchZones()
            // - Fullscreen mode: 9-zone via handleTouchZone()
            safeViews.touchZonesOverlay.isVisible = false
            safeViews.touchZones3Overlay.isVisible = false
            
            // Determine target view for image loading
            val targetView = if (usePhotoView) binding.photoView else binding.imageView
            
            // Determine scale type based on crop setting and orientation match
            val isFullscreenOrSlideshow = !callback.isShowingCommandPanel() || isSlideshowActive
            // Get device dimensions (API 28+ compatible)
            val (deviceWidth, deviceHeight) = WindowMetricsCompat.getScreenSize(
                callback.getWindowManager()
            )
            
            // Store context for scale type determination in onResourceReady
            currentCropSetting = settings.cropImagesToFullscreen
            currentIsFullscreenOrSlideshow = isFullscreenOrSlideshow
            currentDeviceWidth = deviceWidth
            currentDeviceHeight = deviceHeight
            currentTargetView = targetView
            
            // Apply initial scale type based on settings
            // Note: This is a heuristic - we don't have image dimensions yet
            // The actual scale type will be re-evaluated in onResourceReady when we have image dims
            val initialScaleType = if (settings.cropImagesToFullscreen && isFullscreenOrSlideshow) {
                android.widget.ImageView.ScaleType.CENTER_CROP
            } else {
                android.widget.ImageView.ScaleType.FIT_CENTER
            }
            targetView.scaleType = initialScaleType
            
            // NOTE: Touch listeners are NOT set here anymore - they are configured once
            // in PlayerActivity.setupTouchZones() and must NOT be overwritten.
            // The imageTouchGestureDetector in PlayerActivity handles 9-zone touch detection.
            
            // Determine actual resource type from path prefix (for Favorites with mixed sources)
            val actualResourceType = when {
                path.startsWith("cloud://") -> ResourceType.CLOUD
                path.startsWith("smb://") -> ResourceType.SMB
                path.startsWith("sftp://") -> ResourceType.SFTP
                path.startsWith("ftp://") -> ResourceType.FTP
                else -> resource?.type ?: ResourceType.LOCAL
            }
            
            // Check if this is a cloud resource
            // During slideshow, always limit size to prevent OOM
            val effectiveLoadFullSize = settings.loadFullSizeImages && !isSlideshowActive
            if (currentFile != null && actualResourceType == ResourceType.CLOUD) {
                loadCloudImage(path, currentFile, targetView, effectiveLoadFullSize, isSlideshowActive)
            } else if (currentFile != null && 
                (actualResourceType == ResourceType.SMB || actualResourceType == ResourceType.SFTP || actualResourceType == ResourceType.FTP)) {
                loadNetworkImage(path, currentFile, resource, targetView, effectiveLoadFullSize, isSlideshowActive)
            } else {
                loadLocalImage(path, currentFile, targetView, effectiveLoadFullSize, isSlideshowActive)
            }

            // Start prefetching adjacent images immediately (don't wait for current image to load).
            // This ensures next image is ready by the time slideshow interval expires.
            preloadNextImageIfNeeded()
            
            callback.updateSlideShow()
        }
    }
    
    private suspend fun loadCloudImage(
        path: String,
        currentFile: MediaFile,
        targetView: android.widget.ImageView,
        loadFullSize: Boolean,
        isSlideshowActive: Boolean = false
    ) {
        // Detect cloud provider from URL scheme authority (cloud://dropbox/, cloud://googledrive/, etc.)
        val provider = when {
            path.startsWith("cloud://googledrive", ignoreCase = true) || path.startsWith("cloud://google_drive", ignoreCase = true) -> CloudProvider.GOOGLE_DRIVE
            path.startsWith("cloud://onedrive", ignoreCase = true) -> CloudProvider.ONEDRIVE
            path.startsWith("cloud://dropbox", ignoreCase = true) -> CloudProvider.DROPBOX
            else -> CloudProvider.GOOGLE_DRIVE // default fallback
        }
        
        // Extract file ID from cloud path
        // For Google Drive/OneDrive: cloud://google_drive/FILE_ID -> FILE_ID
        // For Dropbox: cloud:/dropbox/folder/file.jpg -> /folder/file.jpg
        val fileId = when (provider) {
            CloudProvider.DROPBOX -> {
                // Dropbox needs full path starting with /
                val afterScheme = path.substringAfter("cloud://dropbox")
                val cleanPath = afterScheme.trimStart('/')
                "/$cleanPath"
            }
            else -> {
                // Google Drive and OneDrive use file ID (last segment)
                path.substringAfterLast("/")
            }
        }
        
        Timber.d("ImageLoadingManager: Loading cloud image - fileId = $fileId, path = $path, provider = $provider")
        Timber.d("ImageLoadingManager: loadFullSizeImages = $loadFullSize")
        
        // Always load full image in player (never thumbnail)
        // Resolution limiting is done via Glide's override() if needed
        val thumbnailData = CloudThumbnailData(
            fileId = fileId,
            thumbnailUrl = currentFile.thumbnailUrl ?: "",
            loadFullImage = true,  // Always load full image in player
            cloudProvider = provider
        )

        val isGif = currentFile.type == MediaType.GIF || path.endsWith(".gif", ignoreCase = true)

        if (isGif) {
            Timber.d("ImageLoadingManager: Loading GIF via explicit asGif() pipeline")

            val gifRequest = Glide.with(binding.root.context)
                .asGif()
                .load(thumbnailData)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .priority(Priority.IMMEDIATE)

            val finalGifRequest = if (!loadFullSize || memoryTier == MemoryTier.LOW) {
                val maxDimension = if (memoryTier == MemoryTier.LOW) 1280 else 1920
                gifRequest.override(maxDimension, maxDimension)
            } else {
                gifRequest
            }

            finalGifRequest
                .listener(createGifGlideListener())
                .into(targetView)
            return
        }
        
        Timber.d("ImageLoadingManager: Created CloudThumbnailData with provider=$provider, loadFullImage=true")
        
        val glideRequest = Glide.with(binding.root.context)
            .load(thumbnailData)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)  // Cache decoded image, not source stream
            .priority(Priority.IMMEDIATE)
        
        // Apply memory-aware optimizations for LOW tier devices
        val optimizedRequest = if (memoryTier == MemoryTier.LOW) {
            Timber.d("ImageLoadingManager: Applying LOW memory tier optimizations - RGB_565, no animation, reduced resolution")
            glideRequest
                .format(DecodeFormat.PREFER_RGB_565)  // 50% memory per pixel
                .dontAnimate()  // Disable animations to save memory
        } else {
            glideRequest
        }
        
        // Apply size limit if loadFullSizeImages is false (limit to 1920px max dimension)
        // For LOW tier, always limit size regardless of loadFullSize setting
        val finalRequest = if (!loadFullSize || memoryTier == MemoryTier.LOW) {
            val maxDimension = if (memoryTier == MemoryTier.LOW) 1280 else 1920
            Timber.d("ImageLoadingManager: Loading cloud image with size limit: ${maxDimension}px max dimension")
            optimizedRequest.override(maxDimension, maxDimension)
        } else {
            // Load original size for zooming
            Timber.d("ImageLoadingManager: Loading cloud image at original size (no limit)")
            optimizedRequest
        }
        
        // In slideshow mode skip crossfade so image and edge-strips appear simultaneously.
        val cloudTransition = if (isSlideshowActive) DrawableTransitionOptions.withCrossFade(0)
                              else DrawableTransitionOptions.withCrossFade(150)

        // Show cached thumbnail immediately while full image downloads (eliminates black flash).
        // The thumbnail was already loaded in BrowseActivity → Glide disk cache hit = instant.
        val hasThumbnail = thumbnailData.thumbnailUrl.isNotBlank() &&
            thumbnailData.cloudProvider == CloudProvider.GOOGLE_DRIVE
        if (hasThumbnail) {
            val thumbData = thumbnailData.copy(loadFullImage = false)
            val thumbRequest = Glide.with(binding.root.context)
                .load(thumbData)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .priority(Priority.LOW)
                .override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
            finalRequest
                .thumbnail(thumbRequest)
                .transition(cloudTransition)
                .listener(createGlideListener())
                .into(targetView)
        } else {
            finalRequest
                .transition(cloudTransition)
                .listener(createGlideListener())
                .into(targetView)
        }
    }
    
    private suspend fun loadNetworkImage(
        path: String,
        currentFile: MediaFile,
        resource: com.sza.fastmediasorter.domain.model.MediaResource?,
        targetView: android.widget.ImageView,
        loadFullSize: Boolean,
        isSlideshowActive: Boolean = false
    ) {
        // Network image loading
        
        // Use NetworkFileData for Glide to load via NetworkFileModelLoader
        val networkData = NetworkFileData(
            path = path, 
            credentialsId = resource?.credentialsId, 
            loadFullImage = true,
            highPriority = true,
            size = currentFile.size,
            createdDate = currentFile.createdDate
        )
        val cacheKey = networkData.getCacheKey()

        val isGif = currentFile.type == MediaType.GIF || path.endsWith(".gif", ignoreCase = true)

        if (isGif) {
            Timber.d("ImageLoadingManager: Loading network GIF via explicit asGif() pipeline")

            val gifRequest = Glide.with(binding.root.context)
                .asGif()
                .load(networkData)
                .signature(ObjectKey(cacheKey))
                .diskCacheStrategy(DiskCacheStrategy.ALL)

            val finalGifRequest = if (!loadFullSize || memoryTier == MemoryTier.LOW) {
                val (screenWidth, screenHeight) = WindowMetricsCompat.getScreenSize(
                    callback.getWindowManager()
                )
                val (targetWidth, targetHeight) = if (memoryTier == MemoryTier.LOW) {
                    Pair((screenWidth * 0.75).toInt(), (screenHeight * 0.75).toInt())
                } else {
                    Pair(screenWidth, screenHeight)
                }
                gifRequest.override(targetWidth, targetHeight)
            } else {
                gifRequest
            }

            finalGifRequest
                .listener(createGifGlideListener())
                .into(targetView)
            return
        }
        
        val glideRequest = Glide.with(binding.root.context)
            .load(networkData)
            .signature(ObjectKey(cacheKey))
            .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache both source and decoded for persistence
        
        // Apply memory-aware optimizations for LOW tier devices
        val optimizedRequest = if (memoryTier == MemoryTier.LOW) {
            Timber.d("ImageLoadingManager: Applying LOW memory tier optimizations - RGB_565, no animation")
            glideRequest
                .format(DecodeFormat.PREFER_RGB_565)  // 50% memory per pixel
                .dontAnimate()  // Disable animations to save memory
        } else {
            glideRequest
        }
        
        // Apply size limit if loadFullSizeImages is false
        // For LOW tier, always limit size regardless of loadFullSize setting
        val finalRequest = if (!loadFullSize || memoryTier == MemoryTier.LOW) {
            // Limit to screen size to save memory (API 28+ compatible)
            val (screenWidth, screenHeight) = WindowMetricsCompat.getScreenSize(
                callback.getWindowManager()
            )
            // For LOW tier, reduce resolution by 25%
            val (targetWidth, targetHeight) = if (memoryTier == MemoryTier.LOW) {
                Pair((screenWidth * 0.75).toInt(), (screenHeight * 0.75).toInt())
            } else {
                Pair(screenWidth, screenHeight)
            }
            Timber.d("ImageLoadingManager: Loading image with size limit: ${targetWidth}x${targetHeight}")
            optimizedRequest.override(targetWidth, targetHeight)
        } else {
            // Load original size for zooming
            Timber.d("ImageLoadingManager: Loading image at original size (no limit)")
            optimizedRequest
        }
        
        // In slideshow mode skip crossfade so image and edge-strips appear simultaneously.
        val networkTransition = if (isSlideshowActive) DrawableTransitionOptions.withCrossFade(0)
                                else DrawableTransitionOptions.withCrossFade(150)
        finalRequest
            .transition(networkTransition)
            .listener(createGlideListener())
            .into(targetView)
    }
    
    private suspend fun loadLocalImage(
        path: String,
        currentFile: MediaFile?,
        targetView: android.widget.ImageView,
        loadFullSize: Boolean,
        isSlideshowActive: Boolean = false
    ) {
        // Local file - support both file:// paths and content:// URIs
        
        // Check file existence before loading
        val fileExists = if (path.startsWith("content://")) {
            try {
                val uri = Uri.parse(path)
                val docFile = DocumentFile.fromSingleUri(binding.root.context, uri)
                docFile?.exists() == true
            } catch (e: Exception) {
                Timber.e(e, "ImageLoadingManager: Error checking SAF URI existence: $path")
                false
            }
        } else {
            File(path).exists()
        }
        
        if (!fileExists) {
            Timber.w("ImageLoadingManager: File does not exist, showing error: $path")
            loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
            if (!callback.isDestroyed()) {
                binding.progressBar.isVisible = false
                callback.showError(binding.root.context.getString(R.string.file_not_found_name, currentFile?.name ?: path))
            }
            return
        }
        
        val data: Any = if (path.startsWith("content://")) {
            Uri.parse(path)
        } else if (!File(path).canRead() && !currentFile?.contentUri.isNullOrEmpty()) {
            Uri.parse(currentFile!!.contentUri)
        } else {
            File(path)
        }
        val cacheKey = "${path}_${currentFile?.size}"

        val isGif = currentFile?.type == MediaType.GIF || path.endsWith(".gif", ignoreCase = true)

        if (isGif) {
            Timber.d("ImageLoadingManager: Loading local GIF via explicit asGif() pipeline")

            val gifRequest = Glide.with(binding.root.context)
                .asGif()
                .load(data)
                .signature(ObjectKey(cacheKey))
                .diskCacheStrategy(DiskCacheStrategy.ALL)

            val finalGifRequest = if (!loadFullSize || memoryTier == MemoryTier.LOW) {
                val (screenWidth, screenHeight) = WindowMetricsCompat.getScreenSize(
                    callback.getWindowManager()
                )
                val (targetWidth, targetHeight) = if (memoryTier == MemoryTier.LOW) {
                    Pair((screenWidth * 0.75).toInt(), (screenHeight * 0.75).toInt())
                } else {
                    Pair(screenWidth, screenHeight)
                }
                gifRequest.override(targetWidth, targetHeight)
            } else {
                gifRequest
            }

            finalGifRequest
                .listener(createGifGlideListener())
                .into(targetView)
            return
        }
        
        val glideRequest = Glide.with(binding.root.context)
            .load(data)
            .signature(ObjectKey(cacheKey))
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
        
        // Apply memory-aware optimizations for LOW tier devices
        val optimizedRequest = if (memoryTier == MemoryTier.LOW) {
            Timber.d("ImageLoadingManager: Applying LOW memory tier optimizations - RGB_565, no animation")
            glideRequest
                .format(DecodeFormat.PREFER_RGB_565)  // 50% memory per pixel
                .dontAnimate()  // Disable animations to save memory
        } else {
            glideRequest
        }
        
        // Apply size limit if loadFullSizeImages is false
        // For LOW tier, always limit size regardless of loadFullSize setting
        val finalRequest = if (!loadFullSize || memoryTier == MemoryTier.LOW) {
            // Limit to screen size to save memory (API 28+ compatible)
            val (screenWidth, screenHeight) = WindowMetricsCompat.getScreenSize(
                callback.getWindowManager()
            )
            // For LOW tier, reduce resolution by 25%
            val (targetWidth, targetHeight) = if (memoryTier == MemoryTier.LOW) {
                Pair((screenWidth * 0.75).toInt(), (screenHeight * 0.75).toInt())
            } else {
                Pair(screenWidth, screenHeight)
            }
            Timber.d("ImageLoadingManager: Loading local image with size limit: ${targetWidth}x${targetHeight}")
            optimizedRequest.override(targetWidth, targetHeight)
        } else {
            // Load original size for zooming
            Timber.d("ImageLoadingManager: Loading local image at original size (no limit)")
            optimizedRequest
        }
        
        // In slideshow mode skip crossfade so image and edge-strips appear simultaneously.
        val localTransition = if (isSlideshowActive) DrawableTransitionOptions.withCrossFade(0)
                              else DrawableTransitionOptions.withCrossFade(150)
        finalRequest
            .transition(localTransition)
            .listener(createGlideListener())
            .into(targetView)
    }
    
    private fun createGlideListener(): RequestListener<Drawable> {
        return object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<Drawable>,
                isFirstResource: Boolean
            ): Boolean {
                Timber.e(e, "ImageLoadingManager.GlideListener: onLoadFailed triggered")
                animatedImageController.onLoadFailed()
                currentIsAnimatedContent = false
                callback.setAnimatedBadgeVisible(false)
                loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
                loadingIndicatorHandler.removeCallbacks(hideLoadingSafetyRunnable)
                if (!callback.isDestroyed()) {
                    binding.progressBar.isVisible = false
                }

                if (isNonCriticalNetworkImageError(e)) {
                    Timber.d("ImageLoadingManager: Suppressed non-critical network image error")
                    return false
                }
                
                // Check if this is a race condition error from fast scrolling
                val isRaceConditionError = e?.rootCauses?.any { cause ->
                    val msg = cause.message ?: ""
                    msg.contains("memory mapping") ||
                    msg.contains("setDataSource failed") ||
                    msg.contains("cancelled")
                } == true
                
                if (isRaceConditionError) {
                    Timber.w("ImageLoadingManager: Race condition error during fast scrolling")
                    if (!callback.isDestroyed()) {
                        callback.showToast("Slow down! 🐢")
                    }
                } else {
                    Timber.e(e, "ImageLoadingManager: Failed to load image")
                    if (!callback.isDestroyed()) {
                        callback.showError("Failed to load image: ${e?.message}", e)
                    }
                }
                return false
            }
            
            override fun onResourceReady(
                resource: Drawable,
                model: Any,
                target: Target<Drawable>?,
                dataSource: DataSource,
                isFirstResource: Boolean
            ): Boolean {
                Timber.d("ImageLoadingManager.GlideListener: onResourceReady triggered")
                animatedImageController.onDrawableLoaded(resource, currentTargetView)
                loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
                loadingIndicatorHandler.removeCallbacks(hideLoadingSafetyRunnable)
                if (!callback.isDestroyed()) {
                    binding.progressBar.isVisible = false
                    
                    // Apply proper scale type based on orientation matching
                    currentTargetView?.let { view ->
                        val imageWidth = resource.intrinsicWidth
                        val imageHeight = resource.intrinsicHeight
                        
                        if (imageWidth > 0 && imageHeight > 0) {
                            val scaleType = ImageDisplayUtils.determineImageScaleType(
                                cropImagesToFullscreen = currentCropSetting,
                                isFullscreenOrSlideshow = currentIsFullscreenOrSlideshow,
                                imageWidth = imageWidth,
                                imageHeight = imageHeight,
                                deviceWidth = currentDeviceWidth,
                                deviceHeight = currentDeviceHeight
                            )
                            view.scaleType = scaleType
                            Timber.d("ImageLoadingManager: Applied scale type $scaleType (image: ${imageWidth}x${imageHeight}, device: ${currentDeviceWidth}x${currentDeviceHeight})")
                        }
                    }
                    
                    // Log memory state AFTER successful image load
                    logMemoryStats("AFTER onResourceReady")

                    // Trigger dynamic background extension if enabled.
                    // Guard with isInImageDisplayMode: a stale Glide request that completes
                    // after the user has navigated to video would otherwise re-show the
                    // previous image's blurred background over the video pillarbox areas.
                    if (isDynamicBackgroundEnabled && isInImageDisplayMode) {
                        dynamicBackgroundProcessor?.process(
                            drawable = resource,
                            screenWidth = currentDeviceWidth,
                            screenHeight = currentDeviceHeight
                        )
                    }
                }
                return false
            }
        }
    }

    private fun createGifGlideListener(): RequestListener<com.bumptech.glide.load.resource.gif.GifDrawable> {
        return object : RequestListener<com.bumptech.glide.load.resource.gif.GifDrawable> {
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<com.bumptech.glide.load.resource.gif.GifDrawable>,
                isFirstResource: Boolean
            ): Boolean {
                Timber.e(e, "ImageLoadingManager.GifListener: onLoadFailed triggered")
                animatedImageController.onLoadFailed()
                currentIsAnimatedContent = false
                callback.setAnimatedBadgeVisible(false)
                loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
                loadingIndicatorHandler.removeCallbacks(hideLoadingSafetyRunnable)
                if (!callback.isDestroyed()) {
                    binding.progressBar.isVisible = false
                    if (isNonCriticalNetworkImageError(e)) {
                        Timber.d("ImageLoadingManager: Suppressed non-critical network GIF error")
                        return false
                    }
                    callback.showError("Failed to load GIF: ${e?.message}", e)
                }
                return false
            }

            override fun onResourceReady(
                resource: com.bumptech.glide.load.resource.gif.GifDrawable,
                model: Any,
                target: Target<com.bumptech.glide.load.resource.gif.GifDrawable>?,
                dataSource: DataSource,
                isFirstResource: Boolean
            ): Boolean {
                Timber.d("ImageLoadingManager.GifListener: onResourceReady triggered")
                animatedImageController.onDrawableLoaded(resource, currentTargetView)
                loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
                loadingIndicatorHandler.removeCallbacks(hideLoadingSafetyRunnable)
                if (!callback.isDestroyed()) {
                    binding.progressBar.isVisible = false

                    currentTargetView?.let { view ->
                        val imageWidth = resource.intrinsicWidth
                        val imageHeight = resource.intrinsicHeight

                        if (imageWidth > 0 && imageHeight > 0) {
                            val scaleType = ImageDisplayUtils.determineImageScaleType(
                                cropImagesToFullscreen = currentCropSetting,
                                isFullscreenOrSlideshow = currentIsFullscreenOrSlideshow,
                                imageWidth = imageWidth,
                                imageHeight = imageHeight,
                                deviceWidth = currentDeviceWidth,
                                deviceHeight = currentDeviceHeight
                            )
                            view.scaleType = scaleType
                            Timber.d("ImageLoadingManager: Applied GIF scale type $scaleType (image: ${imageWidth}x${imageHeight}, device: ${currentDeviceWidth}x${currentDeviceHeight})")
                        }
                    }

                    logMemoryStats("AFTER GIF onResourceReady")
                }
                return false
            }
        }
    }

    private fun isNonCriticalNetworkImageError(exception: GlideException?): Boolean {
        if (exception == null) return false

        val messages = buildList {
            add(exception.message ?: "")
            exception.rootCauses.forEach { rootCause ->
                add(rootCause.message ?: "")
                var cause = rootCause.cause
                while (cause != null) {
                    add(cause.message ?: "")
                    cause = cause.cause
                }
            }
        }

        val hasNetworkContext = messages.any { msg ->
            msg.contains("NetworkFileDataFetcher", ignoreCase = true) ||
            msg.contains("Failed to load network file:", ignoreCase = true) ||
            msg.contains("smb://", ignoreCase = true) ||
            msg.contains("sftp://", ignoreCase = true) ||
            msg.contains("ftp://", ignoreCase = true)
        }

        if (!hasNetworkContext) return false

        return messages.any { msg ->
            msg.contains("Invalid/corrupted image data", ignoreCase = true) ||
            msg.contains("Corrupted image data:", ignoreCase = true) ||
            msg.contains("Request cancelled", ignoreCase = true) ||
            msg.contains("Failed to decode", ignoreCase = true) ||
            msg.contains("DecodeException", ignoreCase = true) ||
            msg.contains("ImageDecoder", ignoreCase = true) ||
            msg.contains("HEIC", ignoreCase = true) ||
            msg.contains("HEIF", ignoreCase = true)
        }
    }

    /**
     * Preload adjacent images (previous + next) in background for faster navigation.
     * Only preloads IMAGE and GIF files.
     * Supports circular navigation.
     * Priority order: NEXT (index 0) > PREVIOUS (index 1) > LOOKAHEAD (others).
     */
    fun preloadNextImageIfNeeded() {
        val adjacentFiles = callback.getAdjacentFiles()
        if (adjacentFiles.isEmpty()) {
            Timber.d("ImageLoadingManager: Preload skipped - no adjacent files")
            return
        }
        
        val resource = callback.getCurrentResource() ?: run {
            Timber.d("ImageLoadingManager: Preload skipped - no current resource")
            return
        }
        prefetchQueue.clear()
        val enqueued = prefetchQueue.offerAll(
            adjacentFiles.mapIndexed { index, file ->
                // Priority assignment: NEXT(0), PREVIOUS(1), LOOKAHEAD(2+)
                val priority = when (index) {
                    0 -> RenderPriority.NEXT
                    1 -> RenderPriority.PREVIOUS
                    else -> RenderPriority.LOOKAHEAD
                }
                RenderTarget(
                    mediaFile = file,
                    path = file.path,
                    priority = priority,
                    modeHint = RenderModeHint.KEEP_CURRENT
                )
            }
        )

        Timber.d("ImageLoadingManager: Starting preload for $enqueued/${adjacentFiles.size} adjacent files (queue=${prefetchQueue.size()})")

        while (true) {
            val target = prefetchQueue.pollNext() ?: break
            preloadAdjacentTarget(target, resource)
        }
        Timber.d("ImageLoadingManager: Preload initiated via queue-shim")
        
        // Log memory stats every 10 preloads to track accumulation patterns
        preloadCounter++
        if (preloadCounter >= 10) {
            logMemoryStats("AFTER preload (every 10)")
            preloadCounter = 0
        }
    }

    private fun preloadAdjacentTarget(
        target: RenderTarget,
        resource: com.sza.fastmediasorter.domain.model.MediaResource
    ) {
        val file = target.mediaFile
        Timber.d("ImageLoadingManager: Preloading ${file.name} (${file.type}) via queue-shim")

        val job = lifecycleScope.launch {
            val actualResourceType = when {
                file.path.startsWith("cloud://") -> ResourceType.CLOUD
                file.path.startsWith("smb://") -> ResourceType.SMB
                file.path.startsWith("sftp://") -> ResourceType.SFTP
                file.path.startsWith("ftp://") -> ResourceType.FTP
                else -> resource.type
            }

            if (actualResourceType == ResourceType.SMB || actualResourceType == ResourceType.SFTP || actualResourceType == ResourceType.FTP) {
                preloadNetworkFile(file, resource)
            } else if (actualResourceType == ResourceType.CLOUD) {
                preloadCloudFile(file)
            } else {
                preloadLocalFile(file)
            }
        }

        job.invokeOnCompletion {
            synchronized(preloadJobs) {
                preloadJobs.remove(job)
            }
        }

        synchronized(preloadJobs) {
            preloadJobs.add(job)
        }
    }

    private suspend fun preloadNetworkFile(
        file: MediaFile,
        resource: com.sza.fastmediasorter.domain.model.MediaResource
    ) {
        val networkData = NetworkFileData(
            path = file.path,
            credentialsId = resource.credentialsId,
            loadFullImage = true,
            size = file.size,
            createdDate = file.createdDate
        )
        val cacheKey = networkData.getCacheKey()

        val preloadMaxDimension = if (memoryTier == MemoryTier.LOW) 1280 else 1920
        try {
            withContext(Dispatchers.IO) {
                val request = Glide.with(binding.root.context.applicationContext)
                    .downloadOnly()
                    .load(networkData)
                    .signature(ObjectKey(cacheKey))
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .override(preloadMaxDimension, preloadMaxDimension)

                if (memoryTier == MemoryTier.LOW) {
                    request.set(com.bumptech.glide.load.Option.memory("decodeFormat"), DecodeFormat.PREFER_RGB_565)
                }

                request.submit().get()
            }
            Timber.d("ImageLoadingManager: Preload ACTUALLY completed for ${file.name}")
        } catch (e: Exception) {
            Timber.d("ImageLoadingManager: Preload failed for ${file.name}: ${e.message}")
        }
    }

    private suspend fun preloadCloudFile(file: MediaFile) {
        val provider = when {
            file.path.startsWith("cloud://googledrive", ignoreCase = true) || file.path.startsWith("cloud://google_drive", ignoreCase = true) -> CloudProvider.GOOGLE_DRIVE
            file.path.startsWith("cloud://onedrive", ignoreCase = true) -> CloudProvider.ONEDRIVE
            file.path.startsWith("cloud://dropbox", ignoreCase = true) -> CloudProvider.DROPBOX
            else -> CloudProvider.GOOGLE_DRIVE
        }
        val fileId = when (provider) {
            CloudProvider.DROPBOX -> {
                val afterScheme = file.path.substringAfter("cloud://dropbox")
                val cleanPath = afterScheme.trimStart('/')
                "/$cleanPath"
            }
            else -> file.path.substringAfterLast('/')
        }
        val cloudData = CloudThumbnailData(
            thumbnailUrl = file.thumbnailUrl ?: "",
            fileId = fileId,
            loadFullImage = true,
            cloudProvider = provider
        )
        val cacheKey = "${file.path}_${file.size}"

        val preloadMaxDimension = if (memoryTier == MemoryTier.LOW) 1280 else 1920
        try {
            withContext(Dispatchers.IO) {
                val request = Glide.with(binding.root.context.applicationContext)
                    .downloadOnly()
                    .load(cloudData)
                    .signature(ObjectKey(cacheKey))
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .override(preloadMaxDimension, preloadMaxDimension)

                if (memoryTier == MemoryTier.LOW) {
                    request.set(com.bumptech.glide.load.Option.memory("decodeFormat"), DecodeFormat.PREFER_RGB_565)
                }

                request.submit().get()
            }
            Timber.d("ImageLoadingManager: Preload ACTUALLY completed for ${file.name}")
        } catch (e: Exception) {
            Timber.d("ImageLoadingManager: Preload failed for ${file.name}: ${e.message}")
        }
    }

    private suspend fun preloadLocalFile(file: MediaFile) {
        val cacheKey = "${file.path}_${file.size}"
        val preloadMaxDimension = if (memoryTier == MemoryTier.LOW) 1280 else 1920
        try {
            withContext(Dispatchers.IO) {
                val loadSource: Any = if (!File(file.path).canRead() && !file.contentUri.isNullOrEmpty()) {
                    Uri.parse(file.contentUri)
                } else {
                    File(file.path)
                }
                val request = Glide.with(binding.root.context.applicationContext)
                    .downloadOnly()
                    .load(loadSource)
                    .signature(ObjectKey(cacheKey))
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .override(preloadMaxDimension, preloadMaxDimension)

                if (memoryTier == MemoryTier.LOW) {
                    request.set(com.bumptech.glide.load.Option.memory("decodeFormat"), DecodeFormat.PREFER_RGB_565)
                }

                request.submit().get()
            }
            Timber.d("ImageLoadingManager: Preload ACTUALLY completed for ${file.name}")
        } catch (e: Exception) {
            Timber.d("ImageLoadingManager: Preload failed for ${file.name}: ${e.message}")
        }
    }
    
    /**
     * Show audio file info overlay.
     * Top line (audioMetadata): Artist – Album – Title  (or filename without ext if no metadata)
     * Bottom line (audioFileInfo): Size • Format • Duration  (assembled on one line)
     * Full filename with extension is displayed via tvFileNameOverlay (top-left corner).
     */
    fun showAudioFileInfo(file: MediaFile?) {
        if (file == null) return

        binding.audioInfoOverlay.isVisible = true

        // audioFileName view is never used now — full filename is in top-left tvFileNameOverlay
        safeViews.audioFileName.visibility = View.GONE

        // Build Artist – Album – Title line.
        // If ID3 tags are absent, try to infer artist from the parent directory name
        // (common pattern in Russian music collections: "YYYY-Artist-Album/track.mp3").
        val effectiveArtist = file.artist?.takeIf { it.isNotBlank() }
            ?: parseArtistFromPath(file.path)
        val effectiveTitle = file.title?.takeIf { it.isNotBlank() }
        val metadataParts = listOfNotNull(
            effectiveArtist,
            file.album?.takeIf { it.isNotBlank() },
            effectiveTitle
        )
        val metadataLine = if (metadataParts.isNotEmpty()) {
            metadataParts.joinToString(" \u2013 ")   // en-dash separator
        } else {
            // No embedded metadata — show "DirName / filename" as fallback
            val fileNameNoExt = file.name.substringBeforeLast('.')
            val dirName = file.path.substringBeforeLast('/').substringAfterLast('/')
            if (dirName.isNotBlank() && dirName != fileNameNoExt) {
                "$dirName / $fileNameNoExt"
            } else {
                fileNameNoExt
            }
        }
        safeViews.audioMetadata.text = metadataLine
        safeViews.audioMetadata.visibility = View.VISIBLE

        // Reset cached parts and build info line asynchronously
        audioFileSizeStr = ""
        audioDurationStr = ""
        safeViews.audioFileInfo.text = ""

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fileSize = file.size
                audioFileSizeStr = if (fileSize > 0) {
                    com.sza.fastmediasorter.core.util.formatFileSize(fileSize)
                } else ""

                audioDurationStr = file.duration?.let {
                    if (it > 0) formatDuration(it) else ""
                } ?: ""

                withContext(Dispatchers.Main) {
                    safeViews.audioFileInfo.text = buildAudioInfoLine(null)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get audio file info")
                withContext(Dispatchers.Main) {
                    safeViews.audioFileInfo.text = callback.getString(R.string.file_info_unavailable)
                }
            }
        }
    }

    private fun buildAudioInfoLine(format: String?): String = buildList {
        if (audioFileSizeStr.isNotEmpty()) add(audioFileSizeStr)
        if (!format.isNullOrEmpty()) add(format)
        if (audioDurationStr.isNotEmpty()) add(audioDurationStr)
    }.joinToString(" \u2022 ")

    /**
     * Infers an artist name from the parent directory of the file path.
     * Handles common music folder patterns:
     *   "YYYY-Artist-Album" → "Artist"
     *   "Artist - Album"   → "Artist"
     *   "Artist"           → "Artist"
     * Returns null when no meaningful artist can be determined.
     */
    private fun parseArtistFromPath(path: String): String? {
        val withoutScheme = path.substringAfter("://").ifEmpty { path }
        val parts = withoutScheme.split('/')
        val dirName = parts.dropLast(1).lastOrNull()?.takeIf { it.isNotBlank() } ?: return null

        // Strip leading year (e.g. "2017-" or "2017 ")
        val withoutYear = dirName.replace(Regex("^\\d{4}[-\\s]"), "").trim()

        // Try "Artist - Album" (with spaces around hyphen)
        if (withoutYear.contains(" - ")) {
            return withoutYear.substringBefore(" - ").trim().takeIf { it.isNotBlank() }
        }

        // Try "Artist-Album" (hyphen, no surrounding spaces)
        if (withoutYear.contains("-")) {
            val candidate = withoutYear.substringBefore("-").trim()
            if (candidate.isNotBlank() && !candidate.all { it.isDigit() }) {
                return candidate
            }
        }

        // Single-segment directory without pattern — not reliable enough to use as artist
        return null
    }

    private fun formatDuration(millis: Long?): String {
        if (millis == null || millis <= 0) return "N/A"
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes % 60, seconds % 60)
        } else {
            "%d:%02d".format(minutes, seconds % 60)
        }
    }
    
    /**
     * Update audio format info from ExoPlayer tracks
     */
    fun updateAudioFormatInfo() {
        val formatInfo = callback.getExoPlayer()?.currentTracks?.groups?.firstOrNull { group ->
            group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO
        }?.let { audioGroup ->
            val format = audioGroup.getTrackFormat(0)
            buildString {
                format.sampleMimeType?.let { 
                    append(it.substringAfter("audio/").uppercase())
                }
                format.sampleRate.let { 
                    if (isNotEmpty()) append(" • ")
                    append("${it / 1000} kHz")
                }
                format.channelCount.let {
                    if (isNotEmpty()) append(" • ")
                    append(when (it) {
                        1 -> "Mono"
                        2 -> "Stereo"
                        else -> "$it channels"
                    })
                }
                format.bitrate.let {
                    if (it > 0) {
                        if (isNotEmpty()) append(" • ")
                        append("${it / 1000} kbps")
                    }
                }
            }
        }
        
        if (!formatInfo.isNullOrEmpty()) {
            safeViews.audioFileInfo.text = buildAudioInfoLine(formatInfo)
        }
    }
    
    /**
     * Load audio cover art with fallback to iTunes Search API
     * Called when ExoPlayer is ready for audio files
     */
    fun loadAudioCoverArt(file: MediaFile) {
        // Skip reload if cover art for this exact file is already visible (e.g. seek re-triggers STATE_READY).
        if (file.path == coverArtDisplayedForPath && binding.audioCoverArtView.isVisible) {
            Timber.d("loadAudioCoverArt: cover already displayed for ${file.name}, seek re-trigger skipped")
            return
        }
        coverArtDisplayedForPath = null

        // Cancel any pending cover-art coroutine from a previous track to avoid stale callbacks.
        coverArtJob?.cancel()
        coverArtJob = null

        val callId = System.currentTimeMillis()
        val caller = Thread.currentThread().stackTrace.getOrNull(3)?.let {
            "${it.className}.${it.methodName}():${it.lineNumber}"
        } ?: "Unknown"
        
        Timber.d("╔════════════════════════════════════════════════════════════════")
        Timber.d("║ loadAudioCoverArt() CALLED [ID=$callId]")
        Timber.d("╚════════════════════════════════════════════════════════════════")
        Timber.d("loadAudioCoverArt[$callId]: file=${file.name}")
        Timber.d("loadAudioCoverArt[$callId]: caller=$caller")
        Timber.d("loadAudioCoverArt[$callId]: audioCoverArtView.isVisible=${binding.audioCoverArtView.isVisible}")
        
        val isNetworkFile = file.path.startsWith("smb://") || file.path.startsWith("sftp://") || file.path.startsWith("ftp://")
        val isCloudFile = file.path.startsWith("cloud://")
        
        // For network/cloud files, check if ExoPlayer has embedded artwork first
        // ExoPlayer can extract artwork from network streams, but MediaMetadataRetriever cannot
        // Cloud files also cannot be opened by MediaMetadataRetriever (cloud:// scheme unsupported)
        if (isNetworkFile || isCloudFile) {
            // Show empty-state animation immediately as a placeholder — hide it later if artwork is found.
            lifecycleScope.launch {
                val mode = try { settingsRepository.getSettings().first().audioEmptyStateMode } catch (_: Exception) { AudioEmptyStateController.MODE_NONE }
                Timber.d("loadAudioCoverArt[$callId]: showing empty-state immediately (network/cloud file), mode=$mode")
                audioEmptyStateController?.show(mode)
            }
            // Delay to let ExoPlayer load metadata and artwork
            coverArtJob = lifecycleScope.launch {
                delay(1500) // Wait for ExoPlayer to extract artwork
                
                // Check if ExoPlayer's PlayerView is showing artwork
                val player = binding.playerView.player
                val hasExoPlayerArtwork = player?.mediaMetadata?.artworkData != null ||
                        player?.mediaMetadata?.artworkUri != null
                
                Timber.d("Network file artwork check: hasExoPlayerArtwork=$hasExoPlayerArtwork")
                
                if (hasExoPlayerArtwork) {
                    // PlayerView.artworkDisplayMode is set to OFF (we manage artwork ourselves).
                    // Decode ExoPlayer's artworkData and display it in our audioCoverArtView.
                    val artworkData = player?.mediaMetadata?.artworkData
                    val bitmap = if (artworkData != null) {
                        withContext(Dispatchers.IO) {
                            try {
                                android.graphics.BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size)
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to decode ExoPlayer artworkData")
                                null
                            }
                        }
                    } else null

                    if (bitmap != null) {
                        Timber.d("ExoPlayer embedded artwork decoded (${artworkData?.size} bytes), displaying in audioCoverArtView")
                        audioEmptyStateController?.hide()
                        binding.audioCoverArtView.setImageBitmap(bitmap)
                        binding.audioCoverArtView.isVisible = true
                        coverArtDisplayedForPath = file.path
                    } else {
                        // artworkData missing or decode failed — treat as no artwork
                        val settings = settingsRepository.getSettings().first()
                        if (!settings.searchAudioCoversOnline) {
                            Timber.d("Network file: artwork decode failed, online search disabled — showing empty state")
                            withContext(Dispatchers.Main) {
                                audioEmptyStateController?.show(settings.audioEmptyStateMode)
                                    ?: run { binding.audioCoverArtView.setImageResource(R.drawable.ic_music_note); binding.audioCoverArtView.isVisible = true }
                            }
                        } else {
                            Timber.w("ExoPlayer reports artwork but decode failed, searching online")
                            searchOnlineAndDisplayCover(file)
                        }
                    }
                } else {
                    // ExoPlayer has no embedded artwork
                    val settings = settingsRepository.getSettings().first()
                    if (!settings.searchAudioCoversOnline) {
                        Timber.d("Network file: no artwork, online search disabled — showing empty state")
                        withContext(Dispatchers.Main) {
                            audioEmptyStateController?.show(settings.audioEmptyStateMode)
                                ?: run { binding.audioCoverArtView.setImageResource(R.drawable.ic_music_note); binding.audioCoverArtView.isVisible = true }
                        }
                    } else {
                        Timber.d("ExoPlayer has no artwork, searching online for ${file.name}")
                        searchOnlineAndDisplayCover(file)
                    }
                }
            }
            return
        }
        
        // For local files, use MediaMetadataRetriever.
        // Show empty-state animation immediately as a placeholder — hide it later if artwork is found.
        lifecycleScope.launch {
            val mode = try { settingsRepository.getSettings().first().audioEmptyStateMode } catch (_: Exception) { AudioEmptyStateController.MODE_NONE }
            Timber.d("loadAudioCoverArt[$callId]: showing empty-state immediately (local file), mode=$mode")
            audioEmptyStateController?.show(mode)
        }
        Timber.d("loadAudioCoverArt: Starting cover art search for ${file.name}")
        
        coverArtJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Try to extract embedded cover art using MediaMetadataRetriever
                Timber.d("Extracting embedded cover art for ${file.name}")
                val coverBitmap = withContext(Dispatchers.IO) {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        if (file.path.startsWith("content://")) {
                            retriever.setDataSource(binding.root.context, Uri.parse(file.path))
                        } else {
                            retriever.setDataSource(file.path)
                        }
                        
                        // Try to get embedded picture
                        val embeddedPicture = retriever.embeddedPicture
                        
                        // Debug: check if file has any metadata
                        val hasAudio = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                        val mimeType = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                        Timber.d("MediaMetadataRetriever: hasAudio=$hasAudio, mimeType=$mimeType, embeddedPicture=${embeddedPicture?.size} bytes")
                        
                        if (embeddedPicture != null) {
                            Timber.d("Found embedded cover art, decoding bitmap (${embeddedPicture.size} bytes)")
                            android.graphics.BitmapFactory.decodeByteArray(embeddedPicture, 0, embeddedPicture.size)
                        } else {
                            Timber.d("No embedded cover art found")
                            null
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to extract embedded cover art")
                        null
                    } finally {
                        retriever.release()
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (coverBitmap != null) {
                        // Show embedded cover
                        Timber.d("loadAudioCoverArt[$callId]: ✅ EMBEDDED cover found, displaying")
                        Timber.d("loadAudioCoverArt[$callId]: BEFORE setImageBitmap - audioCoverArtView.isVisible=${binding.audioCoverArtView.isVisible}")
                        audioEmptyStateController?.hide()
                        binding.audioCoverArtView.setImageBitmap(coverBitmap)
                        binding.audioCoverArtView.isVisible = true
                        coverArtDisplayedForPath = file.path
                        Timber.d("loadAudioCoverArt[$callId]: AFTER setImageBitmap - audioCoverArtView.isVisible=${binding.audioCoverArtView.isVisible}")
                    } else {
                        // No embedded cover, check if online search is enabled
                        val settings = settingsRepository.getSettings().first()
                        if (!settings.searchAudioCoversOnline) {
                            Timber.d("loadAudioCoverArt[$callId]: No embedded cover, online search disabled - showing empty state")
                            audioEmptyStateController?.show(settings.audioEmptyStateMode)
                                ?: run {
                                    binding.audioCoverArtView.setImageResource(R.drawable.ic_music_note)
                                    binding.audioCoverArtView.isVisible = true
                                }
                            return@withContext
                        }

                        // ШАГ 2.5: Проверяем локальный кэш
                        val cached = withContext(Dispatchers.IO) {
                            audioMetadataCacheRepository.readMetadata(file.name)
                        }
                        if (cached != null) {
                            Timber.d("loadAudioCoverArt[$callId]: ✅ LOCAL CACHE hit for ${file.name}")
                            if (cached.coverFile != null) {
                                audioEmptyStateController?.hide()
                                Glide.with(binding.audioCoverArtView.context)
                                    .load(cached.coverFile)
                                    .error(R.drawable.ic_music_note)
                                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                                    .into(binding.audioCoverArtView)
                                binding.audioCoverArtView.isVisible = true
                                coverArtDisplayedForPath = file.path
                                callback.onAudioMetadataLoaded(
                                    com.sza.fastmediasorter.domain.model.AudioMetadata(
                                        trackName   = cached.trackName,
                                        artistName  = cached.artistName,
                                        albumName   = cached.albumName,
                                        releaseYear = cached.releaseYear,
                                        coverArtUrl = cached.coverArtUrl
                                    )
                                )
                                return@withContext
                            }
                            if (cached.coverArtUrl != null) {
                                audioEmptyStateController?.hide()
                                binding.audioCoverArtView.isVisible = true
                                Glide.with(binding.audioCoverArtView.context)
                                    .load(cached.coverArtUrl)
                                    .error(R.drawable.ic_music_note)
                                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                                    .into(binding.audioCoverArtView)
                                coverArtDisplayedForPath = file.path
                                return@withContext
                            }
                        }

                        // Online search enabled, try it
                        Timber.d("loadAudioCoverArt[$callId]: ❌ NO embedded cover, searching online")
                        
                        val metadata = withContext(Dispatchers.IO) {
                            Timber.d("loadAudioCoverArt[$callId]: Calling searchAudioCoverUseCase")
                            searchAudioCoverUseCase(file.name, file.path)
                        }
                        
                        val coverUrl = metadata?.coverArtUrl
                        if (coverUrl != null) {
                            // Save metadata for display
                            callback.onAudioMetadataLoaded(metadata)

                            // Сохраняем в локальный кэш (если включено)
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    val shouldSave = settingsRepository.getSettings().first().saveAudioMetadataLocally
                                    if (!shouldSave) return@launch
                                    val ext = if (coverUrl.contains(".png", ignoreCase = true)) "png" else "jpg"
                                    val imageBytes = downloadImageBytes(coverUrl)
                                    audioMetadataCacheRepository.saveMetadata(
                                        file.name,
                                        AudioMetadataSaveData(
                                            trackName      = metadata.trackName,
                                            artistName     = metadata.artistName,
                                            albumName      = metadata.albumName,
                                            releaseYear    = metadata.releaseYear,
                                            coverArtUrl    = coverUrl,
                                            coverExtension = if (imageBytes != null) ext else null
                                        )
                                    )
                                    if (imageBytes != null) {
                                        audioMetadataCacheRepository.saveCover(file.name, imageBytes, ext)
                                    }
                                } catch (e: Exception) {
                                    Timber.d(e, "AudioMetadataCache: save failed for %s", file.name)
                                }
                            }

                            Timber.d("loadAudioCoverArt[$callId]: ✅ ONLINE cover found: $coverUrl")
                            Timber.d("loadAudioCoverArt[$callId]: BEFORE Glide.load - audioCoverArtView.isVisible=${binding.audioCoverArtView.isVisible}")
                            // Hide any running animation before showing cover
                            audioEmptyStateController?.hide()
                            // Show view now, load without placeholder to prevent flicker
                            binding.audioCoverArtView.isVisible = true
                            Timber.d("loadAudioCoverArt[$callId]: AFTER isVisible=true - audioCoverArtView.isVisible=${binding.audioCoverArtView.isVisible}")
                            
                            val capturedMode = settings.audioEmptyStateMode
                            val request = Glide.with(binding.audioCoverArtView.context)
                                .load(coverUrl)
                                .error(R.drawable.ic_music_note)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                            
                            // Apply memory-aware optimizations for LOW tier devices
                            if (memoryTier == MemoryTier.LOW) {
                                request
                                    .format(DecodeFormat.PREFER_RGB_565)
                                    .dontAnimate()
                                    .override(512, 512) // Limit audio cover size for LOW memory
                            }
                            
                            request
                                .listener(object : RequestListener<Drawable> {
                                    override fun onLoadFailed(
                                        e: GlideException?,
                                        model: Any?,
                                        target: Target<Drawable>,
                                        isFirstResource: Boolean
                                    ): Boolean {
                                        Timber.w(e, "Cover art load failed, showing empty state")
                                        val controller = audioEmptyStateController
                                        if (controller != null) {
                                            controller.show(capturedMode)
                                            return true
                                        }
                                        return false // fallback: let Glide show error drawable
                                    }
                                    
                                    override fun onResourceReady(
                                        resource: Drawable,
                                        model: Any,
                                        target: Target<Drawable>?,
                                        dataSource: DataSource,
                                        isFirstResource: Boolean
                                    ): Boolean {
                                        Timber.d("Cover art loaded successfully from $dataSource")
                                        coverArtDisplayedForPath = file.path
                                        return false // Let Glide handle displaying
                                    }
                                })
                                .into(binding.audioCoverArtView)
                        } else {
                            Timber.d("No cover art found online, showing empty state for ${file.name}")
                            audioEmptyStateController?.show(settingsRepository.getSettings().first().audioEmptyStateMode)
                                ?: run {
                                    binding.audioCoverArtView.setImageResource(R.drawable.ic_music_note)
                                    binding.audioCoverArtView.isVisible = true
                                }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load audio cover art for ${file.name}")
                withContext(Dispatchers.Main) {
                    val mode = try { settingsRepository.getSettings().first().audioEmptyStateMode } catch (_: Exception) { AudioEmptyStateController.MODE_NONE }
                    audioEmptyStateController?.show(mode)
                        ?: run {
                            binding.audioCoverArtView.setImageResource(R.drawable.ic_music_note)
                            binding.audioCoverArtView.isVisible = true
                        }
                }
            }
        }
    }
    
    /**
     * Search for cover art online and display it
     */
    private fun searchOnlineAndDisplayCover(file: MediaFile) {
        val callId = System.currentTimeMillis()
        Timber.w("searchOnlineAndDisplayCover[$callId]: START for ${file.name}")
        
        lifecycleScope.launch(Dispatchers.IO) {
            val mode = try {
                settingsRepository.getSettings().first().audioEmptyStateMode
            } catch (_: Exception) {
                AudioEmptyStateController.MODE_NONE
            }
            try {
                // ШАГ 2.5: Проверяем локальный кэш (сетевые файлы тоже кэшируем по имени)
                val cached = audioMetadataCacheRepository.readMetadata(file.name)
                if (cached != null) {
                    Timber.w("searchOnlineAndDisplayCover[$callId]: ✅ LOCAL CACHE hit for ${file.name}")
                    withContext(Dispatchers.Main) {
                        if (cached.coverFile != null) {
                            audioEmptyStateController?.hide()
                            Glide.with(binding.audioCoverArtView.context)
                                .load(cached.coverFile)
                                .error(R.drawable.ic_music_note)
                                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                                .into(binding.audioCoverArtView)
                            binding.audioCoverArtView.isVisible = true
                            coverArtDisplayedForPath = file.path
                            callback.onAudioMetadataLoaded(
                                com.sza.fastmediasorter.domain.model.AudioMetadata(
                                    trackName   = cached.trackName,
                                    artistName  = cached.artistName,
                                    albumName   = cached.albumName,
                                    releaseYear = cached.releaseYear,
                                    coverArtUrl = cached.coverArtUrl
                                )
                            )
                            return@withContext
                        }
                        if (cached.coverArtUrl != null) {
                            audioEmptyStateController?.hide()
                            binding.audioCoverArtView.isVisible = true
                            Glide.with(binding.audioCoverArtView.context)
                                .load(cached.coverArtUrl)
                                .error(R.drawable.ic_music_note)
                                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                                .into(binding.audioCoverArtView)
                            coverArtDisplayedForPath = file.path
                            return@withContext
                        }
                    }
                    return@launch
                }

                val metadata = searchAudioCoverUseCase(file.name, file.path)
                val coverUrl = metadata?.coverArtUrl

                withContext(Dispatchers.Main) {
                    if (coverUrl != null) {
                        // Save metadata for display
                        callback.onAudioMetadataLoaded(metadata)

                        // Сохраняем в локальный кэш (если включено)
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val shouldSave = settingsRepository.getSettings().first().saveAudioMetadataLocally
                                if (!shouldSave) return@launch
                                val ext = if (coverUrl.contains(".png", ignoreCase = true)) "png" else "jpg"
                                val imageBytes = downloadImageBytes(coverUrl)
                                audioMetadataCacheRepository.saveMetadata(
                                    file.name,
                                    AudioMetadataSaveData(
                                        trackName      = metadata.trackName,
                                        artistName     = metadata.artistName,
                                        albumName      = metadata.albumName,
                                        releaseYear    = metadata.releaseYear,
                                        coverArtUrl    = coverUrl,
                                        coverExtension = if (imageBytes != null) ext else null
                                    )
                                )
                                if (imageBytes != null) {
                                    audioMetadataCacheRepository.saveCover(file.name, imageBytes, ext)
                                }
                            } catch (e: Exception) {
                                Timber.d(e, "AudioMetadataCache: save failed for %s", file.name)
                            }
                        }

                        Timber.w("searchOnlineAndDisplayCover[$callId]: ✅ Found URL: $coverUrl")
                        Timber.w("searchOnlineAndDisplayCover[$callId]: BEFORE Glide.load - audioCoverArtView.isVisible=${binding.audioCoverArtView.isVisible}")
                        // Hide any running animation before showing cover
                        audioEmptyStateController?.hide()
                        binding.audioCoverArtView.isVisible = true
                        // Load without placeholder to avoid flicker
                        val request = Glide.with(binding.audioCoverArtView.context)
                            .load(coverUrl)
                            .error(R.drawable.ic_music_note)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                        
                        // Apply memory-aware optimizations for LOW tier devices
                        if (memoryTier == MemoryTier.LOW) {
                            request
                                .format(DecodeFormat.PREFER_RGB_565)
                                .dontAnimate()
                                .override(512, 512) // Limit audio cover size for LOW memory
                        }
                        
                        request
                            .listener(object : RequestListener<Drawable> {
                                override fun onLoadFailed(
                                    e: GlideException?,
                                    model: Any?,
                                    target: Target<Drawable>,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    Timber.w(e, "searchOnlineAndDisplayCover[$callId]: ❌ Glide load FAILED")
                                    val controller = audioEmptyStateController
                                    if (controller != null) {
                                        controller.show(mode)
                                        return true
                                    }
                                    binding.audioCoverArtView.setImageResource(R.drawable.ic_music_note)
                                    return true
                                }
                                
                                override fun onResourceReady(
                                    resource: Drawable,
                                    model: Any,
                                    target: Target<Drawable>?,
                                    dataSource: DataSource,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    Timber.w("searchOnlineAndDisplayCover[$callId]: ✅ Glide loaded from $dataSource")
                                    coverArtDisplayedForPath = file.path
                                    return false
                                }
                            })
                            .into(binding.audioCoverArtView)
                    } else {
                        Timber.w("searchOnlineAndDisplayCover[$callId]: ❌ NO cover found, showing empty state")
                        audioEmptyStateController?.show(mode)
                            ?: run {
                                binding.audioCoverArtView.setImageResource(R.drawable.ic_music_note)
                            }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "searchOnlineAndDisplayCover[$callId]: ❌ EXCEPTION")
                withContext(Dispatchers.Main) {
                    audioEmptyStateController?.show(mode)
                        ?: run {
                            binding.audioCoverArtView.setImageResource(R.drawable.ic_music_note)
                        }
                }
            }
        }
    }

    private suspend fun downloadImageBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.bytes()
            }
        } catch (e: Exception) {
            Timber.d(e, "downloadImageBytes: failed for %s", url)
            null
        }
    }

    fun updateButtonVisibility() {
        lifecycleScope.launch(Dispatchers.IO) {
            val settings = settingsRepository.getSettings().first()
            withContext(Dispatchers.Main) {
                // Only update if currently showing an image (not PDF/Video)
                if (binding.imageView.isVisible || binding.photoView.isVisible) {
                    // Only HIDE buttons if feature is disabled in settings
                    // CommandPanelController controls showing buttons based on orientation
                    if (!settings.enableTranslation) binding.btnTranslateImageCmd.isVisible = false
                    if (!settings.enableGoogleLens) binding.btnGoogleLensImageCmd.isVisible = false
                    if (!settings.enableOcr) binding.btnOcrImageCmd.isVisible = false
                    
                    // Hide deprecated overlay buttons
                    safeViews.btnTranslateImage.isVisible = false
                    safeViews.btnGoogleLensImage.isVisible = false
                    safeViews.btnOcrImage.isVisible = false
                    
                    Timber.d("ImageLoadingManager: Force updated button visibility. Lens=${settings.enableGoogleLens}, OCR=${settings.enableOcr}")
                }
            }
        }
    }
    
    /**
     * Clear Glide memory cache to free up RAM.
     * Should be called periodically during slideshow to prevent OOM.
     * Every 100 clears, triggers System.gc() for aggressive memory reclamation.
     */
    /**
     * Logs current memory usage and Glide cache statistics for debugging memory leaks.
     * Call this before/after image loading to track memory consumption patterns.
     * 
     * @param context Contextual description for the log entry (e.g., "BEFORE displayImage", "AFTER onResourceReady")
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
            
            // Preload jobs count
            val activePreloadJobs = synchronized(preloadJobs) { preloadJobs.size }
            
            // Single comprehensive log line for easier filtering
            Timber.i(
                "MEMORY_DEBUG [$context] | " +
                "Heap: ${usedMemory}MB/${maxMemory}MB (${percentUsed}%) | " +
                "Native: ${nativeHeapAllocated}MB/${nativeHeapSize}MB (free: ${nativeHeapFree}MB) | " +
                "Preload Jobs: $activePreloadJobs"
            )
        } catch (e: Exception) {
            Timber.w(e, "MEMORY_DEBUG: Failed to log memory stats")
        }
    }
    
    fun clearMemoryCache() {
        try {
            Glide.get(binding.root.context).clearMemory()
            
            // Increment global counter and trigger GC every 100 clears
            cacheClears++
            if (cacheClears >= 100) {
                Timber.w("ImageLoadingManager: Memory cache cleared 100 times, triggering System.gc()")
                System.gc()
                cacheClears = 0
            } else {
                Timber.d("ImageLoadingManager: Memory cache cleared (count: $cacheClears/100)")
            }
        } catch (e: Exception) {
            Timber.w(e, "ImageLoadingManager: Failed to clear memory cache")
        }
    }
    
    companion object {
        // Global counter for cache clears across all instances
        // System.gc() is called every 100 clears to aggressively reclaim memory
        private var cacheClears = 0
        
        // Counter for preload operations to periodically log memory stats
        // Logs memory state every 10 preloads to track accumulation patterns
        private var preloadCounter = 0
    }
}
