package com.sza.fastmediasorter.wear.ui.player.common

import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.wear.ui.player.helpers.StreamPlaybackSessionManager
import timber.log.Timber

/**
 * S2432: play/pause as both watch players do it - a pause always releases the wide channel, and a
 * resume is refused while the stream session says the network is not ready for it.
 */
internal fun StreamPlaybackSessionManager.togglePlayPause(player: ExoPlayer) {
    Timber.d("S2432: shared toggle play/pause, playing=${player.isPlaying}")
    if (player.isPlaying) {
        player.pause()
        stop()
    } else if (canStartCurrentStream()) {
        player.play()
    }
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
