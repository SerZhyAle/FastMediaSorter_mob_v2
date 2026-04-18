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
    // File operations — available in standard; excluded from VR overlay
    data object MoveFile : PlaybackCommand()
    data object CopyFile : PlaybackCommand()
    data object DeleteFile : PlaybackCommand()
}

data class PlaybackCommandSet(
    val available: Set<PlaybackCommand>
) {
    companion object {
        /** VR overlay command set — playback controls only, no file operations. */
        fun forVrPlayback() = PlaybackCommandSet(
            available = setOf(
                PlaybackCommand.Play,
                PlaybackCommand.Pause,
                PlaybackCommand.SeekForward,
                PlaybackCommand.SeekBackward,
                PlaybackCommand.PreviousFile,
                PlaybackCommand.NextFile,
                PlaybackCommand.OpenControls,
                PlaybackCommand.Exit
            )
        )

        /** Standard player command set — full set including file operations. */
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
