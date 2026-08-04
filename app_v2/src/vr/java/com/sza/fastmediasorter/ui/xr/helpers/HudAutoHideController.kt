package com.sza.fastmediasorter.ui.xr.helpers

import android.os.Handler

/**
 * S1232: keeps the immersive HUD strip out of the way without making it unreachable.
 *
 * The strip is shown when playback starts and collapses itself after [TIMEOUT_MS] of no ray
 * interaction; the collapsed state still paints the restore pill (see [HudCanvasRenderer]), so the
 * user never has to aim at an invisible target to get it back. Any hover or click while the strip
 * is open restarts the countdown, mirroring how `FilenameOverlayAutoHideManager` extends its
 * deadline on interaction in the flat player.
 *
 * The timeout matches that manager's `TIMEOUT_DEFAULT_MS` (VIDEO / IMAGE / GIF / AUDIO). The flat
 * player's bottom control panel is NOT a precedent here - it has no auto-hide at all, it toggles
 * only on tap (`PlayerViewModel.toggleControls`).
 */
class HudAutoHideController(
    private val handler: Handler,
    private val onCollapse: () -> Unit
) {

    private var armed = false

    private val collapseRunnable = Runnable {
        armed = false
        onCollapse()
    }

    /** Start (or restart) the countdown. Call when the strip becomes visible. */
    fun arm() {
        cancel()
        armed = true
        handler.postDelayed(collapseRunnable, TIMEOUT_MS)
    }

    /**
     * Push the deadline back. Called on every ray interaction - cheap enough to run per tick
     * because it only reschedules a Handler message, never repaints.
     */
    fun poke() {
        if (!armed) return
        handler.removeCallbacks(collapseRunnable)
        handler.postDelayed(collapseRunnable, TIMEOUT_MS)
    }

    /** Stop the countdown - the strip is already collapsed, or the session is going away. */
    fun cancel() {
        armed = false
        handler.removeCallbacks(collapseRunnable)
    }

    private companion object {
        // Mirrors FilenameOverlayAutoHideManager.TIMEOUT_DEFAULT_MS so the two overlays in a
        // playback session disappear on the same rhythm.
        const val TIMEOUT_MS = 15_000L
    }
}
