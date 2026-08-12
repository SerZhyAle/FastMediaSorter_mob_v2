package com.sza.fastmediasorter.ui.launcher.gadget.nowplaying

import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.ui.player.AudioPlaybackService
import com.sza.fastmediasorter.widget.AudioNowPlayingSnapshotStore

/**
 * S0429: this app's own playback, read the way the home-screen widget reads it.
 *
 * Not a temporary shim - this is the permanent fallback the opt-in design leans on, shown whenever
 * notification access is absent or revoked.
 */
class OwnSessionNowPlayingSource(private val context: Context) : NowPlayingSource {

    override fun read(): NowPlayingState {
        val snapshot = AudioNowPlayingSnapshotStore.read(context)
        return NowPlayingState(
            active = snapshot.active,
            title = snapshot.title,
            artist = snapshot.artist,
            isPlaying = snapshot.isPlaying,
            canControl = snapshot.active,
        )
    }

    override fun send(command: NowPlayingCommand): Boolean {
        val intent = Intent(context, AudioPlaybackService::class.java).apply {
            action = AudioPlaybackService.ACTION_WIDGET_COMMAND
            putExtra(AudioPlaybackService.EXTRA_WIDGET_COMMAND, command.toServiceCommand())
        }
        // Never startForegroundService: this only ever reaches a service the user already started by
        // playing something, and a background start of a stopped service would throw on API 26+.
        return runCatching { context.startService(intent) }.isSuccess
    }

    private fun NowPlayingCommand.toServiceCommand(): String = when (this) {
        NowPlayingCommand.PREVIOUS -> AudioPlaybackService.WIDGET_COMMAND_PREVIOUS
        NowPlayingCommand.PLAY_PAUSE -> AudioPlaybackService.WIDGET_COMMAND_PLAY_PAUSE
        NowPlayingCommand.NEXT -> AudioPlaybackService.WIDGET_COMMAND_NEXT
    }
}
