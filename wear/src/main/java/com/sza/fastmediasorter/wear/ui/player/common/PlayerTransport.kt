package com.sza.fastmediasorter.wear.ui.player.common

import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.wear.ui.player.helpers.StreamPlaybackSessionManager

/**
 * S2432: play/pause as both watch players do it - a pause always releases the wide channel, and a
 * resume is refused while the stream session says the network is not ready for it.
 */
internal fun StreamPlaybackSessionManager.togglePlayPause(player: ExoPlayer) {
    if (player.isPlaying) {
        player.pause()
        stop()
    } else if (canStartCurrentStream()) {
        player.play()
    }
}

/**
 * S3217: re-prepares the open stream rather than seeking. A paused player leaves its lag inside the
 * open connection - the watch buffer, the socket and the phone's per-connection pipe - and a progressive
 * HTTP broadcast has no live window to seek within, so only a new connection starts at the live edge.
 * A resume the stream session refuses is refused here too, as in [togglePlayPause].
 */
internal fun StreamPlaybackSessionManager.jumpToLive(player: ExoPlayer) {
    val item = player.currentMediaItem ?: return
    if (!canStartCurrentStream()) return
    player.stop()
    player.setMediaItem(item)
    player.prepare()
    player.play()
}

/**
 * S0902: called from the screen's onStop lifecycle effect - without this, playback keeps running while
 * the host activity is stopped (screen off / app backgrounded); onCleared was the only prior teardown
 * edge.
 */
internal fun StreamPlaybackSessionManager.pauseForHostStop(player: ExoPlayer) {
    player.pause()
    stop()
}
