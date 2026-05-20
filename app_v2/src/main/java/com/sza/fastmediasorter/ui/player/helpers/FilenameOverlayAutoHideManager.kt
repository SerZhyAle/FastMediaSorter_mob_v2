package com.sza.fastmediasorter.ui.player.helpers

import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.core.view.isVisible
import com.sza.fastmediasorter.domain.model.MediaType
import timber.log.Timber

/**
 * Manages automatic hide/show timing for the top-left filename overlay (tvFileNameOverlay).
 *
 * Owns the timer, alpha animation, and re-show logic so that no other class needs to
 * directly manipulate tvFileNameOverlay visibility outside of command-panel mode transitions.
 *
 * Behavioral rules:
 * - TXT  → 5 s auto-hide
 * - PDF / EPUB → 10 s auto-hide
 * - VIDEO / IMAGE / GIF / AUDIO → 15 s auto-hide
 * - File switch always resets the timer from scratch.
 * - Pause (video/audio, non-fullscreen): re-show if hidden, extend if still visible.
 * - Zoom (image/GIF, non-fullscreen): same as pause.
 * - Fullscreen check is delegated to the [isFullscreen] lambda.
 * - [onHostPause] / [onHostResume] preserve remaining time across lifecycle transitions.
 */
class FilenameOverlayAutoHideManager(
    private val overlayView: TextView,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    /** Returns true when the player is in fullscreen (command panel hidden). */
    private val isFullscreen: () -> Boolean
) {
    companion object {
        private const val TIMEOUT_TXT_MS      = 5_000L
        private const val TIMEOUT_DOCUMENT_MS = 10_000L
        private const val TIMEOUT_DEFAULT_MS  = 15_000L
        private const val FADE_DURATION_MS    = 300L
    }

    // When the pending hideRunnable is due (absolute epoch ms). 0 if no pending hide.
    private var deadlineMs: Long = 0L

    // Remaining ms saved across host-pause / lifecycle transitions.
    // -1 means "not paused" / no saved value.
    private var pausedRemainingMs: Long = -1L

    // Current file-type timeout, updated on every onFileShown / pause / zoom call.
    private var currentTimeoutMs: Long = TIMEOUT_DEFAULT_MS

    // Whether we logically think the overlay should be visible.
    // Used to restore correct state when returning from fullscreen.
    private var overlayLogicallyVisible: Boolean = false

    private val hideRunnable = Runnable { animateHide() }

    // ────────────────────────────────────────────────────────────────────
    // Public API
    // ────────────────────────────────────────────────────────────────────

    /**
     * Called every time a new file becomes current. Resets overlay to visible and
     * restarts the type-specific countdown from zero.
     * If currently in fullscreen mode, only updates internal state; the actual show
     * and timer will fire when [onEnterCommandPanelMode] is called.
     */
    fun onFileShown(mediaType: MediaType) {
        currentTimeoutMs = timeoutForType(mediaType)
        pausedRemainingMs = -1L
        mainHandler.removeCallbacks(hideRunnable)

        if (isFullscreen()) {
            // In fullscreen the view is hidden by panel logic. Mark state so that
            // onEnterCommandPanelMode() will show and start the timer on return.
            overlayLogicallyVisible = true
            deadlineMs = 0L  // 0 signals "start fresh when returning"
            Timber.d("FilenameOverlayAutoHideManager: onFileShown (fullscreen) type=$mediaType timeout=${currentTimeoutMs}ms - deferred")
            return
        }

        animateShow()
        scheduleHide(currentTimeoutMs)
        Timber.d("FilenameOverlayAutoHideManager: onFileShown type=$mediaType timeout=${currentTimeoutMs}ms")
    }

    /**
     * Called when the user pauses/unpauses video or audio playback (non-fullscreen only).
     * Re-shows the overlay if already hidden, extends the deadline if still visible.
     */
    fun onPauseInteraction(mediaType: MediaType) {
        // Ignore during fullscreen - overlay is not visible there
        if (isFullscreen()) return
        currentTimeoutMs = timeoutForType(mediaType)
        triggerReShowOrExtend()
        Timber.d("FilenameOverlayAutoHideManager: onPauseInteraction type=$mediaType")
    }

    /**
     * Called when the user pinch-zooms an image or GIF (non-fullscreen only).
     * Only triggers for IMAGE / GIF types; ignored for everything else.
     */
    fun onZoomInteraction(mediaType: MediaType) {
        if (isFullscreen()) return
        // Zoom interaction only applies to image/GIF content
        if (mediaType != MediaType.IMAGE && mediaType != MediaType.GIF) return
        currentTimeoutMs = timeoutForType(mediaType)
        triggerReShowOrExtend()
        Timber.d("FilenameOverlayAutoHideManager: onZoomInteraction type=$mediaType")
    }

    /**
     * Called when the command panel becomes visible (non-fullscreen mode).
     * Restores overlay to the logical visible state preserved before fullscreen,
     * re-arming the timer with remaining time or a fresh countdown.
     */
    fun onEnterCommandPanelMode() {
        if (overlayLogicallyVisible) {
            overlayView.animate().cancel()
            overlayView.alpha = 1f
            overlayView.isVisible = true
            // Re-arm timer: use remaining time if still valid, else start fresh
            val remaining = deadlineMs - System.currentTimeMillis()
            val delay = if (remaining > 0L) remaining else currentTimeoutMs
            scheduleHide(delay)
        }
        // If logically hidden, keep it hidden - nothing to restore.
    }

    /**
     * Called when the player enters fullscreen (command panel hidden).
     * Cancels the pending timer and hides the overlay; logical state is preserved
     * so [onEnterCommandPanelMode] can restore it later.
     */
    fun onEnterFullscreenMode() {
        mainHandler.removeCallbacks(hideRunnable)
        // Don't change overlayLogicallyVisible - preserve for restore on panel-back
        overlayView.animate().cancel()
        overlayView.isVisible = false
        Timber.d("FilenameOverlayAutoHideManager: onEnterFullscreenMode - timer cancelled")
    }

    /**
     * Called from host onPause(). Saves remaining time so it can be resumed on return.
     */
    fun onHostPause() {
        val remaining = deadlineMs - System.currentTimeMillis()
        pausedRemainingMs = if (remaining > 0L) remaining else 0L
        mainHandler.removeCallbacks(hideRunnable)
        Timber.d("FilenameOverlayAutoHideManager: onHostPause savedRemaining=${pausedRemainingMs}ms")
    }

    /**
     * Called from host onResume(). Restores the timer from saved remaining time.
     * If there was no saved time but the overlay is logically visible, starts a fresh timer.
     */
    fun onHostResume(currentType: MediaType?) {
        if (!overlayLogicallyVisible) {
            // Overlay was already hidden before pause - nothing to restore
            pausedRemainingMs = -1L
            return
        }
        val remaining = pausedRemainingMs
        when {
            remaining >= 0L -> {
                scheduleHide(remaining)
                Timber.d("FilenameOverlayAutoHideManager: onHostResume restored ${remaining}ms")
            }
            currentType != null -> {
                val timeout = timeoutForType(currentType)
                scheduleHide(timeout)
                Timber.d("FilenameOverlayAutoHideManager: onHostResume fresh timeout=${timeout}ms")
            }
        }
        pausedRemainingMs = -1L
    }

    /**
     * Cancels any pending timer and immediately hides the overlay.
     * Should be called from onDestroy() to prevent stale runnables.
     */
    fun cancel() {
        mainHandler.removeCallbacks(hideRunnable)
        overlayView.animate().cancel()
        overlayView.alpha = 0f
        overlayView.isVisible = false
        overlayLogicallyVisible = false
        Timber.d("FilenameOverlayAutoHideManager: cancelled")
    }

    // ────────────────────────────────────────────────────────────────────
    // Private helpers
    // ────────────────────────────────────────────────────────────────────

    private fun timeoutForType(type: MediaType): Long = when (type) {
        MediaType.TEXT                 -> TIMEOUT_TXT_MS
        MediaType.PDF, MediaType.EPUB  -> TIMEOUT_DOCUMENT_MS
        else                           -> TIMEOUT_DEFAULT_MS  // VIDEO, IMAGE, GIF, AUDIO
    }

    /**
     * If the overlay is still visible, extend the existing deadline by [currentTimeoutMs].
     * If already hidden, re-show it and start a fresh countdown.
     */
    private fun triggerReShowOrExtend() {
        val isCurrentlyVisible = overlayView.alpha > 0.5f
        if (isCurrentlyVisible) {
            // Extend: add the full timeout to whatever deadline was already set
            val newDeadline = deadlineMs + currentTimeoutMs
            deadlineMs = newDeadline
            mainHandler.removeCallbacks(hideRunnable)
            val remaining = newDeadline - System.currentTimeMillis()
            if (remaining > 0) mainHandler.postDelayed(hideRunnable, remaining)
            Timber.d("FilenameOverlayAutoHideManager: extended deadline by ${currentTimeoutMs}ms remaining=${remaining}ms")
        } else {
            // Re-show and start fresh countdown
            animateShow()
            scheduleHide(currentTimeoutMs)
            Timber.d("FilenameOverlayAutoHideManager: re-showing hidden overlay timeout=${currentTimeoutMs}ms")
        }
    }

    private fun scheduleHide(delayMs: Long) {
        mainHandler.removeCallbacks(hideRunnable)
        deadlineMs = System.currentTimeMillis() + delayMs
        if (delayMs > 0) mainHandler.postDelayed(hideRunnable, delayMs)
        else hideRunnable.run()
    }

    private fun animateShow() {
        overlayLogicallyVisible = true
        overlayView.animate().cancel()
        overlayView.isVisible = true
        overlayView.animate().alpha(1f).setDuration(FADE_DURATION_MS).start()
    }

    private fun animateHide() {
        overlayView.animate().cancel()
        overlayView.animate()
            .alpha(0f)
            .setDuration(FADE_DURATION_MS)
            .withEndAction {
                overlayView.isVisible = false
                overlayLogicallyVisible = false
            }
            .start()
        Timber.d("FilenameOverlayAutoHideManager: hide animation started")
    }
}
