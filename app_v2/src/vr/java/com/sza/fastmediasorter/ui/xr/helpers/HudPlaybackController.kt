package com.sza.fastmediasorter.ui.xr.helpers

import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import timber.log.Timber

class HudPlaybackController(
    private var exoPlayer: ExoPlayer?,
    private val onNext: () -> Unit,
    private val onPrev: () -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    /** S1239: position and duration read together, so a bar built from them cannot show a mix. */
    data class PlaybackPosition(val positionMs: Long, val durationMs: Long)

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

    /**
     * S1240: the first seek capability the immersive session has ever had. The thumbstick is its
     * first caller; S1239's HUD bar goes through the same [seekResolved] body rather than growing
     * a second path, so a scrub begun on one control and finished on the other cannot disagree
     * about clamping or about what an unset duration means.
     */
    fun seekBy(deltaMs: Long) {
        seekResolved { player, _ -> player.currentPosition + deltaMs }
    }

    /** S1239: absolute seek from the HUD bar, issued once when the handle is released. */
    fun seekToFraction(fraction: Float) {
        seekResolved { _, duration -> (duration * fraction.coerceIn(0f, 1f)).toLong() }
    }

    /**
     * S1239: what the HUD seek bar paints. Main thread only - it reads the player synchronously
     * rather than posting, because a posted read has no way to return a value to the caller that
     * is about to draw.
     *
     * Null in the same cases [seekResolved] refuses to seek: no player, or a live/unbounded source
     * whose duration is unset and therefore has no fraction to place a handle on.
     */
    fun positionOrNull(): PlaybackPosition? {
        val player = exoPlayer ?: return null
        val duration = player.duration
        return if (duration == C.TIME_UNSET || duration <= 0L) {
            null
        } else {
            PlaybackPosition(player.currentPosition.coerceIn(0L, duration), duration)
        }
    }

    /**
     * A live or unbounded source reports an unset duration - those are skipped rather than
     * clamped, because there is no end to clamp against and a negative-length window would send
     * the player somewhere arbitrary.
     */
    private fun seekResolved(resolve: (ExoPlayer, Long) -> Long) {
        mainHandler.post {
            val player = exoPlayer ?: return@post
            val duration = player.duration
            if (duration == C.TIME_UNSET || duration <= 0L) {
                return@post
            }
            player.seekTo(resolve(player, duration).coerceIn(0L, duration))
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
