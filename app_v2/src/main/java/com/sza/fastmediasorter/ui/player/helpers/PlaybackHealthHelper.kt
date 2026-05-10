package com.sza.fastmediasorter.ui.player.helpers

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.media3.common.Player
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import timber.log.Timber

/**
 * Playback-health monitor and MediaPlayer fallback.
 *
 * Extension functions on [VideoPlayerManager] — extracted to a separate file to reduce
 * per-file CFG complexity for the Kotlin compiler (avoids GC overhead during parallel
 * flavor compilation of the original 1 700-line VideoPlayerManager.kt).
 *
 * The health check detects "white noise" / stuck-playback scenarios for audio files
 * (FLAC, AC3, EAC3, WavPack) where ExoPlayer reports STATE_READY but position never advances.
 * After two consecutive stuck polls it falls back to Android's MediaPlayer.
 */

// ═══════════════════════════════════════════════════════════════════════════
// Health monitoring lifecycle
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Start monitoring position progress after STATE_READY.
 * Only active for audio formats known to exhibit white-noise playback issues.
 */
internal fun VideoPlayerManager.startPlaybackHealthCheck() {
    cancelPlaybackHealthCheck()

    // Only relevant for audio files prone to silent-decode failures
    val isAudioFile = currentFilePath?.let { path ->
        path.endsWith(".flac", ignoreCase = true) ||
            path.endsWith(".ac3", ignoreCase = true) ||
            path.endsWith(".eac3", ignoreCase = true) ||
            path.endsWith(".wv", ignoreCase = true)
    } ?: false

    if (!isAudioFile || isUsingMediaPlayer) return

    lastCheckedPosition = exoPlayer?.currentPosition ?: 0L

    playbackHealthCheckRunnable = Runnable { checkPlaybackHealth() }
    retryHandler.postDelayed(playbackHealthCheckRunnable!!, VideoPlayerManager.PLAYBACK_HEALTH_CHECK_DELAY_MS)
    Timber.d("VideoPlayerManager: Started playback health monitoring for audio file")
}

/** Check whether position is actually advancing; trigger MediaPlayer fallback if stuck. */
internal fun VideoPlayerManager.checkPlaybackHealth() {
    val player = exoPlayer ?: return

    if (!player.isPlaying || player.playbackState != Player.STATE_READY) {
        Timber.d("VideoPlayerManager: Health check skipped - player not playing or not ready")
        return
    }

    val currentPosition = player.currentPosition
    val positionDelta = currentPosition - lastCheckedPosition

    Timber.d("VideoPlayerManager: Health check - position delta: ${positionDelta}ms " +
        "(from ${lastCheckedPosition}ms to ${currentPosition}ms)")

    // < 500 ms progress in PLAYBACK_HEALTH_CHECK_DELAY_MS window = stuck
    if (positionDelta < 500) {
        playbackStuckCount++
        Timber.w("VideoPlayerManager: Playback appears stuck! Stuck count: " +
            "$playbackStuckCount/${VideoPlayerManager.MAX_PLAYBACK_STUCK_COUNT}")

        if (playbackStuckCount >= VideoPlayerManager.MAX_PLAYBACK_STUCK_COUNT && currentFilePath != null) {
            Timber.e("VideoPlayerManager: Playback confirmed stuck (white noise), falling back to MediaPlayer")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    context.getString(R.string.player_health_switching_to_fallback),
                    Toast.LENGTH_SHORT
                ).show()
                playWithMediaPlayer(currentFilePath!!)
            }
            return
        }

        // Schedule another poll
        lastCheckedPosition = currentPosition
        retryHandler.postDelayed(playbackHealthCheckRunnable!!, VideoPlayerManager.PLAYBACK_HEALTH_CHECK_DELAY_MS)
    } else {
        // Playback is healthy — stop polling
        playbackStuckCount = 0
        Timber.d("VideoPlayerManager: Playback is healthy")
        cancelPlaybackHealthCheck()
    }
}

/** Cancel the health-monitoring runnable and reset stuck counter. */
internal fun VideoPlayerManager.cancelPlaybackHealthCheck() {
    playbackHealthCheckRunnable?.let {
        retryHandler.removeCallbacks(it)
        playbackHealthCheckRunnable = null
        playbackStuckCount = 0
        Timber.d("VideoPlayerManager: Cancelled playback health monitoring")
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// MediaPlayer fallback
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Fall back to Android [MediaPlayer] when ExoPlayer cannot decode the format.
 * Only used for LOCAL files — MediaPlayer cannot handle smb://, sftp://, ftp://, cloud://.
 */
internal fun VideoPlayerManager.playWithMediaPlayer(path: String) {
    try {
        cancelPlaybackHealthCheck()
        isUsingMediaPlayer = true
        releaseMediaPlayer()

        mediaPlayer = MediaPlayer().apply {
            setDataSource(path)
            setOnPreparedListener {
                playerCallback.onPlaybackReady()
                playerCallback.onBuffering(false)
                start()
            }
            setOnCompletionListener {
                playerCallback.onPlaybackEnded()
            }
            setOnErrorListener { _, what, extra ->
                Timber.e("MediaPlayer error: what=$what, extra=$extra, file=$path")

                // MediaPlayer error codes (what):
                // MEDIA_ERROR_UNKNOWN = 1, MEDIA_ERROR_SERVER_DIED = 100
                // Extra codes: IO=-1004, MALFORMED=-1007, UNSUPPORTED=-1010, TIMED_OUT=-110

                val errorTypeText = when (what) {
                    1 -> context.getString(R.string.player_health_error_type_unknown)
                    100 -> context.getString(R.string.player_health_error_type_server_died)
                    else -> context.getString(R.string.player_health_error_type_code, what)
                }
                val extraBlock = when (extra) {
                    -1004 -> context.getString(R.string.player_health_io_block)
                    -1007 -> context.getString(R.string.player_health_malformed_block)
                    -1010 -> context.getString(R.string.player_health_unsupported_block)
                    -110 -> context.getString(R.string.player_health_timeout_block)
                    else -> context.getString(R.string.player_health_default_block, extra)
                }
                val errorMessage = buildString {
                    append(context.getString(R.string.player_health_dialog_header))
                    append("\n\n")
                    append(errorTypeText)
                    append("\n\n")
                    append(extraBlock)
                    append("\n\n")
                    append(context.getString(R.string.player_health_file_label, path.substringAfterLast('/')))
                }

                playerCallback.showError(errorMessage)
                true
            }
            prepareAsync()
        }

        playerCallback.onBuffering(true)
        Timber.i("VideoPlayerManager: Using MediaPlayer fallback for: $path")
    } catch (e: Exception) {
        Timber.e(e, "VideoPlayerManager: MediaPlayer fallback failed")
        playerCallback.showError(
            context.getString(R.string.player_health_fallback_failed, context.getString(R.string.error_reason_unknown))
        )
        isUsingMediaPlayer = false
    }
}

/** Release [MediaPlayer] resources and reset [VideoPlayerManager.isUsingMediaPlayer] flag. */
internal fun VideoPlayerManager.releaseMediaPlayer() {
    mediaPlayer?.apply {
        try {
            if (isPlaying) stop()
            release()
        } catch (e: Exception) {
            Timber.e(e, "Error releasing MediaPlayer")
        }
    }
    mediaPlayer = null
    isUsingMediaPlayer = false
}
