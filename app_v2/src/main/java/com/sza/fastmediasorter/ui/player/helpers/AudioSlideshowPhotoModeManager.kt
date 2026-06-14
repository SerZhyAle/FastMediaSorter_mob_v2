package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.sza.fastmediasorter.data.network.glide.NetworkFileData
import com.sza.fastmediasorter.core.debug.MemoryEnduranceTracker
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * Manages fullscreen photo display mode during audio slideshow playback.
 *
 * Responsibilities:
 * - Enter/exit fullscreen photo mode (hide audio UI, show photo)
 * - Load and preload photos via Glide (local + network)
 * - Enforce UI invariants when state observers run
 * - Update photo label and current song label overlays
 * - Advance to next photo on slideshow tick
 *
 * Extracted from PlayerActivity to reduce its size (~315 lines).
 */
class AudioSlideshowPhotoModeManager(
    private val activity: Activity,
    private val binding: ActivityPlayerUnifiedBinding,
    private val viewModel: PlayerViewModel,
    private val audioBackgroundPhotosManagerProvider: () -> AudioBackgroundPhotosManager,
    private val backgroundMusicManagerProvider: () -> BackgroundMusicManager,
    private val dialogAndUiStateManager: PlayerDialogAndUiStateManager,
    private val settingsRepository: SettingsRepository,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val callback: Callback
) {
    private val safeViews = PlayerBindingSafeViews(binding)
    private val context: android.content.Context get() = activity
    private val audioBackgroundPhotosManager: AudioBackgroundPhotosManager
        get() = audioBackgroundPhotosManagerProvider()
    private val backgroundMusicManager: BackgroundMusicManager
        get() = backgroundMusicManagerProvider()

    /** Current mode state - true when fullscreen photo mode is active */
    var isActive: Boolean = false
        private set

    /**
     * Callback for operations that remain in PlayerActivity
     * (thin routing to other managers / ActionBar / system bars).
     */
    interface Callback {
        fun updateSlideShowButton()
        fun updateSystemBarsForPlayer(showCommandPanel: Boolean)
        fun toggleSlideshow()
        fun updateSlideshowState()
        fun getSupportActionBar(): androidx.appcompat.app.ActionBar?
    }

    // ========================================
    // Enter / Exit
    // ========================================

    /**
     * Enter fullscreen photo mode for audio slideshow.
     * Hides audio player UI, shows photo fullscreen with photo path/name in corner.
     * Tap on photo exits back to normal audio player.
     */
    fun enter() {
        if (isActive) return
        isActive = true
        dialogAndUiStateManager.isAudioSlideshowPhotoMode = true
        backgroundMusicManager.isAudioSlideshowPhotoMode = true
        Timber.d("AudioSlideshowPhotoModeManager: Entering audio slideshow photo mode")

        binding.playerView.isVisible = false
        binding.audioCoverArtView.isVisible = false
        binding.audioInfoOverlay.isVisible = false

        binding.imageView.isVisible = true

        // Show current photo path/name overlay
        val photo = audioBackgroundPhotosManager.getCurrentPhoto()
        updatePhotoLabel(photo)

        // Load current photo if available
        if (photo != null) {
            loadBackgroundPhoto(photo)
            preloadNextPhoto()
        }

        // Tap on imageView to return to normal audio player
        binding.imageView.setOnClickListener {
            exit()
            // Also stop slideshow
            if (viewModel.state.value.isSlideShowActive) {
                callback.toggleSlideshow()
                callback.updateSlideshowState()
                callback.updateSlideShowButton()
            }
        }

        // Hide command panel for fullscreen experience
        if (viewModel.state.value.showCommandPanel) {
            viewModel.toggleCommandPanel()
        }

        // Hide toolbar to avoid showing music file name
        binding.toolbar.isVisible = false
        callback.getSupportActionBar()?.hide()
        callback.updateSystemBarsForPlayer(viewModel.state.value.showCommandPanel)

        // Hide overlays that could cover the photo
        binding.controlsOverlay.isVisible = false
        binding.topCommandPanel.isVisible = false

        // Show current song info in bottom-left corner
        updateCurrentSongLabel()
    }

    /**
     * Exit fullscreen photo mode and return to normal audio player UI.
     */
    fun exit() {
        if (!isActive) return
        isActive = false
        dialogAndUiStateManager.isAudioSlideshowPhotoMode = false
        backgroundMusicManager.isAudioSlideshowPhotoMode = false
        Timber.d("AudioSlideshowPhotoModeManager: Exiting audio slideshow photo mode")

        binding.imageView.isVisible = false
        binding.imageView.setImageDrawable(null)
        binding.imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        binding.imageView.setOnClickListener(null)
        binding.audioSlideshowSongLabel.isVisible = false

        // Hide countdown and background music label that may linger
        safeViews.tvCountdown.isVisible = false
        safeViews.tvBackgroundMusicTrack.isVisible = false

        // Restore audio player UI
        binding.playerView.isVisible = true
        binding.audioCoverArtView.isVisible = true
        binding.audioInfoOverlay.isVisible = true

        // Restore command panel
        if (!viewModel.state.value.showCommandPanel) {
            viewModel.toggleCommandPanel()
        }

        // Restore toolbar
        binding.toolbar.isVisible = true
        callback.getSupportActionBar()?.show()
        callback.updateSystemBarsForPlayer(viewModel.state.value.showCommandPanel)
    }

    // ========================================
    // UI Enforcement
    // ========================================

    /**
     * Enforce correct UI state for audio slideshow photo mode.
     * Called after every updateUI() to override any state observer side effects.
     */
    fun enforceUI() {
        binding.imageView.isVisible = true
        binding.playerView.isVisible = false
        binding.audioCoverArtView.isVisible = false
        binding.audioInfoOverlay.isVisible = false

        binding.controlsOverlay.isVisible = false
        binding.topCommandPanel.isVisible = false
        safeViews.tvCountdown.isVisible = false
        safeViews.copyToPanel.isVisible = false
        safeViews.moveToPanel.isVisible = false

        callback.getSupportActionBar()?.hide()
    }

    // ========================================
    // Photo Loading
    // ========================================

    /**
     * Load background photo into ImageView during audio slideshow.
     */
    fun loadBackgroundPhoto(photo: MediaFile) {
        Timber.d("AudioSlideshowPhotoModeManager: Loading background photo: ${photo.name}")

        try {
            val resourceType = audioBackgroundPhotosManager.getPhotoResourceType() ?: ResourceType.LOCAL
            val credentialsId = audioBackgroundPhotosManager.getPhotoCredentialsId()

            val glideRequest = when (resourceType) {
                ResourceType.LOCAL -> {
                    val file = File(photo.path)
                    Glide.with(context).load(file)
                }
                ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP -> {
                    val networkData = NetworkFileData(
                        path = photo.path,
                        credentialsId = credentialsId,
                        loadFullImage = true,
                        highPriority = true,
                        size = photo.size,
                        createdDate = photo.createdDate
                    )
                    Glide.with(context)
                        .load(networkData)
                        .signature(ObjectKey(networkData.getCacheKey()))
                }
                else -> {
                    Timber.w("AudioSlideshowPhotoModeManager: Unsupported resource type: $resourceType")
                    return
                }
            }

            glideRequest
                .priority(Priority.HIGH)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(0))
                .placeholder(binding.imageView.drawable)
                .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable,
                        model: Any,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        dataSource: com.bumptech.glide.load.DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        val imageWidth = resource.intrinsicWidth
                        val imageHeight = resource.intrinsicHeight

                        if (imageWidth > 0 && imageHeight > 0) {
                            lifecycleScope.launch(Dispatchers.Main) {
                                try {
                                    val settings = settingsRepository.getSettings().first()
                                    val (deviceWidth, deviceHeight) = WindowMetricsCompat.getScreenSize(
                                        activity.windowManager
                                    )

                                    val scaleType = com.sza.fastmediasorter.ui.image.ImageDisplayUtils.determineImageScaleType(
                                        cropImagesToFullscreen = settings.cropImagesToFullscreen,
                                        isFullscreenOrSlideshow = true,
                                        imageWidth = imageWidth,
                                        imageHeight = imageHeight,
                                        deviceWidth = deviceWidth,
                                        deviceHeight = deviceHeight
                                    )

                                    binding.imageView.scaleType = scaleType
                                    Timber.d("AudioSlideshow: Applied scale type $scaleType (image: ${imageWidth}x${imageHeight}, device: ${deviceWidth}x${deviceHeight})")
                                } catch (e: Exception) {
                                    Timber.e(e, "AudioSlideshow: Error determining scale type, using FIT_CENTER")
                                    binding.imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                                }
                            }
                        } else {
                            binding.imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                        }

                        preloadNextPhoto()
                        return false
                    }

                    override fun onLoadFailed(
                        e: com.bumptech.glide.load.engine.GlideException?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        Timber.e(e, "Failed to load audio slideshow photo")
                        return false
                    }
                })
                .into(binding.imageView)

        } catch (e: Exception) {
            Timber.e(e, "AudioSlideshowPhotoModeManager: Error loading background photo")
        }
    }

    /**
     * Preload next photo for smooth transitions in audio slideshow mode.
     */
    fun preloadNextPhoto() {
        try {
            val nextPhoto = audioBackgroundPhotosManager.getNextPhoto() ?: return
            val resourceType = audioBackgroundPhotosManager.getPhotoResourceType() ?: ResourceType.LOCAL
            val credentialsId = audioBackgroundPhotosManager.getPhotoCredentialsId()

            Timber.d("AudioSlideshow: Preloading next photo: ${nextPhoto.name}")

            when (resourceType) {
                ResourceType.LOCAL -> {
                    val file = File(nextPhoto.path)
                    Glide.with(context)
                        .load(file)
                        .priority(Priority.HIGH)
                        .preload()
                }
                ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP -> {
                    val networkData = NetworkFileData(
                        path = nextPhoto.path,
                        credentialsId = credentialsId,
                        loadFullImage = true,
                        highPriority = true,
                        size = nextPhoto.size,
                        createdDate = nextPhoto.createdDate
                    )
                    Glide.with(context)
                        .load(networkData)
                        .signature(ObjectKey(networkData.getCacheKey()))
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .priority(Priority.HIGH)
                        .preload()
                }
                else -> {
                    Timber.w("AudioSlideshow: Unsupported resource type for preload: $resourceType")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "AudioSlideshow: Error preloading next photo")
        }
    }

    // ========================================
    // Slideshow Tick
    // ========================================

    /**
     * Advance to next photo for audio background photos feature.
     * Called by PlayerNavigationManager on slideshow timer tick.
     */
    fun advancePhoto() {
        val intervalSec = (viewModel.state.value.slideShowInterval / 1000).toInt()
        Timber.d("AudioSlideshow: advanceAudioBackgroundPhoto called (interval=${intervalSec}s)")
        MemoryEnduranceTracker.checkpoint("TRANSITION", "MIX-audio-slideshow") // S0120
        audioBackgroundPhotosManager.advanceToNextPhoto()
    }

    // ========================================
    // Labels
    // ========================================

    /**
     * Update the photo name overlay label.
     */
    fun updatePhotoLabel(photo: MediaFile?) {
        val labelText = when {
            photo == null -> ""
            photo.name.isNotBlank() -> photo.name.substringBeforeLast('.')
            photo.path.isNotBlank() -> photo.path.substringAfterLast('/').substringBeforeLast('.')
            else -> ""
        }
        binding.audioSlideshowSongLabel.text = labelText
        binding.audioSlideshowSongLabel.isVisible = isActive && labelText.isNotBlank()
    }

    /**
     * Update the current song label (tvBackgroundMusicTrack) during audio slideshow photo mode.
     * Shows "♪ Artist - Track" from audio metadata, or falls back to file name.
     * Called when entering mode, changing tracks, or updating metadata.
     */
    fun updateCurrentSongLabel() {
        if (!isActive) return

        val metadata = safeViews.audioMetadata.text?.toString()
        val songText = if (!metadata.isNullOrBlank()) {
            "♪ $metadata"
        } else {
            val currentFile = viewModel.state.value.currentFile
            if (currentFile != null) {
                "♪ ${currentFile.name.substringBeforeLast('.')}"
            } else ""
        }

        if (songText.isNotBlank()) {
            safeViews.tvBackgroundMusicTrack.text = songText
            safeViews.tvBackgroundMusicTrack.isVisible = true
        } else {
            safeViews.tvBackgroundMusicTrack.isVisible = false
        }
    }
}
