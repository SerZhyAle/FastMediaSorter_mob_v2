package com.sza.fastmediasorter.ui.player

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.core.constants.AppConstants
import com.sza.fastmediasorter.core.debug.MemoryEnduranceTracker
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
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
    private val slideshowCallback: SlideshowCallback,
    private val statsSink: StatsSink,
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
        /**
         * Check if timer/countdown should be suppressed for current file.
         * Used for mixed resources with playToEndInSlideshow: audio/video files
         * advance via onPlaybackEnded, not via interval timer.
         * Default returns false (no suppression).
         */
        fun shouldSuppressTimer(): Boolean = false
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private val countdownHandler = Handler(Looper.getMainLooper())
    
    private var isActive = false
    private var isPaused = false
    private var intervalMs = AppConstants.DEFAULT_SLIDESHOW_INTERVAL_MS
    private var slideCounter = 0
    // S0120: endurance cycle counter (resets with scenario, independent of slideCounter)
    private var enduranceTransitions = 0

    /**
     * When true, slideshow timer and countdown are suppressed.
     * File advance happens only via external trigger (onPlaybackEnded).
     * Set for AUDIO_LIBRARY / VIDEO_LIBRARY resource profiles.
     */
    var isMediaLibraryMode: Boolean = false
    
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
            statsSink.record(StatsEvent.SlideAdvanced)

            // S0120: checkpoint every transition; CYCLE_END every 25 for delta analysis
            enduranceTransitions++
            MemoryEnduranceTracker.checkpoint("TRANSITION")
            if (enduranceTransitions % 25 == 0) {
                MemoryEnduranceTracker.checkpoint("CYCLE_END")
            }

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

        val wasActive = isActive
        intervalMs = intervalSeconds * 1000L
        isActive = true
        isPaused = false
        enduranceTransitions = 0
        // Count one session only on a real inactive -> active transition, not on interval restarts.
        if (!wasActive) statsSink.record(StatsEvent.SlideshowStarted)
        MemoryEnduranceTracker.startScenario("IMG-slideshow")
        
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
        // S1308: scheduleNextSlide() also posts an untracked showCountdown() lambda. Removing only
        // countdownRunnable left that lambda pending, so it fired after stop/pause and ticked
        // 3..2..1 on a dead slideshow - and since the clearing tick 0 lives in the (already removed)
        // slideShowRunnable, the "1.." badge then stayed on screen.
        countdownHandler.removeCallbacksAndMessages(null)
        
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

        MemoryEnduranceTracker.endScenario()
        isActive = false
        isPaused = false
        handler.removeCallbacks(slideShowRunnable)
        // S1308: scheduleNextSlide() also posts an untracked showCountdown() lambda. Removing only
        // countdownRunnable left that lambda pending, so it fired after stop/pause and ticked
        // 3..2..1 on a dead slideshow - and since the clearing tick 0 lives in the (already removed)
        // slideShowRunnable, the "1.." badge then stayed on screen.
        countdownHandler.removeCallbacksAndMessages(null)
        
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
            // Reschedule with new interval. S1308: drop the pending countdown lambda too, otherwise
            // the old interval's countdown keeps ticking alongside the new schedule.
            handler.removeCallbacks(slideShowRunnable)
            countdownHandler.removeCallbacksAndMessages(null)
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
            countdownHandler.removeCallbacksAndMessages(null)
            scheduleNextSlide()
        }
    }
    
    private fun scheduleNextSlide() {
        // Media library mode: no timer/countdown - advance via onPlaybackEnded only
        if (isMediaLibraryMode) {
            Timber.d("SlideshowController: scheduleNextSlide skipped (mediaLibraryMode)")
            return
        }

        // Mixed resource playToEnd mode: suppress timer for audio/video files
        if (slideshowCallback.shouldSuppressTimer()) {
            Timber.d("SlideshowController: scheduleNextSlide skipped (shouldSuppressTimer)")
            return
        }

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
        // Keep slideshow state but stop timers (null removes ALL pending messages incl. anonymous lambdas)
        handler.removeCallbacksAndMessages(null)
        countdownHandler.removeCallbacksAndMessages(null)
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
        // S0120: end scenario on destroy if stopSlideshow() was not called explicitly
        if (isActive) MemoryEnduranceTracker.endScenario()
        handler.removeCallbacksAndMessages(null)
        countdownHandler.removeCallbacksAndMessages(null)
        lifecycle.removeObserver(this)
        // D.8: Ensure screen-on flag is restored on destroy
        slideshowCallback.setKeepScreenAwake(false)
        // Reset state to prevent further operations
        isActive = false
        isPaused = false
        Timber.d("SlideshowController: Cleaned up")
    }
}
