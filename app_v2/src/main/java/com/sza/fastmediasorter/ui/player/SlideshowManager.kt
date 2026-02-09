package com.sza.fastmediasorter.ui.player

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.core.constants.AppConstants
import timber.log.Timber

/**
 * Manager for slideshow functionality in PlayerActivity.
 * Manages auto-advance timer, countdown display, and play/pause state.
 * 
 * Responsibilities:
 * - Slideshow timer management (start/stop/pause/resume)
 * - Countdown display (3-2-1)
 * - Auto-advance to next media file
 * - State tracking (active/paused)
 */
class SlideshowManager(
    private val lifecycle: Lifecycle,
    private val callback: SlideshowCallback
) : DefaultLifecycleObserver {
    
    /**
     * Callback interface for slideshow events
     */
    interface SlideshowCallback {
        fun onSlideAdvance()
        fun onSlideshowStateChanged(isActive: Boolean, isPaused: Boolean)
        fun onCountdownTick(seconds: Int)
        fun onMemoryCacheClear()
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private val countdownHandler = Handler(Looper.getMainLooper())
    
    private var isActive = false
    private var isPaused = false
    private var intervalMs = AppConstants.DEFAULT_SLIDESHOW_INTERVAL_MS
    private var slideCounter = 0
    
    private val slideShowRunnable = object : Runnable {
        override fun run() {
            if (isActive && !isPaused) {
                // Clear countdown display before advancing
                callback.onCountdownTick(0) // Signal to clear countdown
                
                callback.onSlideAdvance()
                
                // Clear memory cache every 5 slides to prevent OOM during long slideshows
                slideCounter++
                if (slideCounter >= 5) {
                    callback.onMemoryCacheClear()
                    slideCounter = 0
                }
                
                scheduleNextSlide()
            }
        }
    }
    
    private val countdownRunnable = object : Runnable {
        var remainingSeconds = 3
        
        override fun run() {
            if (remainingSeconds > 0) {
                callback.onCountdownTick(remainingSeconds)
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
        Timber.d("SlideshowManager: Starting slideshow with interval ${intervalSeconds}s")
        
        intervalMs = intervalSeconds * 1000L
        isActive = true
        isPaused = false
        
        // Don't show countdown immediately - it will be shown 3 seconds before file change
        scheduleNextSlide()
        
        callback.onSlideshowStateChanged(isActive, isPaused)
    }
    
    /**
     * Pause slideshow (can be resumed)
     */
    fun pauseSlideshow() {
        Timber.d("SlideshowManager: Pausing slideshow")
        
        if (!isActive) return
        
        isPaused = true
        handler.removeCallbacks(slideShowRunnable)
        countdownHandler.removeCallbacks(countdownRunnable)
        
        callback.onSlideshowStateChanged(isActive, isPaused)
    }
    
    /**
     * Resume paused slideshow
     */
    fun resumeSlideshow() {
        Timber.d("SlideshowManager: Resuming slideshow")
        
        if (!isActive || !isPaused) return
        
        isPaused = false
        scheduleNextSlide()
        
        callback.onSlideshowStateChanged(isActive, isPaused)
    }
    
    /**
     * Stop slideshow completely
     */
    fun stopSlideshow() {
        Timber.d("SlideshowManager: Stopping slideshow")
        
        isActive = false
        isPaused = false
        handler.removeCallbacks(slideShowRunnable)
        countdownHandler.removeCallbacks(countdownRunnable)
        
        callback.onSlideshowStateChanged(isActive, isPaused)
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
        Timber.d("SlideshowManager: Cleaned up")
    }
}
