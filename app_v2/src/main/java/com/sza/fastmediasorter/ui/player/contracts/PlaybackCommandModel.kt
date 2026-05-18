package com.sza.fastmediasorter.ui.player.contracts

/**
 * Normalized playback command model.
 * VR command set excludes file operations (copy/move/delete) since
 * VR APK inherits the full standard player but limits interactive commands
 * to what makes sense in a headset environment.
 */
sealed class PlaybackCommand {
    data object Play : PlaybackCommand()
    data object Pause : PlaybackCommand()
    data object SeekForward : PlaybackCommand()
    data object SeekBackward : PlaybackCommand()
    data object PreviousFile : PlaybackCommand()
    data object NextFile : PlaybackCommand()
    data object OpenControls : PlaybackCommand()
    data object Exit : PlaybackCommand()
    // S0031 (П3): switch from immersive mode to the VR panel (2D view within the headset)
    // without fully exiting VR. Distinct from Exit which closes VR entirely.
    data object ExitTo2D : PlaybackCommand()
    // File operations - available in standard; VR exposes these through the immersive file-ops panel.
    data object MoveFile : PlaybackCommand()
    data object CopyFile : PlaybackCommand()
    data object DeleteFile : PlaybackCommand()
    // VR-specific commands - immersive controls (keyboard/mouse/OpenXR controllers).
    data object TogglePausePlay : PlaybackCommand()
    data class VolumeStep(val delta: Int) : PlaybackCommand()
    data class ZoomStep(val increase: Boolean) : PlaybackCommand()
    data object ZoomReset : PlaybackCommand()
    data object Recenter : PlaybackCommand()
    data object ToggleImmersiveMode : PlaybackCommand()
    data object ShowCheatsheet : PlaybackCommand()
    data object ToggleOverlay : PlaybackCommand()
    data object OpenFileOps : PlaybackCommand()
    data object RenameFile : PlaybackCommand()
    data class SeekMicro(val forward: Boolean) : PlaybackCommand()
    data class SeekMacro(val forward: Boolean) : PlaybackCommand()
    data object Mute : PlaybackCommand()
    // VR interactive panel commands (spec_vr-immersive-controls-panel).
    data object VolumeUp : PlaybackCommand()
    data object VolumeDown : PlaybackCommand()
    data object BrightnessUp : PlaybackCommand()
    data object BrightnessDown : PlaybackCommand()
    data class SetPlaybackSpeed(val speed: Float) : PlaybackCommand()
    data object CycleAudioTrack : PlaybackCommand()
    data object CycleStereoFormat : PlaybackCommand()
    data class SeekTo(val positionMs: Long) : PlaybackCommand()
}

data class PlaybackCommandSet(
    val available: Set<PlaybackCommand>
) {
    companion object {
        /**
         * VR overlay / immersive command set. Includes file operations
         * because the immersive file-ops panel delegates through the
         * same dispatch pipeline as keyboard and mouse input.
         */
        fun forVrPlayback() = PlaybackCommandSet(
            available = setOf(
                PlaybackCommand.Play,
                PlaybackCommand.Pause,
                PlaybackCommand.SeekForward,
                PlaybackCommand.SeekBackward,
                PlaybackCommand.PreviousFile,
                PlaybackCommand.NextFile,
                PlaybackCommand.OpenControls,
                PlaybackCommand.Exit,
                PlaybackCommand.MoveFile,
                PlaybackCommand.CopyFile,
                PlaybackCommand.DeleteFile,
                PlaybackCommand.TogglePausePlay,
                PlaybackCommand.ZoomReset,
                PlaybackCommand.Recenter,
                PlaybackCommand.ToggleImmersiveMode,
                PlaybackCommand.ShowCheatsheet,
                PlaybackCommand.ToggleOverlay,
                PlaybackCommand.OpenFileOps,
                PlaybackCommand.RenameFile,
                PlaybackCommand.Mute,
                PlaybackCommand.VolumeUp,
                PlaybackCommand.VolumeDown,
                PlaybackCommand.BrightnessUp,
                PlaybackCommand.BrightnessDown,
                PlaybackCommand.CycleAudioTrack,
                PlaybackCommand.CycleStereoFormat,
                PlaybackCommand.ExitTo2D,
            )
        )

        /** Standard player command set - full set including file operations. */
        fun forStandardPlayback() = PlaybackCommandSet(
            available = setOf(
                PlaybackCommand.Play,
                PlaybackCommand.Pause,
                PlaybackCommand.SeekForward,
                PlaybackCommand.SeekBackward,
                PlaybackCommand.PreviousFile,
                PlaybackCommand.NextFile,
                PlaybackCommand.OpenControls,
                PlaybackCommand.Exit,
                PlaybackCommand.MoveFile,
                PlaybackCommand.CopyFile,
                PlaybackCommand.DeleteFile
            )
        )
    }
}
