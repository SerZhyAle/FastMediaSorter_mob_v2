package com.sza.fastmediasorter.core.playback

import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.ui.player.AudioPlaybackService

/**
 * Ends the background audio service for a caller below the screen layer - a remote command, an exit - so that
 * caller never names the service class. The service banks the playback position in its own teardown.
 */
object BackgroundAudioStopper {

    fun stopIfRunning(context: Context) {
        if (AudioPlaybackService.isRunning) context.stopService(Intent(context, AudioPlaybackService::class.java))
    }
}
