package com.sza.fastmediasorter.ui.player

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.core.constants.AppConstants
import timber.log.Timber

/**
 * Controller for slideshow functionality in PlayerActivity.
 * Manages auto-advance timer, countdown display, and play/pause state.
 * Supports renderer readiness check before slide advance (D.6).
 * 
 * Responsibilities:
 * - Slideshow timer management (start/stop/pause/resume)
 * - Countdown display (3-2-1)
 * - Auto-advance to next media file (with renderer readiness gate)
 * - State tracking (active/paused)
 */
class SlideshowController(
    private val lifecycle: Lifecycle,
    private val slideshowCallback: SlideshowCallback
) : DefaultLifecycleObserver {
    
    /**
     * Callback interface for slideshow events
     */
    interface SlideshowCallback {
        fun onSlideAdvance(): Boolean
        fun onSlideshowStateChanged(isActive: Boolean, isPaused: Boolean)
        fun onCountdownTick(seconds: Int)
        fun onMemoryCacheClear()
        /**
         * Check if renderer is ready for next slide (D.6).
         * Returns true if no renderer migration is active, or renderer state is Ready.
         * Default returns true for backwards compatibility.
         */
        fun isRendererReady(): Boolean = true
        /**
         * Set screen keep-awake state during slideshow (D.8).
         * @param enabled true to force screen on, false to restore to global setting
         */
        fun setKeepScreenAwake(enabled: Boolean) = Unit
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private val countdownHandler = Handler(Looper.getMainLooper())
    
    private var isActive = false
    private var isPaused = false
    private var intervalMs = AppConstants.DEFAULT_SLIDESHOW_INTERVAL_MS
    private var slideCounter = 0
    
    private val slideShowRunnable = object : Runnable {
        override fun run() {
            // Check if lifecycle is at least STARTED before proceeding
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                Timber.w("SlideshowController: Slide advance skipped - lifecycle not STARTED")
                return
            }

            // D.6: State validation - skip on stale/cancelled state
            if (!isActive || isPaused) {
                Timber.d("SlideshowController: Slide advance skipped - state stale (active=$isActive, paused=$isPaused)")
                return
            }

            // D.6: Renderer readiness gate - wait if renderer not ready
            if (!slideshowCallback.isRendererReady()) {
                Timber.d("SlideshowController: Renderer not ready, delaying slide advance by 100ms")
                handler.postDelayed(this, 100L) // Retry after short delay
                return
            }
            
            // Clear countdown display before advancing
            slideshowCallback.onCountdownTick(0) // Signal to clear countdown
            
            val shouldSchedule = slideshowCallback.onSlideAdvance()
            
            // Clear memory cache every 5 slides to prevent OOM during long slideshows
            slideCounter++
            if (slideCounter >= 5) {
                slideshowCallback.onMemoryCacheClear()
                slideCounter = 0
            }
            
            if (shouldSchedule) {
                scheduleNextSlide()
            }
        }
    }
    
    private val countdownRunnable = object : Runnable {
        var remainingSeconds = 3
        
        override fun run() {
            // Check if lifecycle is at least STARTED before updating UI
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                Timber.w("SlideshowController: Countdown skipped - lifecycle not STARTED")
                return
            }
            
            if (remainingSeconds > 0) {
                slideshowCallback.onCountdownTick(remainingSeconds)
                remainingSeconds--
                countdownHandler.postDelayed(this, AppConstants.SLIDESHOW_COUNTDOWN_TICK_MS)
            }
        }
    }
    
    init {
        lifecycle.addObserver(this)
    }
    
    /**
     * Start slideshow with specified interval
     * @param intervalSeconds interval between slides in seconds
     */
    fun startSlideshow(intervalSeconds: Int) {
        Timber.d("SlideshowController: Starting slideshow with interval ${intervalSeconds}s")
        
        intervalMs = intervalSeconds * 1000L
        isActive = true
        isPaused = false
        
        // D.8: Force screen-on during slideshow
        slideshowCallback.setKeepScreenAwake(true)
        
        // Don't show countdown immediately - it will be shown 3 seconds before file change
        scheduleNextSlide()
        
        slideshowCallback.onSlideshowStateChanged(isActive, isPaused)
    }
    
    /**
     * Pause slideshow (can be resumed)
     */
    fun pauseSlideshow() {
        Timber.d("SlideshowController: Pausing slideshow")
        
        if (!isActive) return
        
        isPaused = true
        handler.removeCallbacks(slideShowRunnable)
        countdownHandler.removeCallbacks(countdownRunnable)
        
        slideshowCallback.onSlideshowStateChanged(isActive, isPaused)
    }
    
    /**
     * Resume paused slideshow
     */
    fun resumeSlideshow() {
        Timber.d("SlideshowController: Resuming slideshow")
        
        if (!isActive || !isPaused) return
        
        isPaused = false
        scheduleNextSlide()
        
        slideshowCallback.onSlideshowStateChanged(isActive, isPaused)
    }
    
    /**
     * Stop slideshow completely
     */
    fun stopSlideshow() {
        Timber.d("SlideshowController: Stopping slideshow")
        
        isActive = false
        isPaused = false
        handler.removeCallbacks(slideShowRunnable)
        countdownHandler.removeCallbacks(countdownRunnable)
        
        // D.8: Restore screen-on to global setting
        slideshowCallback.setKeepScreenAwake(false)
        
        slideshowCallback.onSlideshowStateChanged(isActive, isPaused)
    }
    
    /**
     * Update slideshow interval
     * @param intervalSeconds new interval in seconds
     */
    fun updateInterval(intervalSeconds: Int) {
        intervalMs = intervalSeconds * 1000L
        
        if (isActive && !isPaused) {
            // Reschedule with new interval
            handler.removeCallbacks(slideShowRunnable)
            scheduleNextSlide()
        }
    }
    
    /**
     * Check if slideshow is active
     */
    fun isActive(): Boolean = isActive
    
    /**
     * Check if slideshow is paused
     */
    fun isPaused(): Boolean = isPaused
    
    /**
     * Restart current slide timer (call after manual navigation)
     */
    fun restartTimer() {
        if (isActive && !isPaused) {
            handler.removeCallbacks(slideShowRunnable)
            scheduleNextSlide()
        }
    }
    
    private fun scheduleNextSlide() {
        // Schedule countdown to start 3 seconds before file change
        val countdownDelay = if (intervalMs > 3000) intervalMs - 3000 else 0
        if (countdownDelay > 0) {
            countdownHandler.postDelayed({ showCountdown() }, countdownDelay)
        }
        
        // Schedule file change
        handler.postDelayed(slideShowRunnable, intervalMs)
    }
    
    private fun showCountdown() {
        countdownRunnable.remainingSeconds = 3
        countdownHandler.post(countdownRunnable)
    }
    
    // Lifecycle callbacks
    override fun onPause(owner: LifecycleOwner) {
        // Keep slideshow state but stop timers
        handler.removeCallbacks(slideShowRunnable)
        countdownHandler.removeCallbacks(countdownRunnable)
    }
    
    override fun onResume(owner: LifecycleOwner) {
        // Resume timers if slideshow was active
        if (isActive && !isPaused) {
            scheduleNextSlide()
        }
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        cleanup()
    }
    
    private fun cleanup() {
        handler.removeCallbacks(slideShowRunnable)
        countdownHandler.removeCallbacks(countdownRunnable)
        lifecycle.removeObserver(this)
        // Reset state to prevent further operations
        isActive = false
        isPaused = false
        Timber.d("SlideshowController: Cleaned up")
    }
}
