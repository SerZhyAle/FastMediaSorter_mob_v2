package com.sza.fastmediasorter.ui.slideshow

import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.data.AppDatabase
import com.sza.fastmediasorter.databinding.ActivitySlideshowBinding
import com.sza.fastmediasorter.network.ImageRepository
import com.sza.fastmediasorter.network.LocalStorageClient
import com.sza.fastmediasorter.network.SmbClient
import com.sza.fastmediasorter.network.SmbDataSourceFactory
import com.sza.fastmediasorter.ui.base.LocaleActivity
import com.sza.fastmediasorter.utils.Logger
import com.sza.fastmediasorter.utils.MediaUtils
import com.sza.fastmediasorter.utils.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@androidx.media3.common.util.UnstableApi
class SlideshowActivity : LocaleActivity() {
    private lateinit var binding: ActivitySlideshowBinding
    private lateinit var imageRepository: ImageRepository
    private lateinit var preferenceManager: PreferenceManager
    private var localStorageClient: LocalStorageClient? = null
    private var isLocalMode = false
    private var currentConfigId: Long = 0
    private var currentInterval: Int = 10

    private var images: List<String> = emptyList()
    private var sortedImages: List<String> = emptyList()
    private var currentIndex = 0
    private var slideshowJob: Job? = null
    private var isPaused = false
    private var currentBitmap: Bitmap? = null
    private var elapsedTime = 0
    private var isShuffleMode = false

    // Video support
    private var exoPlayer: ExoPlayer? = null
    private var isCurrentMediaVideo = false
    private var waitingForVideoEnd = false
    private var videoPlayerListener: Player.Listener? = null
    private var videoTimeoutJob: Job? = null
    private val videoLoadTimeoutMs = 3000L // 3 seconds timeout for video loading/parsing
    private var isRecreatingPlayer = false // Flag to prevent timeout cancellation during player recreation

    // Diagnostic dialog tracking to prevent window leaks
    private var diagnosticDialog: androidx.appcompat.app.AlertDialog? = null

    // Preloading optimization
    private var nextImageData: ByteArray? = null
    private var nextImageIndex: Int = -1
    private var lastLoadTime: Long = 0

    // Error tracking
    private data class MediaError(
        val fileName: String,
        val errorType: String,
        val errorMessage: String,
        val timestamp: Long = System.currentTimeMillis(),
    )

    private val errorLog = mutableListOf<MediaError>()
    private var consecutiveErrors = 0
    private val blacklistedFiles = mutableSetOf<String>() // Files that failed to load

    companion object {
        private const val TAG = "SlideshowActivity"
        private const val KEY_CURRENT_INDEX = "current_index"
        private const val KEY_IMAGES = "images"
        private const val MAX_CONSECUTIVE_ERRORS = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySlideshowBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Log app version on startup
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        Logger.i(TAG, "========== FastMediaSorter v$versionName started ==========")

        preferenceManager = PreferenceManager(this)

        // Keep screen on if enabled
        if (preferenceManager.isKeepScreenOn()) {
            window.addFlags(
                android
                    .view
                    .WindowManager
                    .LayoutParams
                    .FLAG_KEEP_SCREEN_ON,
            )
        }

        imageRepository = ImageRepository(SmbClient(), preferenceManager)

        // Get configId from intent, like SortActivity does
        val configId = intent.getLongExtra("configId", -1)
        Logger.d(TAG, "SlideshowActivity onCreate - configId: $configId")

        // Determine connection type and local mode
        val connectionType = intent.getStringExtra("connectionType") ?: "LOCAL"
        isLocalMode = connectionType == "LOCAL"
        Logger.d(TAG, "SlideshowActivity onCreate - connectionType: '$connectionType', isLocalMode: $isLocalMode")

        if (isLocalMode) {
            localStorageClient = LocalStorageClient(this)
        }

        setupExoPlayer()
        setupFullscreen()

        if (savedInstanceState != null) {
            currentIndex = savedInstanceState.getInt(KEY_CURRENT_INDEX, 0)
            val imagesList = savedInstanceState.getStringArrayList(KEY_IMAGES)
            if (imagesList != null && imagesList.isNotEmpty()) {
                images = imagesList
                startSlideshow()
            } else {
                loadImages(configId)
            }
        } else {
            // Try to restore last session index
            currentIndex = preferenceManager.getLastImageIndex()
            loadImages(configId)
        }
    }

    private fun setupExoPlayer() {
        // Release any existing player first
        exoPlayer?.let { player ->
            player.stop()
            player.release()
        }

        // Create listener once
        videoPlayerListener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            binding.videoLoadingLayout.visibility = View.VISIBLE
                        }
                        Player.STATE_READY -> {
                            // Cancel timeout - video started successfully (only if not recreating player)
                            if (!isRecreatingPlayer) {
                                videoTimeoutJob?.cancel()
                                Logger.d(TAG, "Video ready, timeout cancelled")
                            } else {
                                Logger.d(TAG, "Video ready (during recreation), timeout NOT cancelled")
                            }
                            binding.videoLoadingLayout.visibility = View.GONE
                        }
                        Player.STATE_ENDED -> {
                            // Cancel timeout (only if not recreating player)
                            if (!isRecreatingPlayer) {
                                videoTimeoutJob?.cancel()
                            }
                            binding.videoLoadingLayout.visibility = View.GONE
                            // Auto-advance to next media when video ends
                            if (waitingForVideoEnd && !isPaused) {
                                waitingForVideoEnd = false
                                skipToNextImage()
                            }
                        }
                        Player.STATE_IDLE -> {
                            binding.videoLoadingLayout.visibility = View.GONE
                            // STATE_IDLE is a normal state after stop() or before prepare()
                            // Error handling is done in onPlayerError callback
                        }
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    // Cancel timeout - error detected (only if not recreating player)
                    if (!isRecreatingPlayer) {
                        videoTimeoutJob?.cancel()
                    }

                    val currentMedia = images.getOrNull(currentIndex) ?: "unknown"

                    // Prevent error spam for already blacklisted files
                    if (blacklistedFiles.contains(currentMedia)) {
                        Logger
                            .w(TAG, "onPlayerError for already blacklisted file: ${currentMedia.substringAfterLast('/')}, ignoring")
                        return
                    }

                    Logger.e(TAG, "===========================================")
                    Logger.e(TAG, "=== onPlayerError TRIGGERED ===")
                    Logger.e(TAG, "Error: ${error.message}")
                    Logger.e(TAG, "Error Code: ${error.errorCode} (${getErrorCodeName(error.errorCode)})")
                    Logger.e(TAG, "Exception: ${error.javaClass.simpleName}")
                    Logger.e(TAG, "Cause: ${error.cause?.javaClass?.simpleName ?: "None"}")
                    Logger.e(TAG, "Cause Message: ${error.cause?.message ?: "None"}")
                    Logger.e(TAG, "Media: $currentMedia")
                    Logger.e(TAG, "File: ${currentMedia.substringAfterLast('/')}")
                    Logger.e(TAG, "Extension: .${currentMedia.substringAfterLast('.', "unknown")}")
                    Logger.e(TAG, "Index: $currentIndex")
                    Logger.e(TAG, "isPaused: $isPaused")
                    Logger.e(TAG, "waitingForVideoEnd: $waitingForVideoEnd")

                    // Detailed diagnosis for specific errors
                    if (error.cause?.message?.contains("ArrayIndexOutOfBoundsException") == true) {
                        Logger.e(TAG, "▶ DIAGNOSIS: ArrayIndexOutOfBoundsException")
                        Logger.e(TAG, "  This indicates corrupted file structure")
                        Logger.e(TAG, "  Parser tried to read beyond available data")
                        Logger.e(TAG, "  File is likely truncated or damaged")
                    }

                    Logger.e(TAG, "===========================================")

                    binding.videoLoadingLayout.visibility = View.GONE

