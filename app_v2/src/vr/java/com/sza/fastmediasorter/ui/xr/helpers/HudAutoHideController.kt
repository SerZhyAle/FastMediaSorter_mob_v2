package com.sza.fastmediasorter.ui.xr.helpers

import android.os.Handler

/**
 * S1232: an idle countdown for the immersive HUD strip. **Nothing constructs this class - it is
 * staged, not live**, and S1281 corrected this KDoc after it was found describing behaviour the
 * app no longer has.
 *
 * What it would do if wired: arm when the strip becomes visible, and fire [onCollapse] after
 * [TIMEOUT_MS] of no ray interaction, with any hover or click pushing the deadline back - the way
 * `FilenameOverlayAutoHideManager` extends its own deadline in the flat player. The timeout matches
 * that manager's `TIMEOUT_DEFAULT_MS`. The flat player's bottom control panel is NOT a precedent
 * here: it has no auto-hide at all and toggles only on tap (`PlayerViewModel.toggleControls`).
 *
 * What is no longer true: there is no collapsed state and no restore pill. S1232 deleted the pill -
 * the owner rejected it as an obstruction in the middle of the view - and replaced it with a HIDE
 * button plus a controller-button summon, so hidden now means fully hidden and the way back is a
 * button press rather than a target to aim at. [onCollapse] therefore has no defined meaning until
 * a caller decides what "hidden" is for it.
 *
 * Why it survives unwired: automatic disappearance is an explicit non-goal of S1232, which kept
 * this as the shape a later answer would take if the headset verdict ever asked for the strip to
 * get out of the way by itself. S1281 owns that decision - wire it or delete it.
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
