package com.sza.fastmediasorter.ui.xr.helpers

import android.os.Handler

/**
 * S1232 / S1281: the idle countdown for the immersive HUD strip, live since S1281 wired it into
 * `DiagnosticXrActivity`. It arms when the strip becomes visible and fires [onCollapse] after
 * [TIMEOUT_MS] of no ray interaction; any hover or click pushes the deadline back, the way
 * `FilenameOverlayAutoHideManager` extends its own deadline in the flat player. The timeout matches
 * that manager's `TIMEOUT_DEFAULT_MS`. The flat player's bottom control panel is NOT a precedent
 * here: it has no auto-hide at all and toggles only on tap (`PlayerViewModel.toggleControls`).
 *
 * [onCollapse] means exactly what the HIDE button means - `DiagnosticXrActivity.hideHudStrip`, the
 * single hide path both share. There is no collapsed state and no restore pill: S1232 deleted the
 * pill as an obstruction in the middle of the view, so hidden is fully hidden and the controller
 * summon is the only way back. A second flavour of hidden would need a second way back, which is
 * why the countdown reuses the button's action instead of introducing one.
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
