package com.sza.fastmediasorter.ui.player

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
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
import com.sza.fastmediasorter.core.util.HeifSupportUtils
import com.sza.fastmediasorter.core.util.MemoryTier
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.cloud.glide.CloudThumbnailData
import com.sza.fastmediasorter.data.network.glide.NetworkFileData
import com.sza.fastmediasorter.data.repository.AudioMetadataCacheRepository
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.SearchAudioCoverUseCase
import com.sza.fastmediasorter.ui.image.ImageDisplayUtils
import com.sza.fastmediasorter.ui.player.helpers.AnimatedImageController
import com.sza.fastmediasorter.ui.player.helpers.AudioEmptyStateController
import com.sza.fastmediasorter.ui.player.helpers.AudioInfoDisplayHelper
import com.sza.fastmediasorter.ui.player.helpers.PanelStereoSingleEyeNotifier
import com.sza.fastmediasorter.ui.player.helpers.PlayerBindingSafeViews
import com.sza.fastmediasorter.ui.player.helpers.WindowMetricsCompat
import com.sza.fastmediasorter.ui.player.render.DualSurfaceStaticImageRenderer
import com.sza.fastmediasorter.ui.player.render.StaticImageRenderer
import kotlinx.coroutines.Dispatchers
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
    private val panelStereoSingleEyeNotifier: PanelStereoSingleEyeNotifier,
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
        fun isImageCropEditMode(): Boolean
        fun setAnimatedBadgeVisible(visible: Boolean)
        /** S0107: Called when a static (non-GIF) image is fully loaded; null on load failure or video transition. */
        fun onStaticImageLoaded(bitmap: android.graphics.Bitmap?) {}
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

    /**
     * Called when the user performs a pinch-zoom gesture on the current image or GIF.
     * Injected from outside after construction; wires to FilenameOverlayAutoHideManager.
     * Load-time PhotoView scale events are filtered before invoking this callback.
     */
    var onZoomInteraction: (() -> Unit)? = null

    /**
     * True once Glide has successfully delivered the current image to PhotoView.
     * Reset on every new displayImage() call to suppress load-time scale events.
     */
    private var isPhotoViewImageLoaded = false

    private var dynamicBackgroundProcessor: DynamicBackgroundProcessor? = null
    private var isDynamicBackgroundEnabled: Boolean = false
    private val staticImageRenderer: StaticImageRenderer = DualSurfaceStaticImageRenderer(
        surfaceA = binding.photoView,
        surfaceB = binding.photoViewSurfaceB,
        panelStereoSingleEyeNotifier = panelStereoSingleEyeNotifier
    )

    private val imagePreloadHelper: ImagePreloadHelper by lazy {
        ImagePreloadHelper(
            binding = binding,
            lifecycleScope = lifecycleScope,
            memoryTier = memoryTier,
            callback = object : ImagePreloadHelper.Callback {
                override fun getAdjacentFiles() = callback.getAdjacentFiles()
                override fun getCurrentResource() = callback.getCurrentResource()
            }
        )
    }

    private val audioInfoHelper: AudioInfoDisplayHelper by lazy {
        AudioInfoDisplayHelper(
            binding = binding,
            lifecycleScope = lifecycleScope,
            callback = object : AudioInfoDisplayHelper.Callback {
                override fun getString(resId: Int) = callback.getString(resId)
                override fun getExoPlayer() = callback.getExoPlayer()
            }
        )
    }

    private val audioCoverArtLoader: AudioCoverArtLoader by lazy {
        AudioCoverArtLoader(
            binding = binding,
            lifecycleScope = lifecycleScope,
            settingsRepository = settingsRepository,
            searchAudioCoverUseCase = searchAudioCoverUseCase,
            audioMetadataCacheRepository = audioMetadataCacheRepository,
            okHttpClient = okHttpClient,
            memoryTier = memoryTier,
            callback = object : AudioCoverArtLoader.Callback {
                override fun onAudioMetadataLoaded(metadata: com.sza.fastmediasorter.domain.model.AudioMetadata) =
                    callback.onAudioMetadataLoaded(metadata)
            }
        )
    }

    fun setAudioEmptyStateController(controller: AudioEmptyStateController) {
        audioCoverArtLoader.setAudioEmptyStateController(controller)
    }

    /** Delegates slideshow bias to the preload helper's prefetch queue. */
    fun setSlideshowBias(enabled: Boolean) {
        imagePreloadHelper.setSlideshowBias(enabled)
    }

    /**
     * Set stereo crop mode for 3D images. Delegates to the underlying renderer.
     * SBS crops to right half, OU crops to bottom half, MONO = no crop.
     */
    fun setStereoMode(mode: com.sza.fastmediasorter.domain.model.StereoMode) {
        staticImageRenderer.setStereoMode(mode)
    }

    /**
     * Toggle the panel single-eye crop master flag for 3D images.
     * Caller (PlayerManagerInitializer) is responsible for re-displaying the current image
     * so the toggle takes effect without a fresh navigation.
     */
    fun setPanelStereoSingleEyeEnabled(enabled: Boolean) {
        staticImageRenderer.setPanelStereoSingleEyeEnabled(enabled)
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
    fun triggerVideoBackground(bitmap: android.graphics.Bitmap, isPlaceholder: Boolean) {
        if (!isDynamicBackgroundEnabled) return
        val w = currentDeviceWidth.takeIf { it > 0 } ?: return
        val h = currentDeviceHeight.takeIf { it > 0 } ?: return
        Timber.d("ImageLoadingManager: triggerVideoBackground frame=${bitmap.width}x${bitmap.height} screen=${w}x${h} placeholder=$isPlaceholder")
        // processFromBitmap internally uses the backgroundView dimensions via process(),
        // which now receives the view size — but for video we pass screen dims as fallback;
        // the processor will use backgroundView.width/height if available via its own layout.
        dynamicBackgroundProcessor?.processFromBitmap(bitmap, w, h)
        binding.ivDynamicBackground.contentDescription = if (isPlaceholder) {
            binding.root.context.getString(R.string.poster_thumbnail_unavailable)
        } else {
            null
        }
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
        
        // Cancel all preload jobs and clear prefetch queue
        imagePreloadHelper.cleanup()
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
    
    // Display image in ImageView or PhotoView based on settings
    fun displayImage(path: String) {
        isInImageDisplayMode = true
        // Reset the zoom-ready flag so load-time PhotoView scale events are not
        // treated as intentional user zoom gestures on the new image.
        isPhotoViewImageLoaded = false
        Timber.i("ImageLoadingManager.displayImage: START - path=$path")
        
        // Log memory state BEFORE loading new image
        logMemoryStats("BEFORE displayImage")
        
        // NOTE: Do NOT clear imageView/photoView before loading new image!
        // This causes a brief black screen flash between slides.
        // Glide will automatically replace the image when the new one is ready.
        // Memory cleanup happens when the new request completes and replaces the old bitmap.
        
        // Smart cancellation: only cancel jobs for files no longer adjacent to the new position
        val nextAdjacentPaths = callback.getAdjacentFiles().map { it.path }.toSet()
        val cancelledCount = imagePreloadHelper.cancelStaleJobsForPaths(nextAdjacentPaths)
        Timber.d("ImageLoadingManager.displayImage: Cancelled $cancelledCount stale preload job(s), kept ${imagePreloadHelper.preloadJobCount} still-useful")
        
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
        audioCoverArtLoader.hideEmptyState()
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

        // Pre-flight: HEIC/HEIF needs API 28+, AVIF needs API 31+. Show a clear message on
        // unsupported devices instead of letting Glide fail silently.
        val pathExtension = path.substringAfterLast('.', "").lowercase()
        if (!HeifSupportUtils.isSupported(pathExtension)) {
            loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
            loadingIndicatorHandler.removeCallbacks(hideLoadingSafetyRunnable)
            binding.progressBar.isVisible = false
            val minVersion = HeifSupportUtils.minimumAndroidVersion(pathExtension) ?: pathExtension
            Timber.w("ImageLoadingManager: ${pathExtension.uppercase()} not supported on this device (requires $minVersion)")
            callback.showError(
                binding.root.context.getString(
                    R.string.heic_not_supported_on_device,
                    pathExtension.uppercase(),
                    minVersion
                )
            )
            return
        }

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
                    
                    // Scale change listener: detect real user zoom gestures.
                    // isPhotoViewImageLoaded guards against load-time auto-scale events
                    // fired by PhotoView when the image first arrives from Glide.
                    // scaleFactor is the per-frame delta (1.0 = no change); a meaningful
                    // pinch gesture produces values well outside a tiny rounding band.
                    setOnScaleChangeListener { scaleFactor, focusX, focusY ->
                        Timber.d("GESTURE_DEBUG: Scale change - factor=${"%.2f".format(scaleFactor)}, focus=(${"%.0f".format(focusX)}, ${"%.0f".format(focusY)})")
                        if (isPhotoViewImageLoaded && kotlin.math.abs(scaleFactor - 1.0f) > 0.02f) {
                            // Real user pinch-zoom — notify overlay manager
                            onZoomInteraction?.invoke()
                        }
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
            val isCropEditMode = callback.isImageCropEditMode()
            currentCropSetting = settings.cropImagesToFullscreen && !isCropEditMode
            Timber.d("S0127: ImageLoadingManager.displayImage cropSetting=${settings.cropImagesToFullscreen} isCropEditMode=$isCropEditMode → effective=$currentCropSetting")
            currentIsFullscreenOrSlideshow = isFullscreenOrSlideshow
            currentDeviceWidth = deviceWidth
            currentDeviceHeight = deviceHeight
            currentTargetView = targetView

            // Apply initial scale type based on settings
            // Note: This is a heuristic - we don't have image dimensions yet
            // The actual scale type will be re-evaluated in onResourceReady when we have image dims
            val initialScaleType = if (currentCropSetting && isFullscreenOrSlideshow) {
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
                        callback.showError(binding.root.context.getString(R.string.error_image_load_failed), e)
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
                // Mark image as fully loaded so PhotoView scale events are now user-driven
                isPhotoViewImageLoaded = true
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

                    // S0107: expose loaded bitmap for draw overlay merge
                    val loadedBitmap = (resource as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    callback.onStaticImageLoaded(loadedBitmap)

                    // Trigger dynamic background extension if enabled.
                    // Guard with isInImageDisplayMode: a stale Glide request that completes
                    // after the user has navigated to video would otherwise re-show the
                    // previous image's blurred background over the video pillarbox areas.
                    if (isDynamicBackgroundEnabled && isInImageDisplayMode) {
                        // Use the ImageView's actual laid-out dimensions, not the full screen size.
                        // The ImageView may be smaller than the screen when the command panel is
                        // visible, so using screen dimensions would produce wrong imgLeft/imgTop
                        // offsets and draw bars in the wrong positions.
                        val targetView = currentTargetView
                        val viewW = targetView?.width?.takeIf { it > 0 } ?: currentDeviceWidth
                        val viewH = targetView?.height?.takeIf { it > 0 } ?: currentDeviceHeight
                        Timber.d("DynamicBg: triggering process view=${viewW}x${viewH} screen=${currentDeviceWidth}x${currentDeviceHeight}")
                        dynamicBackgroundProcessor?.process(
                            drawable = resource,
                            screenWidth = viewW,
                            screenHeight = viewH
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
                    callback.showError(binding.root.context.getString(R.string.error_gif_load_failed), e)
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
                // Mark image as fully loaded so PhotoView scale events are now user-driven
                isPhotoViewImageLoaded = true
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

    private fun isNonCriticalNetworkImageError(exception: GlideException?): Boolean =
        ImageLoadingDiagnostics.isNonCriticalNetworkImageError(exception)

    fun preloadNextImageIfNeeded() = imagePreloadHelper.preloadNextImageIfNeeded()

    fun showAudioFileInfo(file: MediaFile?) = audioInfoHelper.showAudioFileInfo(file)

    fun updateAudioFormatInfo() = audioInfoHelper.updateAudioFormatInfo()

    fun loadAudioCoverArt(file: MediaFile) = audioCoverArtLoader.loadAudioCoverArt(file)

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
    private fun logMemoryStats(context: String) =
        ImageLoadingDiagnostics.logMemoryStats(context, imagePreloadHelper.preloadJobCount)
    
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
    }
}
