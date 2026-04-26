package com.sza.fastmediasorter.vr.helpers

import android.os.SystemClock
import com.sza.fastmediasorter.ui.player.contracts.PlaybackCommand

/**
 * Suppresses duplicate VR controller commands that arrive within a configurable window.
 *
 * OpenXR reports button-held state on every render frame, so a single physical press
 * can produce dozens of events over its hold duration. The debouncer lets the first
 * event through and silently drops subsequent ones until the window expires.
 *
 * Volume and zoom commands are excluded — they are already rate-limited by
 * [VrControllerInputManager]'s analog-input throttles.
 */
internal class VrCommandDebouncer {

    private val lastDispatchMs = mutableMapOf<String, Long>()

    /**
     * Returns true if [command] from a CONTROLLER source should be dispatched now.
     * Always returns true for non-CONTROLLER sources or commands with no suppression window.
     */
    fun shouldDispatch(command: PlaybackCommand, source: VrCommandSource): Boolean {
        if (source != VrCommandSource.CONTROLLER) return true
        val windowMs = windowFor(command)
        if (windowMs <= 0L) return true
        val key = command::class.java.simpleName
        val now = SystemClock.uptimeMillis()
        val last = lastDispatchMs[key] ?: 0L
        if (now - last < windowMs) return false
        lastDispatchMs[key] = now
        return true
    }

    private fun windowFor(command: PlaybackCommand): Long = when (command) {
        PlaybackCommand.TogglePausePlay,
        PlaybackCommand.Play,
        PlaybackCommand.Pause,
        PlaybackCommand.ToggleImmersiveMode,
        PlaybackCommand.ToggleOverlay,
        PlaybackCommand.OpenControls,
        PlaybackCommand.OpenFileOps,
        PlaybackCommand.ShowCheatsheet,
        PlaybackCommand.Recenter,
        PlaybackCommand.Mute,
        PlaybackCommand.ZoomReset,
        PlaybackCommand.Exit -> 500L

        PlaybackCommand.NextFile,
        PlaybackCommand.PreviousFile,
        PlaybackCommand.SeekForward,
        PlaybackCommand.SeekBackward -> 300L

        is PlaybackCommand.SeekMicro,
        is PlaybackCommand.SeekMacro -> 300L

        // VolumeStep and ZoomStep are rate-limited in VrControllerInputManager — no debounce.
        is PlaybackCommand.VolumeStep,
        is PlaybackCommand.ZoomStep -> 0L

        else -> 0L
    }
}
