package com.sza.fastmediasorter.ui.player.helpers

import android.view.MotionEvent
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import com.sza.fastmediasorter.ui.player.SlideshowController
import timber.log.Timber
import kotlin.random.Random

/**
 * Manager for file navigation and slideshow functionality in PlayerActivity.
 * Handles all navigation triggers (touch zones, buttons, gestures, mouse wheel)
 * and coordinates slideshow auto-advance with countdown display.
 *
 * Responsibilities:
 * - File navigation (next/previous) triggered by various UI events
 * - Slideshow controller management and state synchronization
 * - Countdown display for slideshow auto-advance
 * - Navigation event logging and debugging
 */
class PlayerNavigationManager(
    private val activity: PlayerActivity,
    private val viewModel: PlayerViewModel,
    lifecycle: Lifecycle
) {

    private lateinit var slideshowController: SlideshowController

    init {
        initializeSlideshowController(lifecycle)
    }

    /**
     * Initialize the slideshow controller with navigation callbacks
     */
    private fun initializeSlideshowController(lifecycle: Lifecycle) {
        slideshowController = SlideshowController(
            lifecycle = lifecycle,
            slideshowCallback = object : SlideshowController.SlideshowCallback {
                override fun onSlideAdvance(): Boolean {
                    val currentFile = viewModel.state.value.currentFile
                    
                    // Audio slideshow mode: advance photo, not file
                    if (currentFile?.type == MediaType.AUDIO &&
                        viewModel.state.value.enablePhotosDuringAudio &&
                        viewModel.state.value.audioBackgroundPhotosResourceId != null) {
                        val intervalSec = (viewModel.state.value.slideShowInterval / 1000).toInt()
                        Timber.d("AudioSlideshow: onSlideAdvance - advancing photo (interval=${intervalSec}s)")
                        activity.advanceAudioBackgroundPhoto()
                        return true
                    }
                    
                    val isMediaPlaying = currentFile?.type == MediaType.VIDEO ||
                                       currentFile?.type == MediaType.AUDIO

                    // If playToEndInSlideshow is enabled and media is playing, don't auto-advance
                    if (viewModel.state.value.playToEndInSlideshow && isMediaPlaying) {
                        // Will be handled by exoPlayerListener.STATE_ENDED
                        // Don't restart timer - wait for playback to end
                        return false
                    } else {
                        // For images or when playToEnd is disabled - normal auto-advance
                        // Skip documents (PDF, TXT, EPUB) in slideshow
                        Timber.tag("TOUCH_ZONE_DEBUG").w("NEXT triggered by: Slideshow auto-advance")
                        navigateNext(skipDocuments = true)
                        return true
                    }
                }

                override fun onSlideshowStateChanged(isActive: Boolean, isPaused: Boolean) {
                    // Synchronize ViewModel state with controller state directly (no toggle to avoid loops)
                    viewModel.setSlideShowActive(isActive)
                    viewModel.setPaused(isPaused)
                    // S0550: this callback can fire during PlayerActivity init (initial displayImage ->
                    // updateSlideshowState -> stopSlideshow) before dialogAndUiStateManager is assigned in
                    // initUiCoordinators. Skip the UI sync until ready; the default inactive button state is
                    // rendered correctly once init completes and state observers fire.
                    if (activity.isDialogAndUiStateManagerInitialized) {
                        Timber.d("S0550: onSlideshowStateChanged UI sync (active=$isActive paused=$isPaused)")
                        activity.dialogAndUiStateManager.updateSlideShowButton()
                    }
                    activity.updatePlayPauseButton()
                    
                    // Auto-enter fullscreen when slideshow starts
                    if (isActive && !isPaused) {
                        viewModel.enterFullscreenMode()
                    }
                }

                override fun onCountdownTick(seconds: Int) {
                    // S0550: same init-time race guard as onSlideshowStateChanged.
                    if (activity.isDialogAndUiStateManagerInitialized) {
                        activity.dialogAndUiStateManager.updateCountdownDisplay(seconds)
                    }
                }
                
                override fun onMemoryCacheClear() {
                    activity.clearImageMemoryCache()
                }
                
                override fun setKeepScreenAwake(enabled: Boolean) {
                    activity.setSlideshowKeepAwake(enabled)
                }

                override fun shouldSuppressTimer(): Boolean {
                    val state = viewModel.state.value
                    val currentFile = state.currentFile
                    val isMedia = currentFile?.type == MediaType.VIDEO ||
                                  currentFile?.type == MediaType.AUDIO
                    return state.playToEndInSlideshow && isMedia
                }
            }
        )
    }

    /**
     * Handle mouse wheel scroll for navigation
     */
    fun handleMouseWheelScroll(verticalScroll: Float): Boolean {
        if (verticalScroll != 0f) {
            if (verticalScroll > 0) {
                // Scroll up = previous file
                Timber.tag("TOUCH_ZONE_DEBUG").w("PREVIOUS triggered by: Mouse scroll UP")
                navigatePrevious(manual = true)
            } else {
                // Scroll down = next file
                Timber.tag("TOUCH_ZONE_DEBUG").w("NEXT triggered by: Mouse scroll DOWN")
                navigateNext(manual = true)
            }
            return true
        }
        return false
    }

    /**
     * Handle touch zone navigation
     */
    fun handleTouchZoneNavigation(zone: com.sza.fastmediasorter.ui.player.PlayerGestureHelper.TouchZone) {
        when (zone) {
            com.sza.fastmediasorter.ui.player.PlayerGestureHelper.TouchZone.LEFT -> {
                Timber.tag("TOUCH_ZONE_DEBUG").w("PREVIOUS triggered by: 2-zone LEFT touch (GestureHelper)")
                navigatePrevious(manual = true)
            }
            com.sza.fastmediasorter.ui.player.PlayerGestureHelper.TouchZone.RIGHT -> {
                Timber.tag("TOUCH_ZONE_DEBUG").w("NEXT triggered by: 2-zone RIGHT touch (GestureHelper)")
                navigateNext(manual = true)
            }
            com.sza.fastmediasorter.ui.player.PlayerGestureHelper.TouchZone.CENTER -> {
                viewModel.togglePause()
                if (viewModel.state.value.currentFile?.type == MediaType.IMAGE) {
                    activity.updateSlideShow()
                }
            }
            com.sza.fastmediasorter.ui.player.PlayerGestureHelper.TouchZone.COPY_PANEL -> activity.dialogAndUiStateManager.showCopyDialog()
            com.sza.fastmediasorter.ui.player.PlayerGestureHelper.TouchZone.MOVE_PANEL -> activity.dialogAndUiStateManager.showMoveDialog()
            com.sza.fastmediasorter.ui.player.PlayerGestureHelper.TouchZone.DELETE -> activity.fileOperationsHandler.deleteCurrentFile()
        }
    }

    /**
     * Navigate to previous file (UI button callback)
     */
    fun navigatePreviousFromButton() {
        Timber.tag("TOUCH_ZONE_DEBUG").w("PREVIOUS triggered by: UI Manager PREVIOUS button clicked")
        navigatePrevious(manual = true)
        activity.scheduleHideControls()
    }

    /**
     * Navigate to next file (UI button callback)
     */
    fun navigateNextFromButton() {
        Timber.tag("TOUCH_ZONE_DEBUG").w("NEXT triggered by: UI Manager NEXT button clicked")
        navigateNext(manual = true)
        activity.scheduleHideControls()
    }

    /**
     * Navigate to a random file from the current library profile.
     */
    fun navigateRandomFromButton() {
        val currentState = viewModel.state.value
        val files = currentState.files
        if (files.size <= 1) {
            Timber.d("navigateRandomFromButton: skipped because files.size=${files.size}")
            return
        }

        val currentIndex = currentState.currentIndex.coerceIn(files.indices)
        // Wrapped offset keeps all non-current files equally likely without allocating candidates.
        val randomOffset = Random.nextInt(files.size - 1) + 1
        val randomIndex = (currentIndex + randomOffset) % files.size
        Timber.tag("TOUCH_ZONE_DEBUG").w("RANDOM triggered by: UI Manager RANDOM button clicked -> $currentIndex to $randomIndex")
        activity.onManualSlideshowNavigation()
        viewModel.jumpToIndex(randomIndex, manual = true)

        if (currentState.currentFile?.type == MediaType.AUDIO) {
            activity.advanceAudioBackgroundPhoto()
            activity.updateAudioSlideshowCurrentSongLabel()
        }
        activity.scheduleHideControls()
    }

    /**
     * Navigate to previous file (gesture callback)
     */
    fun navigatePreviousFromGesture() {
        Timber.tag("TOUCH_ZONE_DEBUG").w("PREVIOUS triggered by: Gesture PREVIOUS")
        navigatePrevious(manual = true)
    }

    /**
     * Navigate to next file (gesture callback)
     */
    fun navigateNextFromGesture() {
        Timber.tag("TOUCH_ZONE_DEBUG").w("NEXT triggered by: Gesture NEXT")
        navigateNext(manual = true)
    }

    /**
     * Navigate to previous file
     */
    private fun navigatePrevious(skipDocuments: Boolean = false, manual: Boolean = false) {
        val currentFileBeforeNav = viewModel.state.value.currentFile
        if (manual) {
            activity.onManualSlideshowNavigation()
        }
        viewModel.previousFile(skipDocuments, manual)
        // Advance to next photo if was playing audio (file type checked before navigation)
        if (currentFileBeforeNav?.type == MediaType.AUDIO) {
            activity.advanceAudioBackgroundPhoto()
            // Update current song label after track change
            activity.updateAudioSlideshowCurrentSongLabel()
        }
    }

    /**
     * Navigate to next file
     */
    private fun navigateNext(skipDocuments: Boolean = false, manual: Boolean = false) {
        val currentFileBeforeNav = viewModel.state.value.currentFile
        if (manual) {
            activity.onManualSlideshowNavigation()
        }
        viewModel.nextFile(skipDocuments, manual)
        // Advance to next photo if was playing audio (file type checked before navigation)
        if (currentFileBeforeNav?.type == MediaType.AUDIO) {
            activity.advanceAudioBackgroundPhoto()
            // Update current song label after track change
            activity.updateAudioSlideshowCurrentSongLabel()
        }
    }

    /**
     * Handle slideshow control from UI
     */
    fun handleSlideshowControl() {
        syncMediaLibraryMode()
        if (slideshowController.isActive()) {
            if (slideshowController.isPaused()) {
                slideshowController.resumeSlideshow()
            } else {
                slideshowController.pauseSlideshow()
            }
        } else {
            // Start slideshow with current interval
            val interval = viewModel.state.value.slideShowInterval.toInt()
            slideshowController.startSlideshow(interval)
        }
    }

    /**
     * Update slideshow interval
     */
    fun updateSlideshowInterval(intervalSeconds: Int) {
        slideshowController.updateInterval(intervalSeconds)
        if (slideshowController.isActive() && !slideshowController.isPaused()) {
            slideshowController.restartTimer()
        }
    }

    /**
     * Handle play/pause state change for slideshow coordination
     */
    fun handlePlaybackStateChange(isPaused: Boolean) {
        if (isPaused) {
            slideshowController.pauseSlideshow()
        } else {
            slideshowController.resumeSlideshow()
        }
    }

    /**
     * Check if slideshow is active
     */
    fun isSlideshowActive(): Boolean = slideshowController.isActive()

    /**
     * Get slideshow controller for other managers that need it
     */
    fun getSlideshowController(): SlideshowController = slideshowController

    /**
     * Navigate to next file (from auto-advance after copy/move/delete)
     */
    fun navigateNextAfterOperation(reason: String) {
        Timber.tag("TOUCH_ZONE_DEBUG").w("NEXT triggered by: $reason")
        navigateNext()
    }

    /**
     * Navigate to previous file (from touch zone)
     */
    fun navigatePreviousFromTouchZone() {
        Timber.tag("TOUCH_ZONE_DEBUG").w("PREVIOUS triggered by: Touch zone click")
        navigatePrevious(manual = true)
    }

    /**
     * Navigate to next file (from touch zone)
     */
    fun navigateNextFromTouchZone() {
        Timber.tag("TOUCH_ZONE_DEBUG").w("NEXT triggered by: Touch zone click")
        navigateNext(manual = true)
    }

    /**
     * Navigate to previous file (from keyboard/remote control)
     */
    fun navigatePreviousFromControl(manual: Boolean = true) {
        navigatePrevious(manual = manual)
    }

    /**
     * Navigate to next file (from keyboard/remote control)
     */
    fun navigateNextFromControl(manual: Boolean = true) {
        navigateNext(manual = manual)
    }

    /**
     * Navigate to previous file (from mouse scroll - already logged)
     */
    fun navigatePreviousFromMouseScroll() {
        navigatePrevious(manual = true)
    }

    /**
     * Navigate to next file (from mouse scroll - already logged)
     */
    fun navigateNextFromMouseScroll() {
        navigateNext(manual = true)
    }

    /**
     * Toggle slideshow mode (for UI button callbacks)
     */
    fun toggleSlideshow() {
        viewModel.toggleSlideShow()
    }

    /**
     * Sync media library mode flag on the controller based on current resource profile.
     * AUDIO_LIBRARY / VIDEO_LIBRARY → no timer/countdown; advance via onPlaybackEnded only.
     */
    private fun syncMediaLibraryMode() {
        val profile = viewModel.state.value.resource?.profile
        slideshowController.isMediaLibraryMode =
            profile == ResourceProfile.AUDIO_LIBRARY || profile == ResourceProfile.VIDEO_LIBRARY
    }

    /**
     * Update slideshow state based on ViewModel state
     * Synchronizes controller with current ViewModel slideshow/pause state
     */
    fun updateSlideshowState() {
        syncMediaLibraryMode()
        if (viewModel.state.value.isSlideShowActive && !viewModel.state.value.isPaused) {
            val intervalSeconds = (viewModel.state.value.slideShowInterval / 1000).toInt()
            
            // Enable slideshow bias for efficient prefetching during slideshow
            activity.imageLoadingManager.setSlideshowBias(true)
            
            if (!slideshowController.isActive()) {
                slideshowController.startSlideshow(intervalSeconds)
            } else {
                slideshowController.updateInterval(intervalSeconds)
                slideshowController.restartTimer()
            }
        } else {
            // Disable slideshow bias when slideshow ends
            activity.imageLoadingManager.setSlideshowBias(false)
            slideshowController.stopSlideshow()
        }
    }
}