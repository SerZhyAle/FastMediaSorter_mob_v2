package com.sza.fastmediasorter.ui.player.helpers

import android.view.MotionEvent
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import com.sza.fastmediasorter.ui.player.SlideshowController
import timber.log.Timber

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
                    activity.updateSlideShowButton()
                    activity.updatePlayPauseButton()
                    
                    // Auto-enter fullscreen when slideshow starts
                    if (isActive && !isPaused) {
                        viewModel.enterFullscreenMode()
                    }
                }

                override fun onCountdownTick(seconds: Int) {
                    activity.updateCountdownDisplay(seconds)
                }
                
                override fun onMemoryCacheClear() {
                    activity.clearImageMemoryCache()
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
                navigatePrevious()
            } else {
                // Scroll down = next file
                Timber.tag("TOUCH_ZONE_DEBUG").w("NEXT triggered by: Mouse scroll DOWN")
                navigateNext()
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
                navigatePrevious()
            }
            com.sza.fastmediasorter.ui.player.PlayerGestureHelper.TouchZone.RIGHT -> {
                Timber.tag("TOUCH_ZONE_DEBUG").w("NEXT triggered by: 2-zone RIGHT touch (GestureHelper)")
                navigateNext()
            }
            com.sza.fastmediasorter.ui.player.PlayerGestureHelper.TouchZone.CENTER -> {
                viewModel.togglePause()
                if (viewModel.state.value.currentFile?.type == MediaType.IMAGE) {
                    activity.updateSlideShow()
                }
            }
            com.sza.fastmediasorter.ui.player.PlayerGestureHelper.TouchZone.COPY_PANEL -> activity.showCopyDialog()
            com.sza.fastmediasorter.ui.player.PlayerGestureHelper.TouchZone.MOVE_PANEL -> activity.showMoveDialog()
            com.sza.fastmediasorter.ui.player.PlayerGestureHelper.TouchZone.DELETE -> activity.deleteCurrentFile()
        }
    }

    /**
     * Navigate to previous file (UI button callback)
     */
    fun navigatePreviousFromButton() {
        Timber.tag("TOUCH_ZONE_DEBUG").w("PREVIOUS triggered by: UI Manager PREVIOUS button clicked")
        navigatePrevious()
        activity.scheduleHideControls()
    }

    /**
     * Navigate to next file (UI button callback)
     */
    fun navigateNextFromButton() {
        Timber.tag("TOUCH_ZONE_DEBUG").w("NEXT triggered by: UI Manager NEXT button clicked")
        navigateNext()
        activity.scheduleHideControls()
    }

    /**
     * Navigate to previous file (gesture callback)
     */
    fun navigatePreviousFromGesture() {
        Timber.tag("TOUCH_ZONE_DEBUG").w("PREVIOUS triggered by: Gesture PREVIOUS")
        navigatePrevious()
    }

    /**
     * Navigate to next file (gesture callback)
     */
    fun navigateNextFromGesture() {
        Timber.tag("TOUCH_ZONE_DEBUG").w("NEXT triggered by: Gesture NEXT")
        navigateNext()
    }

    /**
     * Navigate to previous file
     */
    private fun navigatePrevious(skipDocuments: Boolean = false) {
        val currentFileBeforeNav = viewModel.state.value.currentFile
        viewModel.previousFile(skipDocuments)
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
    private fun navigateNext(skipDocuments: Boolean = false) {
        val currentFileBeforeNav = viewModel.state.value.currentFile
        viewModel.nextFile(skipDocuments)
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
        navigatePrevious()
    }

    /**
     * Navigate to next file (from touch zone)
     */
    fun navigateNextFromTouchZone() {
        Timber.tag("TOUCH_ZONE_DEBUG").w("NEXT triggered by: Touch zone click")
        navigateNext()
    }

    /**
     * Navigate to previous file (from keyboard/remote control)
     */
    fun navigatePreviousFromControl() {
        navigatePrevious()
    }

    /**
     * Navigate to next file (from keyboard/remote control)
     */
    fun navigateNextFromControl() {
        navigateNext()
    }

    /**
     * Navigate to previous file (from mouse scroll - already logged)
     */
    fun navigatePreviousFromMouseScroll() {
        navigatePrevious()
    }

    /**
     * Navigate to next file (from mouse scroll - already logged)
     */
    fun navigateNextFromMouseScroll() {
        navigateNext()
    }

    /**
     * Toggle slideshow mode (for UI button callbacks)
     */
    fun toggleSlideshow() {
        viewModel.toggleSlideShow()
    }

    /**
     * Update slideshow state based on ViewModel state
     * Synchronizes controller with current ViewModel slideshow/pause state
     */
    fun updateSlideshowState() {
        if (viewModel.state.value.isSlideShowActive && !viewModel.state.value.isPaused) {
            val intervalSeconds = (viewModel.state.value.slideShowInterval / 1000).toInt()
            if (!slideshowController.isActive()) {
                slideshowController.startSlideshow(intervalSeconds)
            } else {
                slideshowController.updateInterval(intervalSeconds)
                slideshowController.restartTimer()
            }
        } else {
            slideshowController.stopSlideshow()
        }
    }
}