package com.sza.fastmediasorter.vr.ui

import com.sza.fastmediasorter.ui.player.contracts.PlaybackCommand
import com.sza.fastmediasorter.ui.player.contracts.PlaybackCommandSet
import timber.log.Timber

/**
 * Manages the VR control overlay shown in the headset.
 *
 * Commands available: Play/Pause, Seek ±15s, Previous/Next, Exit.
 * File operations (copy/move/delete) are excluded per PlaybackCommandSet.forVrPlayback().
 *
 * Input model: controller ray-cast via OpenXR action set.
 * (Hand tracking and gaze input are out of scope for this phase.)
 *
 * The overlay appears when the user presses Menu/Back on the controller
 * and auto-hides after [AUTO_HIDE_DELAY_MS] milliseconds.
 */
class VrControlOverlayManager(
    private val commandSet: PlaybackCommandSet = PlaybackCommandSet.forVrPlayback(),
    private val onCommand: (PlaybackCommand) -> Unit
) {

    private var isVisible = false

    /**
     * Show the overlay. Auto-hides after [AUTO_HIDE_DELAY_MS].
     */
    fun show() {
        Timber.d("VrControlOverlayManager: show overlay")
        isVisible = true
        // TODO: Render overlay quad in XR space,
        //       bind controller ray-cast hit-test to available commands,
        //       schedule auto-hide via Handler/coroutine
    }

    /**
     * Hide the overlay immediately.
     */
    fun hide() {
        Timber.d("VrControlOverlayManager: hide overlay")
        isVisible = false
        // TODO: Remove overlay from XR scene
    }

    /**
     * Toggle overlay visibility — bound to the Menu/Back button on the controller.
     */
    fun toggle() {
        if (isVisible) hide() else show()
    }

    /**
     * Dispatch a command from the overlay.
     * Only commands in [commandSet.available] are processed.
     */
    fun dispatchCommand(command: PlaybackCommand) {
        if (command !in commandSet.available) {
            Timber.w("VrControlOverlayManager: command $command not in available set, ignoring")
            return
        }
        Timber.d("VrControlOverlayManager: dispatching $command")
        onCommand(command)
    }

    fun isOverlayVisible(): Boolean = isVisible

    companion object {
        /** Overlay auto-hides after 5 seconds of inactivity. */
        const val AUTO_HIDE_DELAY_MS = 5_000L
    }
}
