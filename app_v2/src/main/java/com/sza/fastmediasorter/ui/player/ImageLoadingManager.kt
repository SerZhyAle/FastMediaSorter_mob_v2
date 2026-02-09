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
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.data.cloud.glide.CloudThumbnailData
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.cloud.CloudPathParser
import com.sza.fastmediasorter.data.network.glide.NetworkFileData
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.SearchAudioCoverUseCase
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
    private val lifecycleScope: LifecycleCoroutineScope,
    private val loadingIndicatorHandler: Handler,
    private val showLoadingIndicatorRunnable: Runnable,
    private val preloadJobs: MutableList<Job>,
    private val callback: ImageLoadingCallback
) {
    
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
    }
    
    /**
     * Cleanup all resources - cancel Glide requests and pending handlers.
     * Called from PlayerLifecycleManager.onDestroy() to prevent memory leaks.
     */
    fun cleanup() {
        Timber.d("ImageLoadingManager: Cleaning up resources")
        
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
        
        Timber.d("ImageLoadingManager: Cleanup complete")
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
        Timber.i("ImageLoadingManager.displayImage: START - path=$path")
        
        // Cancel any pending Glide requests from previous file immediately
        Glide.with(binding.root.context).clear(binding.imageView)
        Glide.with(binding.root.context).clear(binding.photoView)
        
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
        binding.playerView.isVisible = false
        
        // Hide audio-related views
        Timber.w("displayImage: HIDING audioCoverArtView (was ${binding.audioCoverArtView.isVisible})")
        binding.audioCoverArtView.isVisible = false
        binding.audioTouchZonesOverlay.isVisible = false
        binding.audioInfoOverlay.isVisible = false
        binding.pdfControlsLayout.isVisible = false
        binding.textViewerContainer.isVisible = false
        
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
        binding.epubControlsLayout.isVisible = false
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
            // During slideshow, force size limit to prevent OOM crashes
            val isSlideshowActive = callback.isSlideshowActive()
            val usePhotoView = settings.loadFullSizeImages && currentFile != null && !isSlideshowActive
            
            Timber.d("ImageLoadingManager.displayImage: enableTranslation=${settings.enableTranslation}")
            Timber.w("TOUCH_DEBUG: ImageLoadingManager.displayImage - usePhotoView=$usePhotoView, loadFullSizeImages=${settings.loadFullSizeImages}")
            
            // Switch visibility between ImageView and PhotoView
            binding.imageView.isVisible = !usePhotoView
            binding.photoView.isVisible = usePhotoView
            Timber.w("TOUCH_DEBUG: View visibility set - imageView.isVisible=${binding.imageView.isVisible}, photoView.isVisible=${binding.photoView.isVisible}")
            
            // Configure PhotoView gestures based on loadFullSizeImages setting
            if (usePhotoView) {
                binding.photoView.apply {
                    if (settings.loadFullSizeImages) {
                        // Full gestures mode: zoom, pan, rotation, double-tap
                        // PhotoView supports all gestures by default when scale limits allow it
                        minimumScale = 1.0f  // Original size
                        mediumScale = 2.0f   // Not used (we override tap gestures)
                        maximumScale = 5.0f  // Maximum zoom
                        
                        Timber.w("GESTURE_CONFIG: PhotoView configured for CUSTOM GESTURES mode")
                        Timber.w("GESTURE_CONFIG: - Zoom: ENABLED (min=1.0x, max=5.0x)")
                        Timber.w("GESTURE_CONFIG: - Double-tap: Command Panel=2x (REG-3100), Fullscreen=3x")
                        Timber.w("GESTURE_CONFIG: - Long press: Command Panel=2x, Fullscreen=3x")
                        Timber.w("GESTURE_CONFIG: - Rotation: ENABLED (two-finger rotate)")
                        Timber.w("GESTURE_CONFIG: - Pan: ENABLED (after zoom)")
                        Timber.w("GESTURE_CONFIG: - Pinch zoom: ENABLED (gradual zoom)")
                        
                        // NOTE: OnDoubleTapListener is set once in setupGestureDetector()
                        // Do NOT reset it here - it handles touch zones + custom zoom logic
                    } else {
                        // Rotation-only mode: disable zoom by setting all scales to 1.0f
                        // PhotoView rotation is always available (two-finger gesture)
                        minimumScale = 1.0f
                        mediumScale = 1.0f  // Disable zoom on double-tap
                        maximumScale = 1.0f // Disable pinch zoom
                        
                        Timber.w("GESTURE_CONFIG: PhotoView configured for ROTATION-ONLY mode")
                        Timber.w("GESTURE_CONFIG: - Zoom: DISABLED (all scales locked to 1.0x)")
                        Timber.w("GESTURE_CONFIG: - Rotation: ENABLED (two-finger rotate)")
                        Timber.w("GESTURE_CONFIG: - Pan: DISABLED (no zoom = no pan)")
                        Timber.w("GESTURE_CONFIG: - Double-tap: DISABLED (mediumScale = 1.0x)")
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
            binding.btnTranslateImage.isVisible = false
            binding.btnGoogleLensImage.isVisible = false
            binding.btnOcrImage.isVisible = false
            
            Timber.d("ImageLoadingManager.displayImage: btnTranslateImage.isVisible=${binding.btnTranslateImage.isVisible}, btnTranslateImage.visibility=${binding.btnTranslateImage.visibility}")
            
            // ALWAYS hide View-based touch zone overlays
            // TouchZoneGestureManager handles ALL touch detection via gesture listeners
            // - Command panel mode: 3-zone via handleCommandPanelTouchZones()
            // - Fullscreen mode: 9-zone via handleTouchZone()
            binding.touchZonesOverlay.isVisible = false
            binding.touchZones3Overlay.isVisible = false
            
            // Determine target view for image loading
            val targetView = if (usePhotoView) binding.photoView else binding.imageView
            
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
                loadCloudImage(path, currentFile, targetView, effectiveLoadFullSize)
            } else if (currentFile != null && 
                (actualResourceType == ResourceType.SMB || actualResourceType == ResourceType.SFTP || actualResourceType == ResourceType.FTP)) {
                loadNetworkImage(path, currentFile, resource, targetView, effectiveLoadFullSize)
            } else {
                loadLocalImage(path, currentFile, targetView, effectiveLoadFullSize)
            }

            callback.updateSlideShow()
        }
    }
    
    private suspend fun loadCloudImage(
        path: String,
        currentFile: MediaFile,
        targetView: android.widget.ImageView,
        loadFullSize: Boolean
    ) {
        // Detect cloud provider from path: cloud://googledrive/, cloud://onedrive/, cloud://dropbox/
        val provider = when {
            path.contains("googledrive", ignoreCase = true) || path.contains("google_drive", ignoreCase = true) -> CloudProvider.GOOGLE_DRIVE
            path.contains("onedrive", ignoreCase = true) -> CloudProvider.ONEDRIVE
            path.contains("dropbox", ignoreCase = true) -> CloudProvider.DROPBOX
            else -> CloudProvider.GOOGLE_DRIVE // default fallback
        }
        
        // Extract file ID from cloud path
        // For Google Drive/OneDrive: cloud://google_drive/FILE_ID -> FILE_ID
        // For Dropbox: cloud:/dropbox/folder/file.jpg -> /folder/file.jpg
        val fileId = when (provider) {
            CloudProvider.DROPBOX -> {
                // Dropbox needs full path starting with /
                val dropboxPath = path.substringAfter("cloud:/dropbox")
                if (dropboxPath.startsWith("/")) dropboxPath else "/$dropboxPath"
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
        
        Timber.d("ImageLoadingManager: Created CloudThumbnailData with provider=$provider, loadFullImage=true")
        
        val glideRequest = Glide.with(binding.root.context)
            .load(thumbnailData)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)  // Cache decoded image, not source stream
            .priority(Priority.IMMEDIATE)
        
        // Apply size limit if loadFullSizeImages is false (limit to 1920px max dimension)
        val finalRequest = if (!loadFullSize) {
            Timber.d("ImageLoadingManager: Loading cloud image with size limit: 1920px max dimension")
            glideRequest.override(1920, 1920)
        } else {
            // Load original size for zooming
            Timber.d("ImageLoadingManager: Loading cloud image at original size (no limit)")
            glideRequest
        }
        
        finalRequest
            .listener(createGlideListener())
            .into(targetView)
    }
    
    private suspend fun loadNetworkImage(
        path: String,
        currentFile: MediaFile,
        resource: com.sza.fastmediasorter.domain.model.MediaResource?,
        targetView: android.widget.ImageView,
        loadFullSize: Boolean
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
        
        val glideRequest = Glide.with(binding.root.context)
            .load(networkData)
            .signature(ObjectKey(cacheKey))
            .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache both source and decoded for persistence
        
        // Apply size limit if loadFullSizeImages is false
        val finalRequest = if (!loadFullSize) {
            // Limit to screen size to save memory
            val bounds = callback.getWindowManager().currentWindowMetrics.bounds
            val screenWidth = bounds.width()
            val screenHeight = bounds.height()
            Timber.d("ImageLoadingManager: Loading image with screen size limit: ${screenWidth}x${screenHeight}")
            glideRequest.override(screenWidth, screenHeight)
        } else {
            // Load original size for zooming
            Timber.d("ImageLoadingManager: Loading image at original size (no limit)")
            glideRequest
        }
        
        finalRequest
            .listener(createGlideListener())
            .into(targetView)
    }
    
    private suspend fun loadLocalImage(
        path: String,
        currentFile: MediaFile?,
        targetView: android.widget.ImageView,
        loadFullSize: Boolean
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
        
        val data = if (path.startsWith("content://")) {
            Uri.parse(path)
        } else {
            File(path)
        }
        val cacheKey = "${path}_${currentFile?.size}"
        
        val glideRequest = Glide.with(binding.root.context)
            .load(data)
            .signature(ObjectKey(cacheKey))
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
        
        // Apply size limit if loadFullSizeImages is false
        val finalRequest = if (!loadFullSize) {
            // Limit to screen size to save memory
            val bounds = callback.getWindowManager().currentWindowMetrics.bounds
            val screenWidth = bounds.width()
            val screenHeight = bounds.height()
            Timber.d("ImageLoadingManager: Loading local image with screen size limit: ${screenWidth}x${screenHeight}")
            glideRequest.override(screenWidth, screenHeight)
        } else {
            // Load original size for zooming
            Timber.d("ImageLoadingManager: Loading local image at original size (no limit)")
            glideRequest
        }
        
        finalRequest
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
                loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
                loadingIndicatorHandler.removeCallbacks(hideLoadingSafetyRunnable)
                if (!callback.isDestroyed()) {
                    binding.progressBar.isVisible = false
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
                loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
                loadingIndicatorHandler.removeCallbacks(hideLoadingSafetyRunnable)
                if (!callback.isDestroyed()) {
                    binding.progressBar.isVisible = false
                    preloadNextImageIfNeeded()
                }
                return false
            }
        }
    }

    /**
     * Preload adjacent images (previous + next) in background for faster navigation.
     * Only preloads IMAGE and GIF files.
     * Supports circular navigation.
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
        
        Timber.d("ImageLoadingManager: Starting preload for ${adjacentFiles.size} adjacent files")
        
        // Preload each adjacent file
        adjacentFiles.forEach { file ->
            Timber.d("ImageLoadingManager: Preloading ${file.name} (${file.type})")
            val job = lifecycleScope.launch {
                // Determine actual resource type from path prefix (for Favorites with mixed sources)
                val actualResourceType = when {
                    file.path.startsWith("cloud://") -> ResourceType.CLOUD
                    file.path.startsWith("smb://") -> ResourceType.SMB
                    file.path.startsWith("sftp://") -> ResourceType.SFTP
                    file.path.startsWith("ftp://") -> ResourceType.FTP
                    else -> resource.type
                }
                
                // Check if this is a network resource
                if (actualResourceType == ResourceType.SMB || actualResourceType == ResourceType.SFTP || actualResourceType == ResourceType.FTP) {
                    val networkData = NetworkFileData(
                        path = file.path,
                        credentialsId = resource.credentialsId,
                        loadFullImage = true,
                        size = file.size,
                        createdDate = file.createdDate
                    )
                    val cacheKey = networkData.getCacheKey()
                    
                    // Preload with Glide with size limit to prevent OOM
                    // Limit to 1920px max dimension during preload
                    try {
                        withContext(Dispatchers.IO) {
                            Glide.with(binding.root.context.applicationContext)
                                .downloadOnly()
                                .load(networkData)
                                .signature(ObjectKey(cacheKey))
                                .diskCacheStrategy(DiskCacheStrategy.DATA) // Cache source data only (raw bytes)
                                .override(1920, 1920) // Limit preload size to prevent OOM
                                .submit()
                                .get() // Block until file is actually in cache
                        }
                        Timber.d("ImageLoadingManager: Preload ACTUALLY completed for ${file.name}")
                    } catch (e: Exception) {
                        Timber.w("ImageLoadingManager: Preload failed for ${file.name}: ${e.message}")
                    }
                } else if (actualResourceType == ResourceType.CLOUD) {
                    // Preload cloud file - detect provider from path
                    val fileId = file.path.substringAfterLast('/')
                    val provider = when {
                        file.path.contains("googledrive", ignoreCase = true) || file.path.contains("google_drive", ignoreCase = true) -> CloudProvider.GOOGLE_DRIVE
                        file.path.contains("onedrive", ignoreCase = true) -> CloudProvider.ONEDRIVE
                        file.path.contains("dropbox", ignoreCase = true) -> CloudProvider.DROPBOX
                        else -> CloudProvider.GOOGLE_DRIVE
                    }
                    val cloudData = CloudThumbnailData(
                        thumbnailUrl = file.thumbnailUrl ?: "",
                        fileId = fileId,
                        loadFullImage = true,
                        cloudProvider = provider
                    )
                    val cacheKey = "${file.path}_${file.size}"
                    
                    try {
                        withContext(Dispatchers.IO) {
                            Glide.with(binding.root.context.applicationContext)
                                .downloadOnly()
                                .load(cloudData)
                                .signature(ObjectKey(cacheKey))
                                .diskCacheStrategy(DiskCacheStrategy.DATA)
                                .override(1920, 1920) // Limit preload size to prevent OOM
                                .submit()
                                .get()
                        }
                        Timber.d("ImageLoadingManager: Preload ACTUALLY completed for ${file.name}")
                    } catch (e: Exception) {
                        Timber.w("ImageLoadingManager: Preload failed for ${file.name}: ${e.message}")
                    }
                } else {
                    // Preload local file
                    val cacheKey = "${file.path}_${file.size}"
                    try {
                        withContext(Dispatchers.IO) {
                            Glide.with(binding.root.context.applicationContext)
                                .downloadOnly()
                                .load(File(file.path))
                                .signature(ObjectKey(cacheKey))
                                .diskCacheStrategy(DiskCacheStrategy.DATA)
                                .override(1920, 1920) // Limit preload size to prevent OOM
                                .submit()
                                .get()
                        }
                        Timber.d("ImageLoadingManager: Preload ACTUALLY completed for ${file.name}")
                    } catch (e: Exception) {
                        Timber.w("ImageLoadingManager: Preload failed for ${file.name}: ${e.message}")
                    }
                }
            }
            
            // Memory leak fix: Remove job from list when completed to prevent accumulation
            job.invokeOnCompletion {
                synchronized(preloadJobs) {
                    preloadJobs.remove(job)
                }
            }
            
            synchronized(preloadJobs) {
                preloadJobs.add(job)
            }
        }
        Timber.d("ImageLoadingManager: Preload initiated for ${adjacentFiles.size} files")
    }
    
    /**
     * Show audio file info overlay with size, duration, format
     */
    fun showAudioFileInfo(file: MediaFile?) {
        if (file == null) return
        
        binding.audioInfoOverlay.isVisible = true
        
        // Clear previous metadata (will be populated later if found online)
        binding.audioMetadata.visibility = View.GONE
        binding.audioMetadata.text = ""
        
        // Display full file name with extension
        // Start with large font (will be reduced if metadata is found)
        binding.audioFileName.text = file.name
        binding.audioFileName.textSize = 22f
        
        // Get file info asynchronously (size, duration, format)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Use file.size from MediaFile (already populated during scan)
                val fileSize = file.size
                
                val fileSizeStr = if (fileSize > 0) {
                    com.sza.fastmediasorter.core.util.formatFileSize(fileSize)
                } else "N/A"
                
                withContext(Dispatchers.Main) {
                    binding.audioFileInfo.text = buildString {
                        append("Size: $fileSizeStr")
                        file.duration?.let { if (it > 0) append("\nDuration: ${formatDuration(it)}") }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get audio file info")
                withContext(Dispatchers.Main) {
                    binding.audioFileInfo.text = callback.getString(R.string.file_info_unavailable)
                }
            }
        }
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
            // Update only the format line, preserve size and duration
            val currentText = binding.audioFileInfo.text.toString()
            val lines = currentText.split("\n").toMutableList()
            
            // Replace or add format info line
            if (lines.size >= 3) {
                lines[2] = formatInfo
            } else {
                lines.add(formatInfo)
            }
            
            binding.audioFileInfo.text = lines.joinToString("\n")
        }
    }
    
    /**
     * Load audio cover art with fallback to iTunes Search API
     * Called when ExoPlayer is ready for audio files
     */
    fun loadAudioCoverArt(file: MediaFile) {
        val callId = System.currentTimeMillis()
        val caller = Thread.currentThread().stackTrace.getOrNull(3)?.let {
            "${it.className}.${it.methodName}():${it.lineNumber}"
        } ?: "Unknown"
        
        Timber.w("╔════════════════════════════════════════════════════════════════")
        Timber.w("║ loadAudioCoverArt() CALLED [ID=$callId]")
        Timber.w("╚════════════════════════════════════════════════════════════════")
        Timber.w("loadAudioCoverArt[$callId]: file=${file.name}")
        Timber.w("loadAudioCoverArt[$callId]: caller=$caller")
        Timber.w("loadAudioCoverArt[$callId]: audioCoverArtView.isVisible=${binding.audioCoverArtView.isVisible}")
        
        val isNetworkFile = file.path.startsWith("smb://") || file.path.startsWith("sftp://") || file.path.startsWith("ftp://")
        
        // For network files, check if ExoPlayer has embedded artwork first
        // ExoPlayer can extract artwork from network streams, but MediaMetadataRetriever cannot
        if (isNetworkFile) {
            // Delay to let ExoPlayer load metadata and artwork
            lifecycleScope.launch {
                delay(1500) // Wait for ExoPlayer to extract artwork
                
                // Check if ExoPlayer's PlayerView is showing artwork
                val player = binding.playerView.player
                val hasExoPlayerArtwork = player?.mediaMetadata?.artworkData != null ||
                        player?.mediaMetadata?.artworkUri != null
                
                Timber.d("Network file artwork check: hasExoPlayerArtwork=$hasExoPlayerArtwork")
                
                if (hasExoPlayerArtwork) {
                    // ExoPlayer has artwork, hide our overlay to show ExoPlayer's artwork
                    Timber.d("ExoPlayer has embedded artwork, hiding audioCoverArtView")
                    binding.audioCoverArtView.isVisible = false
                } else {
                    // ExoPlayer doesn't have artwork, search online
                    Timber.d("ExoPlayer has no artwork, searching online for ${file.name}")
                    binding.audioCoverArtView.isVisible = true
                    searchOnlineAndDisplayCover(file)
                }
            }
            return
        }
        
        // For local files, use MediaMetadataRetriever
        // Don't show view yet - wait until we have actual artwork to avoid flicker
        Timber.d("loadAudioCoverArt: Starting cover art search for ${file.name}")
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Try to extract embedded cover art using MediaMetadataRetriever
                Timber.d("Extracting embedded cover art for ${file.name}")
                val coverBitmap = withContext(Dispatchers.IO) {
                    try {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(file.path)
                        
                        // Try to get embedded picture
                        val embeddedPicture = retriever.embeddedPicture
                        
                        // Debug: check if file has any metadata
                        val hasAudio = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                        val mimeType = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                        Timber.d("MediaMetadataRetriever: hasAudio=$hasAudio, mimeType=$mimeType, embeddedPicture=${embeddedPicture?.size} bytes")
                        
                        retriever.release()
                        
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
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (coverBitmap != null) {
                        // Show embedded cover
                        Timber.w("loadAudioCoverArt[$callId]: ✅ EMBEDDED cover found, displaying")
                        Timber.w("loadAudioCoverArt[$callId]: BEFORE setImageBitmap - audioCoverArtView.isVisible=${binding.audioCoverArtView.isVisible}")
                        binding.audioCoverArtView.setImageBitmap(coverBitmap)
                        binding.audioCoverArtView.isVisible = true
                        Timber.w("loadAudioCoverArt[$callId]: AFTER setImageBitmap - audioCoverArtView.isVisible=${binding.audioCoverArtView.isVisible}")
                    } else {
                        // No embedded cover, check if online search is enabled
                        val settings = settingsRepository.getSettings().first()
                        if (!settings.searchAudioCoversOnline) {
                            Timber.d("loadAudioCoverArt[$callId]: No embedded cover, online search disabled - using placeholder")
                            binding.audioCoverArtView.setImageResource(R.drawable.ic_music_note)
                            binding.audioCoverArtView.isVisible = true
                            return@withContext
                        }
                        
                        // Online search enabled, try it
                        Timber.w("loadAudioCoverArt[$callId]: ❌ NO embedded cover, searching online")
                        
                        val metadata = withContext(Dispatchers.IO) {
                            Timber.w("loadAudioCoverArt[$callId]: Calling searchAudioCoverUseCase")
                            searchAudioCoverUseCase(file.name)
                        }
                        
                        val coverUrl = metadata?.coverArtUrl
                        if (coverUrl != null) {
                            // Save metadata for display
                            callback.onAudioMetadataLoaded(metadata)
                            
                            Timber.w("loadAudioCoverArt[$callId]: ✅ ONLINE cover found: $coverUrl")
                            Timber.w("loadAudioCoverArt[$callId]: BEFORE Glide.load - audioCoverArtView.isVisible=${binding.audioCoverArtView.isVisible}")
                            // Show view now, load without placeholder to prevent flicker
                            binding.audioCoverArtView.isVisible = true
                            Timber.w("loadAudioCoverArt[$callId]: AFTER isVisible=true - audioCoverArtView.isVisible=${binding.audioCoverArtView.isVisible}")
                            Glide.with(binding.audioCoverArtView.context)
                                .load(coverUrl)
                                .error(R.drawable.ic_music_note)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .listener(object : RequestListener<Drawable> {
                                    override fun onLoadFailed(
                                        e: GlideException?,
                                        model: Any?,
                                        target: Target<Drawable>,
                                        isFirstResource: Boolean
                                    ): Boolean {
                                        Timber.w(e, "Cover art load failed, showing placeholder")
                                        return false // Let Glide show error drawable
                                    }
                                    
                                    override fun onResourceReady(
                                        resource: Drawable,
                                        model: Any,
                                        target: Target<Drawable>?,
                                        dataSource: DataSource,
                                        isFirstResource: Boolean
                                    ): Boolean {
                                        Timber.d("Cover art loaded successfully from $dataSource")
                                        return false // Let Glide handle displaying
                                    }
                                })
                                .into(binding.audioCoverArtView)
                        } else {
                            Timber.d("No cover art found online, using placeholder for ${file.name}")
                            binding.audioCoverArtView.setImageResource(R.drawable.ic_music_note)
                            binding.audioCoverArtView.isVisible = true
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load audio cover art for ${file.name}")
                withContext(Dispatchers.Main) {
                    binding.audioCoverArtView.setImageResource(R.drawable.ic_music_note)
                    binding.audioCoverArtView.isVisible = true
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
            try {
                val metadata = searchAudioCoverUseCase(file.name)
                val coverUrl = metadata?.coverArtUrl
                
                withContext(Dispatchers.Main) {
                    if (coverUrl != null) {
                        // Save metadata for display
                        callback.onAudioMetadataLoaded(metadata)
                        
                        Timber.w("searchOnlineAndDisplayCover[$callId]: ✅ Found URL: $coverUrl")
                        Timber.w("searchOnlineAndDisplayCover[$callId]: BEFORE Glide.load - audioCoverArtView.isVisible=${binding.audioCoverArtView.isVisible}")
                        // Load without placeholder to avoid flicker
                        Glide.with(binding.audioCoverArtView.context)
                            .load(coverUrl)
                            .error(R.drawable.ic_music_note)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .listener(object : RequestListener<Drawable> {
                                override fun onLoadFailed(
                                    e: GlideException?,
                                    model: Any?,
                                    target: Target<Drawable>,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    Timber.w(e, "searchOnlineAndDisplayCover[$callId]: ❌ Glide load FAILED")
                                    return false
                                }
                                
                                override fun onResourceReady(
                                    resource: Drawable,
                                    model: Any,
                                    target: Target<Drawable>?,
                                    dataSource: DataSource,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    Timber.w("searchOnlineAndDisplayCover[$callId]: ✅ Glide loaded from $dataSource")
                                    return false
                                }
                            })
                            .into(binding.audioCoverArtView)
                    } else {
                        Timber.w("searchOnlineAndDisplayCover[$callId]: ❌ NO cover found, using placeholder")
                        binding.audioCoverArtView.setImageResource(R.drawable.ic_music_note)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "searchOnlineAndDisplayCover[$callId]: ❌ EXCEPTION")
                withContext(Dispatchers.Main) {
                    binding.audioCoverArtView.setImageResource(R.drawable.ic_music_note)
                }
            }
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
                    binding.btnTranslateImage.isVisible = false
                    binding.btnGoogleLensImage.isVisible = false
                    binding.btnOcrImage.isVisible = false
                    
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