                    // Show error dialog/toast BEFORE starting recovery (while currentIndex still points to error file)
                    if (preferenceManager.isShowVideoErrorDetails()) {
                        showVideoErrorDialog(error)
                    } else {
                        android
                            .widget
                            .Toast
                            .makeText(
                                this@SlideshowActivity,
                                "Video playback error: ${error.message}",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                    }

                    // Skip to next on error (error handling has priority over state flags)
                    if (!isPaused) {
                        Logger.w(TAG, "Initiating error recovery...")
                        // Reset flag first to allow new attempts
                        waitingForVideoEnd = false
                        lifecycleScope.launch {
                            handleMediaError(currentMedia, "Video Load", error.message ?: "Unknown error")
                            Logger.d(TAG, "Attempting to skip to next media after onPlayerError")
                            skipToNextImage()
                            Logger.d(TAG, "skipToNextImage() completed after onPlayerError")
                        }
                    } else {
                        Logger.w(TAG, "Skipping error recovery: Activity is paused")
                    }

                    Logger.e(TAG, "=== onPlayerError COMPLETED ===")
                }
            }

        exoPlayer = ExoPlayer.Builder(this).build()
        binding.playerView.player = exoPlayer

        // Enable controls for video playback in slideshow
        binding.playerView.useController = true
        binding.playerView.controllerAutoShow = true
        binding.playerView.controllerHideOnTouch = false

        // Add listener once
        exoPlayer?.addListener(videoPlayerListener!!)
    }

    private fun setupFullscreen() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        supportActionBar?.hide()

        binding.imageView.setOnClickListener {
            skipToNextImage()
        }

        setupControlAreas()
        updateControlsVisibility()
    }

    private fun updateControlsVisibility() {
        val showControls = preferenceManager.isShowControls()

        if (showControls) {
            // Show visible control panel
            binding.controlPanel.visibility = View.VISIBLE
            // Hide invisible touch areas
            binding.topLayout.visibility = View.GONE
            binding.controlsLayout.visibility = View.GONE
            binding.rotationLayout.visibility = View.GONE

            // Shrink PlayerView to make room for control panel
            adjustPlayerViewForControls(true)
        } else {
            // Hide control panel
            binding.controlPanel.visibility = View.GONE
            // Show invisible touch areas (transparent)
            binding.topLayout.visibility = View.VISIBLE
            binding.controlsLayout.visibility = View.VISIBLE
            binding.rotationLayout.visibility = View.VISIBLE

            // Restore PlayerView to full screen
            adjustPlayerViewForControls(false)
        }
    }

    private fun adjustPlayerViewForControls(showingControls: Boolean) {
        val playerParams = binding.playerView.layoutParams as FrameLayout.LayoutParams

        if (showingControls) {
            // Measure control panel height
            binding.controlPanel.post {
                val controlPanelHeight = binding.controlPanel.height
                playerParams.bottomMargin = controlPanelHeight
                binding.playerView.layoutParams = playerParams
            }
        } else {
            // Full screen
            playerParams.bottomMargin = 0
            binding.playerView.layoutParams = playerParams
        }
    }

    private fun setupControlAreas() {
        binding.controlsLayout.post {
            val screenHeight = binding.controlsLayout.height
            val controlHeight = (screenHeight * 0.75).toInt()
            val topMargin = (screenHeight * 0.125).toInt()
            val bottomMargin = (screenHeight * 0.125).toInt()

            val controlLayoutParams = binding.controlsLayout.layoutParams as FrameLayout.LayoutParams
            controlLayoutParams.height = controlHeight
            controlLayoutParams.topMargin = topMargin
            binding.controlsLayout.layoutParams = controlLayoutParams

            val topLayoutParams = binding.topLayout.layoutParams as FrameLayout.LayoutParams
            topLayoutParams.height = topMargin
            binding.topLayout.layoutParams = topLayoutParams

            val rotationLayoutParams = binding.rotationLayout.layoutParams as FrameLayout.LayoutParams
            rotationLayoutParams.height = bottomMargin
            binding.rotationLayout.layoutParams = rotationLayoutParams
        }

        binding.backButton.setOnClickListener {
            onBackPressed()
        }

        binding.shuffleButton.setOnClickListener {
            toggleShuffleMode()
        }

        binding.previousButton.setOnClickListener {
            skipToPreviousImage()
        }

        binding.pauseButton.setOnClickListener {
            togglePause()
        }

        binding.nextButton.setOnClickListener {
            skipToNextImage()
        }

        // Rotation buttons are now managed dynamically in updateRotationAreas()
        // to prevent touch interception during video playback

        binding.btnBack.setOnClickListener {
            onBackPressed()
        }

        binding.btnPrevious.setOnClickListener {
            skipToPreviousImage()
        }

        binding.btnPlayPause.setOnClickListener {
            togglePause()
        }

        binding.btnNext.setOnClickListener {
            skipToNextImage()
        }

        binding.btnRotateLeft.setOnClickListener {
            rotateImage(-90f)
        }

        binding.btnRotateRight.setOnClickListener {
            rotateImage(90f)
        }

        binding.btnShuffle.setOnClickListener {
            toggleShuffleMode()
        }

        binding.btnJumpToBegin.setOnClickListener {
            jumpToBegin()
        }

        // Video overlay buttons
        binding.btnVideoBack.setOnClickListener {
            onBackPressed()
        }

        binding.btnVideoPrevious.setOnClickListener {
            skipToPreviousImage()
        }

        binding.btnVideoNext.setOnClickListener {
            skipToNextImage()
        }

        binding.btnVideoShuffle.setOnClickListener {
            toggleShuffleMode()
        }
    }

    private fun rotateImage(degrees: Float) {
        val currentRotation = binding.imageView.rotation
        val newRotation = (currentRotation + degrees) % 360
        binding.imageView.rotation = newRotation

        // Auto-pause on rotation
        isPaused = true
    }

    private fun updateRotationAreas() {
        // Disable rotation areas for videos, enable for images
        val enableRotation = !isCurrentMediaVideo

        // Show video overlay for videos, hide for images
        binding.videoControlOverlay.visibility = if (isCurrentMediaVideo) View.VISIBLE else View.GONE

        // Disable invisible touch controls during video playback
        if (isCurrentMediaVideo) {
            // Hide all invisible touch areas when video is playing
            binding.topLayout.visibility = View.GONE
            binding.controlsLayout.visibility = View.GONE
            binding.rotationLayout.visibility = View.GONE
        } else {
            // Show invisible touch areas for images (if controls are hidden)
            val showControls = preferenceManager.isShowControls()
            if (!showControls) {
                binding.topLayout.visibility = View.VISIBLE
                binding.controlsLayout.visibility = View.VISIBLE
                binding.rotationLayout.visibility = View.VISIBLE
            }
        }

        // Disable click listeners for rotation areas during video playback
        binding.rotationLayout.isClickable = enableRotation
        binding.rotateLeftButton.isEnabled = enableRotation
        binding.rotateRightButton.isEnabled = enableRotation
        binding.btnRotateLeft.isEnabled = enableRotation
        binding.btnRotateRight.isEnabled = enableRotation

        // Completely remove click listeners during video to prevent touch interception
        if (enableRotation) {
            binding.rotationLayout.alpha = 1.0f
            binding.rotateLeftButton.setOnClickListener { rotateImage(-90f) }
            binding.rotateRightButton.setOnClickListener { rotateImage(90f) }
        } else {
            binding.rotationLayout.alpha = 0.0f
            // Remove listeners to allow player controls to work
            binding.rotateLeftButton.setOnClickListener(null)
            binding.rotateRightButton.setOnClickListener(null)
        }
    }

    private fun togglePause() {
        isPaused = !isPaused
        updatePlayPauseIcon()

        // Show interval when resuming, OFF when paused
        if (!isPaused) {
            showTimerText("$currentInterval", 1000)
        } else {
            showTimerText("OFF", 1000)
        }
    }

    private fun updatePlayPauseIcon() {
        binding.btnPlayPause.setImageResource(
            if (isPaused) {
                android.R.drawable.ic_media_play
            } else {
                android.R.drawable.ic_media_pause
            },
        )
    }

    private fun toggleShuffleMode() {
        isShuffleMode = !isShuffleMode
        preferenceManager.setShuffleMode(isShuffleMode)

        val currentImagePath =
            if (images.isNotEmpty() && currentIndex < images.size) {
                images[currentIndex]
            } else {
                null
            }

        if (isShuffleMode) {
            // Switch to random
            images = sortedImages.shuffled()
            showTimerText("RND", 1000)
        } else {
            // Switch to ABC - continue from current file
            images = sortedImages
            currentImagePath?.let { path ->
                currentIndex = images.indexOf(path).takeIf { it >= 0 } ?: 0
            }
            showTimerText("ABC", 1000)
        }

        // Clear preloaded data on shuffle mode change
        nextImageData = null
        nextImageIndex = -1

        // Skip to next image in new order
        skipToNextImage()
    }

    private fun jumpToBegin() {
        Logger.d(TAG, "Refreshing folder and jumping to first image")

        // Stop current playback
        isPaused = true

        // Stop video if playing
        if (isCurrentMediaVideo) {
            exoPlayer?.stop()
            binding.playerView.visibility = View.GONE
            binding.imageView.visibility = View.VISIBLE
        }

        // Clear state
        blacklistedFiles.clear()
        nextImageData = null
        nextImageIndex = -1

        // Show feedback
        android
            .widget
            .Toast
            .makeText(this, "Refreshing folder...", android.widget.Toast.LENGTH_SHORT)
            .show()

        // Save current file name to try restoring position if it still exists
        val currentFileName =
            if (images.isNotEmpty() && currentIndex < images.size) {
                images[currentIndex].substringAfterLast('/')
            } else {
                null
            }

        // Reload entire folder
        loadImages()

        // After reload, try to jump to first file or restore position
        lifecycleScope.launch {
            kotlinx.coroutines.delay(500) // Wait for loadImages to complete

            if (images.isNotEmpty()) {
                // If previous file still exists, stay on it, otherwise go to first
                val newIndex =
                    if (currentFileName != null) {
                        images
                            .indexOfFirst { it.substringAfterLast('/') == currentFileName }
                            .takeIf { it >= 0 } ?: 0
                    } else {
                        0
                    }

                currentIndex = newIndex
                showTimerText("→ ${newIndex + 1}/${images.size}", 1000)
                loadCurrentMedia()

                // Auto-resume slideshow
                isPaused = false
                startSlideshow()
            } else {
                showError("No images found after refresh")
            }
        }
    }

    private fun skipToPreviousImage() {
        if (images.isNotEmpty()) {
            currentIndex = if (currentIndex > 0) currentIndex - 1 else images.size - 1
            // Clear preloaded data on manual navigation
            nextImageData = null
            nextImageIndex = -1
            lifecycleScope.launch {
                loadCurrentMedia()
            }
            // Auto-pause when going to previous
            isPaused = true
        }
    }

    private fun skipToNextImage() {
        Logger.d(
            TAG,
            ">>> skipToNextImage() START - currentIndex: $currentIndex, images.size: ${images.size}, blacklisted: ${blacklistedFiles.size}",
        )

        if (images.isNotEmpty()) {
            // Remove blacklisted files from the list
            if (blacklistedFiles.isNotEmpty()) {
                Logger.d(TAG, "Processing blacklist: ${blacklistedFiles.size} files")
                val cleanedImages =
                    images.filterNot { url ->
                        blacklistedFiles.contains(url)
                    }
                if (cleanedImages.isEmpty()) {
                    Logger.e(TAG, "All media files are blacklisted!")
                    Toast.makeText(this, "All media files failed to load", Toast.LENGTH_LONG).show()
                    finishSafely()
                    return
                }
                if (cleanedImages.size != images.size) {
                    val removedCount = images.size - cleanedImages.size
                    images = cleanedImages
                    sortedImages = cleanedImages
                    Logger.d(TAG, "Removed $removedCount blacklisted files from list")
                    // Adjust current index if needed
                    if (currentIndex >= images.size) {
                        Logger.d(TAG, "Adjusting index: $currentIndex -> 0")
                        currentIndex = 0
                    }
                }
            }

            val targetIndex = (currentIndex + 1) % images.size
            Logger.d(TAG, "Target index calculated: $currentIndex -> $targetIndex")

            // Re-shuffle when wrapping around to beginning in shuffle mode
            if (targetIndex == 0 && isShuffleMode && images.size > 1) {
                images = sortedImages.shuffled()
                // Clear preload cache since order changed
                nextImageData = null
                nextImageIndex = -1
            }

            // Check if target image is already preloaded
            if (nextImageIndex == targetIndex && nextImageData != null) {
                Logger.d(TAG, "Using preloaded data for index $targetIndex")
                // Use preloaded data - instant switch
                currentIndex = targetIndex
                lifecycleScope.launch {
                    Logger.d(TAG, ">>> skipToNextImage() - calling loadCurrentMedia() with preloaded data")
                    loadCurrentMedia()

                    // Show interval after manual next
                    if (!isPaused) {
                        showTimerText("$currentInterval", 1000)
                    }
                }
            } else {
                Logger.d(TAG, "No preload, loading index $targetIndex normally")
                // Not preloaded - clear cache and load normally
                currentIndex = targetIndex
                nextImageData = null
                nextImageIndex = -1
                lifecycleScope.launch {
                    Logger.d(TAG, ">>> skipToNextImage() - calling loadCurrentMedia() normally")
                    loadCurrentMedia()

                    // Show interval after manual next
                    if (!isPaused) {
                        showTimerText("$currentInterval", 1000)
                    }
                }
            }

            // Reset timer on manual next
            elapsedTime = 0
        }
        Logger.d(TAG, "<<< skipToNextImage() END")
    }

    private fun loadImages(configId: Long = -1L) {
        Logger.d(TAG, "loadImages() called with configId: $configId")
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            // Load saved position for current connection
            currentConfigId = if (configId != -1L) configId else loadCurrentConfigId()
            currentInterval = preferenceManager.getInterval()
            if (currentConfigId > 0) {
                currentIndex = loadSavedPosition(currentConfigId)
            }

            if (isLocalMode) {
                loadLocalImages()
            } else {
                imageRepository
                    .loadImages()
                    .onSuccess { imageList ->
                        // Save sorted list
                        sortedImages = imageList.sorted()

                        // Load shuffle mode preference
                        isShuffleMode = preferenceManager.isShuffleMode()

                        // Apply current mode
                        images =
                            if (isShuffleMode) {
                                sortedImages.shuffled()
                            } else {
                                sortedImages
                            }

                        if (images.isNotEmpty()) {
                            // Validate currentIndex
                            if (currentIndex >= images.size) {
                                currentIndex = 0
                            }
                            startSlideshow()
                        } else {
                            showError("No images found")
                        }
                    }.onFailure { error ->
                        preferenceManager.clearLastSession()
                        showError("Loading error: ${error.message}")
                    }
            }

            binding.progressBar.visibility = View.GONE
        }
    }

    private suspend fun loadLocalImages() {
        Logger.d(TAG, "loadLocalImages() called")
        try {
            // Load config from DB using currentConfigId, like SortActivity does
            val config =
                if (currentConfigId > 0) {
                    val dao = AppDatabase.getDatabase(this@SlideshowActivity).connectionConfigDao()
                    dao.getConfigById(currentConfigId)
                } else {
                    null
                }

            Logger.d(
                TAG,
                "loadLocalImages() - loaded config: id=${config?.id}, type=${config?.type}, name=${config?.localDisplayName ?: config?.name}, localUri=${config?.localUri}",
            )

            val localUri = config?.localUri?.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }
            val bucketName = config?.localDisplayName?.ifEmpty { null }
            val isVideoEnabled = preferenceManager.isVideoEnabled()
            val maxVideoSizeMb = preferenceManager.getMaxVideoSizeMb()

            Logger.d(TAG, "loadLocalImages() - using localUri: $localUri, bucketName: $bucketName")

            val imageInfoList =
                localStorageClient?.getImageFiles(localUri, bucketName, isVideoEnabled, maxVideoSizeMb) ?: emptyList()
            Logger.d(TAG, "loadLocalImages() - getImageFiles returned ${imageInfoList.size} files")

            val imageUris = imageInfoList.map { it.uri.toString() }
            sortedImages = imageUris.sorted()

            isShuffleMode = preferenceManager.isShuffleMode()
            images =
                if (isShuffleMode) {
                    sortedImages.shuffled()
                } else {
                    sortedImages
                }

            Logger.d(TAG, "loadLocalImages() - final images list size: ${images.size}")

            if (images.isNotEmpty()) {
                // Load saved position for current connection
                currentConfigId = loadCurrentConfigId()
                currentInterval = preferenceManager.getInterval()
                if (currentConfigId > 0) {
                    currentIndex = loadSavedPosition(currentConfigId)
                }

                if (currentIndex >= images.size) {
                    currentIndex = 0
                }
                startSlideshow()
            } else {
                Toast.makeText(this, "No media files found in selected folder", Toast.LENGTH_LONG).show()
                finishSafely()
            }
        } catch (e: Exception) {
            preferenceManager.clearLastSession()
            showError("Error loading local images: ${e.message}")
        }
    }

    private fun startSlideshow() {
        Logger.d(TAG, "startSlideshow() called - images.size: ${images.size}")
        slideshowJob =
            lifecycleScope.launch {
                while (true) {
                    if (images.isNotEmpty()) {
                        val loadStartTime = System.currentTimeMillis()
                        loadCurrentMedia()
                        val loadDuration = (System.currentTimeMillis() - loadStartTime) / 1000
                        lastLoadTime = loadDuration

                        // For videos with "play till end" enabled, skip interval timer - video will auto-advance on completion
                        if (isCurrentMediaVideo && preferenceManager.isPlayVideoTillEnd() && waitingForVideoEnd) {
                            // Wait for video to complete (handled by Player.Listener)
                            while (waitingForVideoEnd && !isPaused) {
                                delay(1000L)
                            }
                            // Video ended, continue to next iteration
                            if (!isPaused) {
                                currentIndex = (currentIndex + 1) % images.size
                                saveCurrentPosition()
                            }
                            continue
                        }

                        if (!isPaused) {
                            // Show interval after image load (1 second)
                            showTimerText("$currentInterval", 1000)
                        }

                        elapsedTime = 0

                        // Preload next image if interval > 2x load time
                        var preloadStarted = false

                        while (elapsedTime < currentInterval) {
                            if (isPaused) {
                                delay(250L)
                                continue
                            }

                            delay(1000L)
                            elapsedTime++

                            val remaining = currentInterval - elapsedTime

                            // Start preloading if interval allows and not started yet
                            if (!preloadStarted && currentInterval > loadDuration * 2 && remaining > loadDuration + 1) {
                                preloadStarted = true
                                val nextIndex = (currentIndex + 1) % images.size
                                lifecycleScope.launch {
                                    preloadNextImage(nextIndex)
                                }
                            }

                            // Show countdown for last 3 seconds
                            if (remaining in 1..3) {
                                showTimerText("in $remaining", 0)
                            }
                        }

                        if (!isPaused) {
                            val nextIndex = (currentIndex + 1) % images.size

                            // Re-shuffle when wrapping around to beginning in shuffle mode
                            if (nextIndex == 0 && isShuffleMode && images.size > 1) {
                                images = sortedImages.shuffled()
                                // Clear preload cache since order changed
                                nextImageData = null
                                nextImageIndex = -1
                            }

                            // Check if next image is preloaded - if not, start loading now
                            if (nextImageIndex != nextIndex || nextImageData == null) {
                                // Not preloaded, load it now to minimize gap
                                lifecycleScope
                                    .launch {
                                        preloadNextImage(nextIndex)
                                    }.join() // Wait for preload to complete
                            }
                            currentIndex = nextIndex
                            saveCurrentPosition()
                        }
                    }
                }
            }
    }

    private fun showTimerText(
        text: String,
        hideAfterMs: Long,
    ) {
        lifecycleScope.launch {
            binding.timerText.text = text
            binding.timerText.visibility = View.VISIBLE

            if (hideAfterMs > 0) {
                delay(hideAfterMs)
                binding.timerText.visibility = View.GONE
            }
        }
    }

    private suspend fun loadCurrentMedia() {
        try {
            Logger.d(
                TAG,
                "loadCurrentMedia() called - currentIndex: $currentIndex, images.size: ${images.size}, blacklisted: ${blacklistedFiles.size}",
            )
            Logger.d(TAG, "loadCurrentMedia() - MODE CHECK: isLocalMode = $isLocalMode")

            // Check if all files are blacklisted
            if (blacklistedFiles.size >= images.size) {
                Logger.e(TAG, "All files are blacklisted! Exiting slideshow.")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast
                        .makeText(
                            this@SlideshowActivity,
                            "All media files failed to load. Exiting slideshow.",
                            Toast.LENGTH_LONG,
                        ).show()
                    finishSafely()
                }
                return
            }

            val mediaUrl = images[currentIndex]

            // Skip blacklisted files immediately
            if (blacklistedFiles.contains(mediaUrl)) {
                Logger.w(
                    TAG,
                    "Skipping blacklisted file: ${mediaUrl.substringAfterLast('/')} - calling skipToNextImage()",
                )
                // Move to next index BEFORE skipping to avoid infinite loop
                currentIndex = (currentIndex + 1) % images.size
                if (!isPaused) {
                    delay(100) // Small delay to prevent tight loop
                    loadCurrentMedia() // Recursively try next file
                }
                return
            }

            // Determine if current media is video
            // For local mode, get actual filename from MediaStore before type detection
            val actualFileName =
                if (isLocalMode) {
                    try {
                        val fileInfo = localStorageClient?.getFileInfo(Uri.parse(mediaUrl))
                        fileInfo?.name ?: mediaUrl.substringAfterLast('/')
                    } catch (e: Exception) {
                        Logger.w(TAG, "Failed to get file info for $mediaUrl, using URI: ${e.message}")
                        mediaUrl.substringAfterLast('/')
                    }
                } else {
                    mediaUrl.substringAfterLast('/')
                }

            val fileName = mediaUrl.substringAfterLast('/')
            val extension = actualFileName.substringAfterLast('.', "").lowercase()
            isCurrentMediaVideo = MediaUtils.isVideo(actualFileName)

            // Update rotation areas based on media type
            updateRotationAreas()

            if (isCurrentMediaVideo) {
                Logger.d(TAG, ">>> LOADING VIDEO: $fileName")
                Logger.d(TAG, "  Setting imageView.visibility = GONE")
                Logger.d(TAG, "  Setting playerView.visibility = VISIBLE")
                Logger.d(TAG, "  Current UI state before change:")
                Logger.d(TAG, "    imageView.visibility = ${binding.imageView.visibility}")
                Logger.d(TAG, "    playerView.visibility = ${binding.playerView.visibility}")
                Logger.d(TAG, "    videoLoadingLayout.visibility = ${binding.videoLoadingLayout.visibility}")

                // Load video - clear previous content
                binding.imageView.visibility = View.GONE

                // Clear player to prevent showing old video frame while loading
                exoPlayer?.clearMediaItems()

                // Clear preload cache since we're switching to video
                nextImageData = null
                nextImageIndex = -1

                binding.playerView.visibility = View.VISIBLE
                Logger.d(TAG, "  UI state after change:")
                Logger.d(TAG, "    imageView.visibility = ${binding.imageView.visibility}")
                Logger.d(TAG, "    playerView.visibility = ${binding.playerView.visibility}")
                Logger.d(TAG, "    videoLoadingLayout.visibility = ${binding.videoLoadingLayout.visibility}")
                loadVideo(mediaUrl, actualFileName)
            } else {
                Logger.d(TAG, ">>> LOADING IMAGE: $fileName")
                Logger.d(TAG, "  Setting playerView.visibility = GONE")
                Logger.d(TAG, "  Setting imageView.visibility = VISIBLE")
                Logger.d(TAG, "  Current UI state before change:")
                Logger.d(TAG, "    imageView.visibility = ${binding.imageView.visibility}")
                Logger.d(TAG, "    playerView.visibility = ${binding.playerView.visibility}")
                Logger.d(TAG, "    videoLoadingLayout.visibility = ${binding.videoLoadingLayout.visibility}")

                // Load image - cancel any pending video operations
                videoTimeoutJob?.cancel()
                exoPlayer?.stop()
                binding.playerView.visibility = View.GONE

                // Clear previous image to prevent showing old content while loading
                binding.imageView.setImageBitmap(null)
                binding.imageView.visibility = View.VISIBLE
                binding.videoLoadingLayout.visibility = View.GONE
                Logger.d(TAG, "  UI state after change:")
                Logger.d(TAG, "    imageView.visibility = ${binding.imageView.visibility}")
                Logger.d(TAG, "    playerView.visibility = ${binding.playerView.visibility}")
                Logger.d(TAG, "    videoLoadingLayout.visibility = ${binding.videoLoadingLayout.visibility}")
                loadImage(mediaUrl, actualFileName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun loadImage(
        imageUrl: String,
        actualFileName: String,
    ) {
        try {
            Logger.d(TAG, "Loading image: ${imageUrl.substringAfterLast('/')}")

            // Always hide video loading indicator when switching to image
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                binding.videoLoadingLayout.visibility = View.GONE
            }

            // Check blacklist BEFORE attempting to load
            if (blacklistedFiles.contains(imageUrl)) {
                Logger.w(TAG, "Attempted to load blacklisted image: ${imageUrl.substringAfterLast('/')}, skipping")
                if (!isPaused) {
                    skipToNextImage()
                }
                return
            }

            // FAST PRE-VALIDATION: Check image file integrity BEFORE loading
            // Validation disabled: files already filtered by extension during discovery
            // If file is corrupted, viewer/player will handle error gracefully

            // Use preloaded data if available for current index
            val imageData =
                if (nextImageIndex == currentIndex && nextImageData != null) {
                    val preloaded = nextImageData
                    nextImageData = null // Clear after use
                    nextImageIndex = -1
                    preloaded
                } else {
                    var toastShown = false
                    var loadingToast: Toast? = null

                    // Schedule toast if loading takes >1 second
                    val toastJob =
                        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            kotlinx.coroutines.delay(1000)
                            val fileInfo =
                                try {
                                    if (isLocalMode) {
                                        localStorageClient?.getFileInfo(Uri.parse(imageUrl))?.let {
                                            SmbClient.FileInfo(
                                                name = it.name,
                                                sizeKB = (it.size / 1024),
                                                modifiedDate = it.dateModified,
                                            )
                                        }
                                    } else {
                                        imageRepository.smbClient.getFileInfo(imageUrl)
                                    }
                                } catch (e: Exception) {
                                    null
                                }

                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                val sizeText = fileInfo?.let { "${it.sizeKB}KB" } ?: "..."
                                loadingToast =
                                    Toast.makeText(
                                        this@SlideshowActivity,
                                        "Loading... $sizeText",
                                        Toast.LENGTH_SHORT,
                                    )
                                loadingToast?.show()
                                toastShown = true
                            }
                        }

                    val data =
                        if (isLocalMode) {
                            localStorageClient?.downloadImage(Uri.parse(imageUrl))
                        } else {
                            imageRepository.downloadImage(imageUrl)
                        }

                    // Cancel toast timer if still waiting
                    toastJob.cancel()

                    // Hide toast if it was shown
                    if (toastShown) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            loadingToast?.cancel()
                        }
                    }

                    data
                }

            imageData?.let { data ->
                val isGif = actualFileName.lowercase().endsWith(".gif")

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (isGif) {
                        // Use Glide for GIF animations
                        Glide
                            .with(this@SlideshowActivity)
                            .asGif()
                            .load(data)
                            .error(
                                com
                                    .sza
                                    .fastmediasorter
                                    .R
                                    .drawable
                                    .error_placeholder,
                            ).listener(
                                object : com.bumptech.glide.request.RequestListener<com.bumptech.glide.load.resource.gif.GifDrawable> {
                                    override fun onLoadFailed(
                                        e: com.bumptech.glide.load.engine.GlideException?,
                                        model: Any?,
                                        target:
                                            com.bumptech.glide.request.target.Target<com.bumptech.glide.load.resource.gif.GifDrawable>,
                                        isFirstResource: Boolean,
                                    ): Boolean {
                                        Logger.e(TAG, "Failed to load GIF: ${e?.message}", e)
                                        lifecycleScope.launch {
                                            handleMediaError(imageUrl, "GIF Load", e?.message ?: "Glide load failed")
                                            if (!isPaused) {
                                                skipToNextImage()
                                            }
                                        }
                                        return false
                                    }

                                    override fun onResourceReady(
                                        resource: com.bumptech.glide.load.resource.gif.GifDrawable,
                                        model: Any,
                                        target:
                                            com.bumptech.glide.request.target.Target<com.bumptech.glide.load.resource.gif.GifDrawable>?,
                                        dataSource: com.bumptech.glide.load.DataSource,
                                        isFirstResource: Boolean,
                                    ): Boolean {
                                        consecutiveErrors = 0
                                        binding.videoLoadingLayout.visibility = View.GONE
                                        saveSessonState()
                                        return false
                                    }
                                },
                            ).into(binding.imageView)
                    } else {
                        // Use BitmapFactory for static images (JPEG, PNG, etc.)
                        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                        if (bitmap != null) {
                            currentBitmap = bitmap
                            binding.imageView.setImageBitmap(bitmap)
                            binding.imageView.rotation = 0f // Reset rotation for new image
                            binding.videoLoadingLayout.visibility = View.GONE

                            // Reset error counter on successful load
                            consecutiveErrors = 0

                            // Save last session state
                            saveSessonState()
                        } else {
                            binding.imageView.setImageResource(
                                com
                                    .sza
                                    .fastmediasorter
                                    .R
                                    .drawable
                                    .error_placeholder,
                            )
                            handleMediaError(imageUrl, "Image Decode", "Failed to decode bitmap")
                            // Skip to next media - blacklist will prevent reload
                            if (!isPaused) {
                                skipToNextImage()
                            }
                        }
                    }
                }
            } ?: run {
                binding.imageView.setImageResource(
                    com
                        .sza
                        .fastmediasorter
                        .R
                        .drawable
                        .error_placeholder,
                )
                handleMediaError(imageUrl, "Image Load", "No data received")
                // Skip to next media - blacklist will prevent reload
                if (!isPaused) {
                    skipToNextImage()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun loadVideo(
        videoUrl: String,
        actualFileName: String,
    ) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            try {
                val fileName = actualFileName
                val extension = actualFileName.substringAfterLast('.', "").lowercase()

                Logger.d(TAG, "Loading video: $fileName")

                // CRITICAL: Verify this is actually a video file
                if (!MediaUtils.isVideo(actualFileName)) {
                    Logger.e(TAG, "Non-video file in loadVideo(): $fileName")
                    // Treat as corrupted and skip
                    blacklistedFiles.add(videoUrl)
                    binding.videoLoadingLayout.visibility = View.GONE
                    if (!isPaused) {
                        skipToNextImage()
                    }
                    return@withContext
                }

                // Check blacklist BEFORE attempting to load
                if (blacklistedFiles.contains(videoUrl)) {
                    Logger.w(TAG, "Attempted to load blacklisted video: ${videoUrl.substringAfterLast('/')}, skipping")
                    if (!isPaused) {
                        skipToNextImage()
                    }
                    return@withContext
                }

                // Validation disabled: files already filtered by extension during discovery
                // ExoPlayer will handle corrupted files with error events

                // Cancel any existing timeout
                videoTimeoutJob?.cancel()

                // Stop current playback
                exoPlayer?.stop()
                exoPlayer?.clearMediaItems()

                // Mark that we're waiting for video to complete only if "play till end" is enabled
                waitingForVideoEnd = preferenceManager.isPlayVideoTillEnd()

                // Start timeout watchdog - will forcefully skip if video doesn't load
                videoTimeoutJob =
                    lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        delay(videoLoadTimeoutMs)

                        // Timeout fired - check if we're still waiting for this video
                        if (!blacklistedFiles.contains(videoUrl)) {
                            Logger.e(TAG, "Video load timeout for ${videoUrl.substringAfterLast('/')}")
                            // Force cleanup regardless of player state
                            binding.videoLoadingLayout.visibility = View.GONE
                            waitingForVideoEnd = false
                            blacklistedFiles.add(videoUrl)

                            // Stop player to interrupt any ongoing operations
                            try {
                                exoPlayer?.stop()
                                exoPlayer?.clearMediaItems()
                            } catch (e: Exception) {
                                Logger.e(TAG, "Error stopping player during timeout", e)
                            }

                            if (!isPaused) {
                                skipToNextImage()
                            }
                        } else {
                            Logger
                                .d(TAG, "Timeout fired but file already blacklisted: ${videoUrl.substringAfterLast('/')}")
                        }
                    }

                if (isLocalMode) {
                    // Local video - use URI directly
                    val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
                    exoPlayer?.setMediaItem(mediaItem)
                    exoPlayer?.prepare()
                    exoPlayer?.play()
                } else {
                    // SMB video - need to recreate player with custom data source
                    val smbContext = imageRepository.getSmbContext()
                    if (smbContext != null) {
                        // Set flag to prevent timeout cancellation during recreation
                        isRecreatingPlayer = true

                        // Release old player and listener
                        exoPlayer?.removeListener(videoPlayerListener!!)
                        exoPlayer?.release()

                        // Create new player with SmbDataSource
                        val dataSourceFactory = SmbDataSourceFactory(imageRepository.smbClient)
                        exoPlayer =
                            ExoPlayer
                                .Builder(this@SlideshowActivity)
                                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory as androidx.media3.datasource.DataSource.Factory))
                                .build()
                        binding.playerView.player = exoPlayer

                        // Re-attach the SAME listener instance
                        exoPlayer?.addListener(videoPlayerListener!!)

                        // Clear flag before prepare()
                        isRecreatingPlayer = false

                        val mediaItem = MediaItem.fromUri(videoUrl)
                        exoPlayer?.setMediaItem(mediaItem)
                        exoPlayer?.prepare()
                        exoPlayer?.play()

                        // Reset error counter on successful load
                        consecutiveErrors = 0
                    } else {
                        Logger.e("SlideshowActivity", "SMB context is null! Cannot play video.")
                        handleMediaError(videoUrl, "Video Load", "SMB context not available")
                        // Skip to next media - blacklist will prevent reload
                        if (!isPaused) {
                            skipToNextImage()
                        }
                    }
                }

                // Save last session state
                saveSessonState()
            } catch (e: Exception) {
                Logger.e("SlideshowActivity", "Failed to load video: ${e.message}", e)
                handleMediaError(videoUrl, "Video Load", e.message ?: "Unknown error")
                waitingForVideoEnd = false
                // Skip to next media - blacklist will prevent reload
                if (!isPaused) {
                    skipToNextImage()
                }
            }
        }
    }

    private suspend fun preloadNextImage(nextIndex: Int) {
        try {
            if (nextIndex < images.size) {
                val imageUrl = images[nextIndex]
                val imageData =
                    if (isLocalMode) {
                        localStorageClient?.downloadImage(Uri.parse(imageUrl))
                    } else {
                        imageRepository.downloadImage(imageUrl)
                    }

                // Store preloaded data
                nextImageData = imageData
                nextImageIndex = nextIndex
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Silently fail preloading
        }
    }

    private fun saveSessonState() {
        val folderAddress = "${preferenceManager.getServerAddress()}\\${preferenceManager.getFolderPath()}"
        preferenceManager.saveLastSession(folderAddress, currentIndex)
    }

    private fun showVideoErrorDialog(error: androidx.media3.common.PlaybackException) {
        val currentFile =
            if (images.isNotEmpty() && currentIndex < images.size) {
                images[currentIndex]
            } else {
                "Unknown"
            }

        val fileName = currentFile.substringAfterLast('/')
        val extension = currentFile.substringAfterLast('.', "").lowercase()

        Logger.e(TAG, "Video playback error: $fileName, code: ${error.errorCode}, message: ${error.message}")

        val errorDetails = StringBuilder()
        errorDetails.append("=== VIDEO PLAYBACK ERROR ===\n")
        errorDetails.append(
            "Date: ${java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                java.util.Locale.US,
            ).format(java.util.Date())}\n\n",
        )

        errorDetails.append("=== FILE INFO ===\n")
        errorDetails.append("File: $fileName\n")
        errorDetails.append("Path: $currentFile\n")
        errorDetails.append("Source: ${if (isLocalMode) "Local Storage" else "SMB Network"}\n")
        errorDetails.append("Mode: Slideshow\n\n")

        errorDetails.append("=== ERROR DETAILS ===\n")
        errorDetails.append("Exception: ${error.javaClass.simpleName}\n")
        errorDetails.append("Error Code: ${error.errorCode}\n")
        errorDetails.append("Error Code Name: ${getErrorCodeName(error.errorCode)}\n")
        errorDetails.append("Message: ${error.message ?: "No message"}\n")
        errorDetails.append("Cause: ${error.cause?.javaClass?.simpleName ?: "None"}\n")
        if (error.cause != null) {
            errorDetails.append("Cause Message: ${error.cause?.message ?: "No message"}\n")

            // Extract detailed exception info
            val causeStackTrace =
                error
                    .cause
                    ?.stackTrace
                    ?.take(3)
                    ?.joinToString("\n  ") { "at $it" }
            if (causeStackTrace != null) {
                errorDetails.append("Stack Trace:\n  $causeStackTrace\n")
            }
        }

        // Add ExoPlayer-specific diagnostics
        try {
            if (error is androidx.media3.common.PlaybackException) {
                errorDetails.append("Timestamp: ${error.timestampMs}ms\n")

                // Detailed error type analysis
                when (error.errorCode) {
                    androidx
                        .media3
                        .common
                        .PlaybackException
                        .ERROR_CODE_IO_UNSPECIFIED,
                    androidx
                        .media3
                        .common
                        .PlaybackException
                        .ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    androidx
                        .media3
                        .common
                        .PlaybackException
                        .ERROR_CODE_IO_BAD_HTTP_STATUS,
                    androidx
                        .media3
                        .common
                        .PlaybackException
                        .ERROR_CODE_IO_FILE_NOT_FOUND,
                    androidx
                        .media3
                        .common
                        .PlaybackException
                        .ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
                    -> {
                        errorDetails.append("Category: I/O Error (File Access Problem)\n")
                    }
                    androidx
                        .media3
                        .common
                        .PlaybackException
                        .ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                    androidx
                        .media3
                        .common
                        .PlaybackException
                        .ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                    androidx
                        .media3
                        .common
                        .PlaybackException
                        .ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                    -> {
                        errorDetails.append("Category: Parsing Error (Corrupted/Unsupported Format)\n")
                    }
                    androidx
                        .media3
                        .common
                        .PlaybackException
                        .ERROR_CODE_DECODER_INIT_FAILED,
                    androidx
                        .media3
                        .common
                        .PlaybackException
                        .ERROR_CODE_DECODING_FAILED,
                    androidx
                        .media3
                        .common
                        .PlaybackException
                        .ERROR_CODE_DECODER_QUERY_FAILED,
                    -> {
                        errorDetails.append("Category: Decoder Error (Unsupported Codec)\n")
                    }
                }
            }
        } catch (e: Exception) {
            errorDetails.append("(Error analyzing exception details)\n")
        }

        errorDetails.append("\n")

        errorDetails.append("=== PLAYBACK STATE ===\n")
        errorDetails.append("Video Enabled: ${preferenceManager.isVideoEnabled()}\n")
        errorDetails.append("Max Video Size: ${preferenceManager.getMaxVideoSizeMb()} MB\n")
        errorDetails.append("Play Video Till End: ${preferenceManager.isPlayVideoTillEnd()}\n")
        errorDetails.append("\n")

        errorDetails.append("=== SYSTEM INFO ===\n")
        errorDetails.append("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
        errorDetails.append("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
        errorDetails.append("\n")

        errorDetails.append("=== POSSIBLE CAUSES ===\n")

        when {
            error.message?.contains("codec", ignoreCase = true) == true -> {
                errorDetails.append("• Unsupported video codec\n")
                errorDetails.append("• Try converting to MP4 with H.264 codec\n")
            }
            error.message?.contains("source", ignoreCase = true) == true -> {
                errorDetails.append("• File not accessible\n")
                errorDetails.append("• Network connection issue (for SMB)\n")
                errorDetails.append("• File may be corrupted\n")
            }
            error.message?.contains("timeout", ignoreCase = true) == true -> {
                errorDetails.append("• Network timeout (for SMB)\n")
                errorDetails.append("• File too large or slow connection\n")
            }
            error.errorCode ==
                androidx
                    .media3
                    .common
                    .PlaybackException
                    .ERROR_CODE_PARSING_CONTAINER_MALFORMED
            -> {
                errorDetails.append("• Container format corrupted or malformed\n")
                errorDetails.append("• File may be incomplete or damaged during transfer\n")
                errorDetails.append("• Header/metadata sections unreadable\n")
            }
            error.errorCode ==
                androidx
                    .media3
                    .common
                    .PlaybackException
                    .ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
            -> {
                errorDetails.append("• Read position out of file bounds\n")
                errorDetails.append("• File index corrupted or truncated\n")
                errorDetails.append("• Incorrect file size metadata\n")
            }
            else -> {
                errorDetails.append("• Unsupported video format\n")
                errorDetails.append("• Corrupted video file\n")
                errorDetails.append("• Insufficient device resources\n")
            }
        }
        errorDetails.append("\n")

        errorDetails.append("=== RECOMMENDATIONS ===\n")
        errorDetails.append("1. Try playing file with another app\n")
        errorDetails.append("2. Convert to MP4 (H.264 codec)\n")
        errorDetails.append("3. Reduce video resolution\n")
        errorDetails.append("4. Check file is not corrupted\n")
        errorDetails.append("5. For SMB: ensure stable network\n")
        errorDetails.append("6. For SMB connection errors: reconnect to share\n")
        errorDetails.append("\n=== DEBUG INFO ===\n")
        errorDetails.append("SMB Context Available: ${imageRepository.getSmbContext() != null}\n")
        errorDetails.append("Full URI: smb://$currentFile\n")
        errorDetails.append("\nSlideshow will skip to next file...\n")

        com.sza.fastmediasorter.ui.dialogs.DiagnosticDialog.show(
            this,
            "Video Playback Error",
            errorDetails.toString(),
            false,
        )
    }

    private fun showVideoValidationError(
        fileName: String,
        validationResult: com.sza.fastmediasorter.utils.MediaValidator.ValidationResult,
    ) {
        val errorDetails = StringBuilder()
        errorDetails.append("=== VIDEO FILE VALIDATION ERROR ===\n")
        errorDetails.append(
            "Date: ${java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                java.util.Locale.US,
            ).format(java.util.Date())}\n\n",
        )

        errorDetails.append("=== FILE INFO ===\n")
        errorDetails.append("File: $fileName\n")
        errorDetails.append("Extension: .${fileName.substringAfterLast('.', "unknown")}\n")
        errorDetails.append("Source: ${if (isLocalMode) "Local Storage" else "SMB Network"}\n\n")

        errorDetails.append("=== PRE-VALIDATION RESULT ===\n")
        errorDetails.append("Status: FAILED (file rejected before playback attempt)\n")
        errorDetails.append("Error Type: ${validationResult.errorType}\n")
        errorDetails.append("Details: ${validationResult.errorDetails}\n\n")

        errorDetails.append("=== WHY THIS HAPPENED ===\n")
        errorDetails.append("This file was rejected INSTANTLY (< 100ms) by header validation.\n")
        errorDetails.append("No playback was attempted, avoiding the 3-second timeout.\n\n")

        errorDetails.append("=== DIAGNOSIS ===\n")
        when {
            validationResult.errorType?.contains("Size mismatch", ignoreCase = true) == true -> {
                errorDetails.append("▶ FILE TRUNCATED OR CORRUPTED\n")
                errorDetails.append("  The file header declares one size, but actual file is different.\n")
                errorDetails.append("  This is the root cause of ArrayIndexOutOfBoundsException.\n")
                errorDetails.append("  File was likely damaged during transfer or incomplete download.\n\n")
            }
            validationResult.errorType?.contains("Invalid", ignoreCase = true) == true -> {
                errorDetails.append("▶ INVALID FILE FORMAT\n")
                errorDetails.append("  File header doesn't match expected format signature.\n")
                errorDetails.append("  File may be renamed, corrupted, or not a valid video.\n\n")
            }
            validationResult.errorType?.contains("Truncated", ignoreCase = true) == true -> {
                errorDetails.append("▶ FILE TOO SMALL\n")
                errorDetails.append("  File doesn't have enough bytes for a valid header.\n")
                errorDetails.append("  File is incomplete or severely damaged.\n\n")
            }
        }

        errorDetails.append("=== RECOMMENDATION ===\n")
        errorDetails.append("${validationResult.recommendation}\n\n")

        errorDetails.append("=== ACTIONS TAKEN ===\n")
        errorDetails.append("✓ File added to blacklist (won't retry)\n")
        errorDetails.append("✓ Skipping to next media\n")
        errorDetails.append("✓ No 3-second timeout wasted\n")

        com.sza.fastmediasorter.ui.dialogs.DiagnosticDialog.show(
            this,
            "Video Validation Failed",
            errorDetails.toString(),
            false,
        )
    }

    private fun showImageValidationError(
        fileName: String,
        validationResult: com.sza.fastmediasorter.utils.MediaValidator.ValidationResult,
    ) {
        val errorDetails = StringBuilder()
        errorDetails.append("=== IMAGE FILE VALIDATION ERROR ===\n")
        errorDetails.append(
            "Date: ${java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                java.util.Locale.US,
            ).format(java.util.Date())}\n\n",
        )

        errorDetails.append("=== FILE INFO ===\n")
        errorDetails.append("File: $fileName\n")
        errorDetails.append("Extension: .${fileName.substringAfterLast('.', "unknown")}\n")
        errorDetails.append("Source: ${if (isLocalMode) "Local Storage" else "SMB Network"}\n\n")

        errorDetails.append("=== PRE-VALIDATION RESULT ===\n")
        errorDetails.append("Status: FAILED (file rejected before loading)\n")
        errorDetails.append("Error Type: ${validationResult.errorType}\n")
        errorDetails.append("Details: ${validationResult.errorDetails}\n\n")

        errorDetails.append("=== WHY THIS HAPPENED ===\n")
        errorDetails.append("This file was rejected INSTANTLY (< 100ms) by header validation.\n")
        errorDetails.append("No loading attempt was made.\n\n")

        errorDetails.append("=== DIAGNOSIS ===\n")
        when {
            validationResult.errorType?.contains("Size mismatch", ignoreCase = true) == true -> {
                errorDetails.append("▶ FILE TRUNCATED OR CORRUPTED\n")
                errorDetails.append("  The file header declares one size, but actual file is different.\n")
                errorDetails.append("  File was likely damaged during transfer or incomplete download.\n\n")
            }
            validationResult.errorType?.contains("Invalid", ignoreCase = true) == true -> {
                errorDetails.append("▶ INVALID FILE FORMAT\n")
                errorDetails.append("  File header doesn't match expected format signature.\n")
                errorDetails.append("  File may be renamed, corrupted, or not a valid image.\n\n")
            }
            validationResult.errorType?.contains("Truncated", ignoreCase = true) == true -> {
                errorDetails.append("▶ FILE TOO SMALL\n")
                errorDetails.append("  File doesn't have enough bytes for a valid header.\n")
                errorDetails.append("  File is incomplete or severely damaged.\n\n")
            }
            validationResult.errorType?.contains("SOI marker", ignoreCase = true) == true -> {
                errorDetails.append("▶ INVALID JPEG SIGNATURE\n")
                errorDetails.append("  JPEG files must start with 0xFFD8 marker.\n")
                errorDetails.append("  File is not a valid JPEG or is corrupted.\n\n")
            }
            validationResult.errorType?.contains("PNG", ignoreCase = true) == true -> {
                errorDetails.append("▶ INVALID PNG SIGNATURE\n")
                errorDetails.append("  PNG files must start with specific 8-byte signature.\n")
                errorDetails.append("  File is not a valid PNG or is corrupted.\n\n")
            }
        }

        errorDetails.append("=== RECOMMENDATION ===\n")
        errorDetails.append("${validationResult.recommendation}\n\n")

        errorDetails.append("=== ACTIONS TAKEN ===\n")
        errorDetails.append("✓ File added to blacklist (won't retry)\n")
        errorDetails.append("✓ Skipping to next media\n")
        errorDetails.append("✓ Fast rejection prevents decode errors\n")

        // Dismiss any existing diagnostic dialog first
        diagnosticDialog?.dismiss()

        diagnosticDialog =
            com.sza.fastmediasorter.ui.dialogs.DiagnosticDialog.show(
                this,
                "Image Validation Failed",
                errorDetails.toString(),
                false,
            )
    }

    private fun showError(message: String) {
        androidx
            .appcompat
            .app
            .AlertDialog
            .Builder(this)
            .setTitle("Connection Error")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> finishSafely() }
            .setCancelable(false)
            .show()
    }

    private fun finishSafely() {
        // Cancel slideshow
        slideshowJob?.cancel()
        slideshowJob = null

        // Stop video playback
        exoPlayer?.stop()

        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_CURRENT_INDEX, currentIndex)
        outState.putStringArrayList(KEY_IMAGES, ArrayList(images))
    }

    override fun onResume() {
        super.onResume()
        // Update controls visibility in case setting changed
        updateControlsVisibility()
    }

    override fun onPause() {
        super.onPause()
        // Cancel video timeout
        videoTimeoutJob?.cancel()
        // Pause video playback to prevent resource leaks
        exoPlayer?.playWhenReady = false
    }

    override fun onStop() {
        super.onStop()

        // Stop media playback immediately
        exoPlayer?.playWhenReady = false

        // Cancel video timeout
        videoTimeoutJob?.cancel()
        videoTimeoutJob = null

        // Cancel slideshow when activity is no longer visible
        slideshowJob?.cancel()
        slideshowJob = null

        Logger.d(TAG, "onStop: Media playback stopped, jobs cancelled")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupFullscreen()
        if (images.isNotEmpty()) {
            lifecycleScope.launch {
                loadCurrentMedia()
            }
        }
    }

    override fun onDestroy() {
        // Cancel any running coroutines first
        slideshowJob?.cancel()
        slideshowJob = null

        // Dismiss diagnostic dialog to prevent window leak
        try {
            diagnosticDialog?.dismiss()
            diagnosticDialog = null
        } catch (e: Exception) {
            Logger.e(TAG, "Error dismissing diagnostic dialog: ${e.message}")
        }

        // Release media player resources
        try {
            exoPlayer?.stop()
            exoPlayer?.release()
            exoPlayer = null
        } catch (e: Exception) {
            Logger.e(TAG, "Error releasing ExoPlayer: ${e.message}")
        }

        // Clear SMB credentials from memory
        if (!isLocalMode) {
            try {
                imageRepository.smbClient.disconnect()
            } catch (e: Exception) {
                Logger.e(TAG, "Error disconnecting SMB client: ${e.message}")
            }
        }

        super.onDestroy()
        Logger.i(TAG, "========== SlideshowActivity terminated successfully ==========")
    }

    override fun onBackPressed() {
        // Cancel slideshow
        slideshowJob?.cancel()
        slideshowJob = null

        // Stop video playback
        exoPlayer?.stop()

        // Clear session when user manually exits
        preferenceManager.clearLastSession()

        super.onBackPressed()
    }

    private suspend fun handleMediaError(
        mediaUrl: String,
        errorType: String,
        errorMessage: String,
    ) {
        val fileName = mediaUrl.substringAfterLast('/')
        errorLog.add(MediaError(fileName, errorType, errorMessage))
        consecutiveErrors++

        // Add file to blacklist to prevent repeated loading attempts
        blacklistedFiles.add(mediaUrl)
        Logger.w(TAG, "Blacklisted file due to error: $fileName (total blacklisted: ${blacklistedFiles.size})")

        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                // Show detailed error report dialog
                showErrorReportDialog()
            } else {
                // Show enhanced Snackbar with filename and longer duration
                showMediaErrorSnackbar(fileName, errorType)
            }
        }
    }

    private fun showMediaErrorSnackbar(
        fileName: String,
        errorType: String,
    ) {
        val message = "⚠ $errorType error\n📁 $fileName"
        val snackbar =
            Snackbar.make(
                binding.root,
                message,
                Snackbar.LENGTH_LONG,
            )

        // Customize Snackbar appearance
        val snackbarView = snackbar.view
        val textView =
            snackbarView.findViewById<android.widget.TextView>(
                com
                    .google
                    .android
                    .material
                    .R
                    .id
                    .snackbar_text,
            )
        textView.maxLines = 3 // Allow multiple lines
        textView.textSize = 16f // Larger text

        snackbar.show()
    }

    private fun showErrorReportDialog() {
        val recentErrors = errorLog.takeLast(10)
        val errorSummary =
            buildString {
                append("⚠️ MULTIPLE ERRORS DETECTED\n\n")
                append("Consecutive errors: $consecutiveErrors\n")
                append("Total errors: ${errorLog.size}\n\n")
                append("Recent errors:\n")
                append("━".repeat(40))
                append("\n\n")

                recentErrors.forEach { error ->
                    val time =
                        java
                            .text
                            .SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                            .format(java.util.Date(error.timestamp))
                    append("[$time] ${error.errorType}\n")
                    append("File: ${error.fileName}\n")
                    append("Error: ${error.errorMessage}\n\n")
                }

                append("\nPossible causes:\n")
                append("• Network connection issues\n")
                append("• Corrupted or unsupported media files\n")
                append("• SMB server disconnected\n")
                append("• Insufficient permissions\n")
            }

        android
            .app
            .AlertDialog
            .Builder(this)
            .setTitle("Media Loading Issues")
            .setMessage(errorSummary)
            .setPositiveButton("Continue") { dialog, _ ->
                consecutiveErrors = 0
                dialog.dismiss()
            }.setNegativeButton("View Error Log") { _, _ ->
                showFullErrorLog()
            }.setNeutralButton("Exit Slideshow") { _, _ ->
                finishSafely()
            }.setCancelable(false)
            .show()
    }

    private fun showFullErrorLog() {
        val fullLog =
            buildString {
                // TODO: Implement full error log display
                append("Full error log not yet implemented\n")
            }

        AlertDialog
            .Builder(this)
            .setTitle("Full Error Log")
            .setMessage(fullLog)
            .setPositiveButton("OK", null)
            .setNeutralButton("Exit Slideshow") { _, _ ->
                finishSafely()
            }.setCancelable(false)
            .show()
    }

    private suspend fun loadCurrentConfigId(): Long =
        withContext(Dispatchers.IO) {
            try {
                val dao = AppDatabase.getDatabase(this@SlideshowActivity).connectionConfigDao()
                val config =
                    if (isLocalMode) {
                        val localUri = preferenceManager.getLocalUri()
                        val bucketName = preferenceManager.getLocalBucketName()
                        dao.getLocalFolderByName(bucketName.ifEmpty { localUri })
                    } else {
                        val server = preferenceManager.getServerAddress()
                        val folder = preferenceManager.getFolderPath()
                        dao.getConfigByFolderAddress(server, folder)
                    }
                config?.id ?: 0
            } catch (e: Exception) {
                Logger.e(TAG, "Error loading config ID: ${e.message}")
                0
            }
        }

    private suspend fun loadSavedPosition(configId: Long): Int =
        withContext(Dispatchers.IO) {
            try {
                val dao = AppDatabase.getDatabase(this@SlideshowActivity).connectionConfigDao()
                val config = dao.getConfigById(configId)
                config?.lastSlideshowIndex ?: 0
            } catch (e: Exception) {
                Logger.e(TAG, "Error loading saved position: ${e.message}")
                0
            }
        }

    private fun saveCurrentPosition() {
        if (currentConfigId > 0) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val dao = AppDatabase.getDatabase(this@SlideshowActivity).connectionConfigDao()
                    dao.updateLastSlideshowIndex(currentConfigId, currentIndex)
                } catch (e: Exception) {
                    Logger.e(TAG, "Error saving position: ${e.message}")
                }
            }
        }
    }

    private fun getErrorCodeName(errorCode: Int): String =
        when (errorCode) {
            androidx
                .media3
                .common
                .PlaybackException
                .ERROR_CODE_IO_UNSPECIFIED,
            -> "IO_UNSPECIFIED"
            androidx
                .media3
                .common
                .PlaybackException
                .ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            -> "IO_NETWORK_CONNECTION_FAILED"
            androidx
                .media3
                .common
                .PlaybackException
                .ERROR_CODE_IO_BAD_HTTP_STATUS,
            -> "IO_BAD_HTTP_STATUS"
            androidx
                .media3
                .common
                .PlaybackException
                .ERROR_CODE_IO_FILE_NOT_FOUND,
            -> "IO_FILE_NOT_FOUND"
            androidx
                .media3
                .common
                .PlaybackException
                .ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            -> "IO_READ_POSITION_OUT_OF_RANGE"
            androidx
                .media3
                .common
                .PlaybackException
                .ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            -> "PARSING_CONTAINER_MALFORMED"
            androidx
                .media3
                .common
                .PlaybackException
                .ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            -> "PARSING_MANIFEST_MALFORMED"
            androidx
                .media3
                .common
                .PlaybackException
                .ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            -> "PARSING_CONTAINER_UNSUPPORTED"
            androidx
                .media3
                .common
                .PlaybackException
                .ERROR_CODE_DECODER_INIT_FAILED,
            -> "DECODER_INIT_FAILED"
            androidx
                .media3
                .common
                .PlaybackException
                .ERROR_CODE_DECODING_FAILED,
            -> "DECODING_FAILED"
            androidx
                .media3
                .common
                .PlaybackException
                .ERROR_CODE_DECODER_QUERY_FAILED,
            -> "DECODER_QUERY_FAILED"
            androidx
                .media3
                .common
                .PlaybackException
                .ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
            -> "AUDIO_TRACK_WRITE_FAILED"
            androidx
                .media3
                .common
                .PlaybackException
                .ERROR_CODE_DRM_UNSPECIFIED,
            -> "DRM_UNSPECIFIED"
            2000 -> "ERROR_CODE_IO_UNSPECIFIED (Source error)"
            else -> "UNKNOWN ($errorCode)"
        }
}
