package com.sza.fastmediasorter.ui.launcher.gadget

import android.widget.FrameLayout
import androidx.core.view.isVisible

/**
 * S2230: owns the video face's control overlay - show on a tap, hide on a repeat tap or after the
 * auto-hide delay, cancel cleanly when the cell tears down.
 *
 * Deliberately player-blind: the manager never touches the ExoPlayer. The buttons report upward
 * through [callbacks], which the view binds to the click listeners, so the release contract of the
 * cell stays in one place and this class remains a pure view-life object.
 *
 * The auto-hide timer is a [Runnable] posted on the overlay view's own handler chain, so it cannot
 * outlive the view's message queue; [cancel] removes it for the detach path, where no more posts
 * should be in flight either.
 */
class StreamWindowOverlayManager(private val root: FrameLayout) {

    /** Clicked-through actions; the view binds each overlay button to one of these. */
    class Callbacks(
        val onPlayPause: () -> Unit,
        val onMute: () -> Unit,
        val onStop: () -> Unit,
        val onPip: () -> Unit,
        val onFullscreen: () -> Unit,
    )

    var callbacks: Callbacks? = null

    private val autoHide = Runnable { hide() }

    fun toggle() {
        if (root.isVisible) hide() else show()
    }

    fun show() {
        root.isVisible = true
        rescheduleHide()
    }

    fun hide() {
        root.removeCallbacks(autoHide)
        root.isVisible = false
    }

    /** Detach path: the timer must not fire into a cell the desktop has already torn down. */
    fun cancel() {
        root.removeCallbacks(autoHide)
    }

    /** Every user interaction with the overlay restarts the window - the player-controls convention. */
    fun rescheduleHide() {
        root.removeCallbacks(autoHide)
        root.postDelayed(autoHide, AUTO_HIDE_DELAY_MS)
    }

    companion object {
        const val AUTO_HIDE_DELAY_MS = 3_000L
    }
}
