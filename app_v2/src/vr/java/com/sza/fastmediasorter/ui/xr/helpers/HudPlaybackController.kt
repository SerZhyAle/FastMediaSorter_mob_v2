package com.sza.fastmediasorter.ui.xr.helpers

import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import timber.log.Timber

class HudPlaybackController(
    private var exoPlayer: ExoPlayer?,
    private val onNext: () -> Unit,
    private val onPrev: () -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun updatePlayer(player: ExoPlayer?) {
        exoPlayer = player
    }

    fun setVolume(volume: Float) {
        // Explicitly marshal to UI main thread to avoid silent ExoPlayer threading crashes (Player.setVolume requires main-thread)
        mainHandler.post {
            exoPlayer?.let { player ->
                player.volume = volume
            }
        }
    }

    fun play() {
        mainHandler.post {
            exoPlayer?.play()
        }
    }

    fun pause() {
        mainHandler.post {
            exoPlayer?.pause()
        }
    }

    fun next() {
        mainHandler.post {
            onNext()
        }
    }

    fun prev() {
        mainHandler.post {
            onPrev()
        }
    }
}
