package com.sza.fastmediasorter.ui.player.helpers

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlaybackException
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.VideoPlaybackFailureSessionCache
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import timber.log.Timber

/**
 * Extracted from VideoPlayerManager.kt as part of S0274 Wave 01 decomposition.
 *
 * Owns the full error-classification ladder of
 * [androidx.media3.common.Player.Listener.onPlayerError]:
 *  - thread-interrupt suppression (ExoPlayer shutdown false positives),
 *  - SFTP IO error suppression while the player is still retrying (S0113),
 *  - EOF retry scheduling (up to [MAX_EOF_RETRIES] attempts),
 *  - MediaCodec cooldown marking (S0213 Pillar A),
 *  - audio-renderer Variant B fallback (disable audio, resume video),
 *  - ExoPlayer → MediaPlayer format-error fallback for local files,
 *  - BD-TS (`.m2ts` / `.m2t`) and DVD_PS_VOB (`.vob`) network-container routing,
 *  - [PlaybackException.ERROR_CODE_TIMEOUT] user-message branch.
 *
 * Returns `true` when the error is fully handled inside this helper - caller skips propagation.
 * Returns `false` when the caller must forward to
 * [VideoPlayerManager.PlayerCallback.onPlaybackError] (after `onBuffering(false)`).
 */
internal class VideoPlayerErrorHandler(
    private val manager: VideoPlayerManager,
) {

    companion object {
        private const val MAX_EOF_RETRIES = 3
        private const val EOF_RETRY_DELAY_MS = 1_000L
    }

    /**
     * Classifies and possibly handles an ExoPlayer error.
     *
     * @return `true` if the error was fully consumed (early-return path);
     *         `false` if the caller must propagate via
     *         `onBuffering(false)` + `onPlaybackError(error)`.
     */
    fun handlePlayerError(error: PlaybackException): Boolean {
        val isThreadInterrupted = generateSequence<Throwable>(error) { it.cause }
            .any { it is InterruptedException }
        if (isThreadInterrupted) {
            Timber.d("VideoPlayerManager: ignoring playback error caused by thread interrupt (ExoPlayer shutdown)")
            return true
        }

        // Suppress recoverable SFTP IO errors while ExoPlayer is still in retry state (S0113)
        val isSftpIoError = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED &&
            generateSequence<Throwable>(error) { it.cause }
                .any { it is java.io.IOException && it.message?.contains("SFTP", ignoreCase = true) == true }
        if (isSftpIoError && manager.exoPlayer?.playbackState != Player.STATE_IDLE) {
            Timber.w("VideoPlayerManager: suppressing SFTP IO error toast - player not idle (will retry)")
            return true
        }

        val isEOFException = error.cause is java.io.EOFException ||
            error.cause?.cause is java.io.EOFException
        val isMediaCodecError = error.errorCode >= 4000 && error.errorCode < 5000

        if (isEOFException && manager.playbackRetryCount < MAX_EOF_RETRIES && !manager.playerCallback.isActivityDestroyed()) {
            manager.playbackRetryCount++
            manager.lastPlaybackPosition = manager.exoPlayer?.currentPosition ?: 0L
            Timber.w("VideoPlayerManager: EOFException, retry ${manager.playbackRetryCount}/$MAX_EOF_RETRIES, position=${manager.lastPlaybackPosition}")
            manager.retryRunnable?.let { manager.retryHandler.removeCallbacks(it) }
            manager.retryRunnable = Runnable {
                if (!manager.playerCallback.isActivityDestroyed()) manager.retryPlayback()
            }
            manager.retryHandler.postDelayed(manager.retryRunnable!!, EOF_RETRY_DELAY_MS)
            return true
        } else if (isEOFException) {
            Timber.e("VideoPlayerManager: EOFException - max retries exceeded")
        }

        if (isMediaCodecError) {
            // S0213 Pillar A: arm cooldown so the very next replay of this same source is
            // short-circuited by PlayerMediaLoaderManager before media3 rebuilds its graph.
            manager.currentFilePath?.let(manager.decoderFailureTracker::markFailed)
            Timber.w("VideoPlayerManager: MediaCodec error - errorCode=${error.errorCode}, cause=${error.cause?.javaClass?.simpleName}")
        } else {
            Timber.e(error, "VideoPlayerManager: Playback error - errorCode=${error.errorCode}")
        }

        if (manager.playerCallback.isActivityDestroyed()) return true

        // --- Variant B: graceful audio-decoder fallback ---
        // When the audio renderer fails to initialize (e.g., DTS on Quest 3 - no platform
        // decoder, and FFmpeg extension also unavailable for this specific codec),
        // disable the audio track and resume video playback instead of skipping the file.
        // This prevents jarring auto-navigation away from a perfectly valid video file.
        val isAudioRendererFailure = run {
            val exoEx = error as? ExoPlaybackException ?: return@run false
            if (exoEx.type != ExoPlaybackException.TYPE_RENDERER) return@run false
            val rendererType = try {
                manager.exoPlayer?.getRendererType(exoEx.rendererIndex)
            } catch (_: Exception) {
                null
            }
            rendererType == C.TRACK_TYPE_AUDIO
        }
        if (isAudioRendererFailure) {
            // S0213 Pillar A: 4003 audio-renderer failure is the canonical crash-loop entry -
            // mark the source so retries are throttled even if the user dismisses our toast.
            manager.currentFilePath?.let(manager.decoderFailureTracker::markFailed)
            val savedPosition = manager.exoPlayer?.currentPosition ?: 0L
            Timber.w("VideoPlayerManager: Audio renderer failed (errorCode=${error.errorCode}) - disabling audio, resuming video at ${savedPosition}ms")
            Toast.makeText(
                manager.context,
                manager.context.getString(R.string.warning_audio_format_unsupported),
                Toast.LENGTH_LONG,
            ).show()
            manager.playerCallback.onBuffering(false)
            manager.exoPlayer?.let { player ->
                // Disable all audio tracks so ExoPlayer skips audio renderer on next prepare
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()
                // Re-prepare from IDLE and seek back to the position before the error
                player.prepare()
                player.seekTo(savedPosition)
                player.play()
            }
            return true
        }
        // --- end Variant B ---

        val isFormatError = error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED

        // MediaPlayer cannot handle network protocols (smb://, sftp://, ftp://, etc.)
        // setDataSource() with such URIs fails immediately (what=1, extra=Integer.MIN_VALUE).
        // Only attempt fallback for local file:// paths.
        val isLocalPath = manager.currentFilePath?.let { p ->
            !p.startsWith("smb://") && !p.startsWith("sftp://") &&
                !p.startsWith("ftp://") && !p.startsWith("ftps://") &&
                !p.startsWith("http://") && !p.startsWith("https://") &&
                !p.startsWith("gdrive://") && !p.startsWith("onedrive://") &&
                !p.startsWith("dropbox://")
        } ?: false

        if (isFormatError && manager.currentFilePath != null && !manager.isUsingMediaPlayer && isLocalPath) {
            Timber.i("VideoPlayerManager: ExoPlayer format error, trying MediaPlayer fallback..")
            Handler(Looper.getMainLooper()).post { manager.playWithMediaPlayer(manager.currentFilePath!!) }
            return true
        }
        if (isFormatError && manager.currentFilePath != null && !isLocalPath) {
            Timber.w("VideoPlayerManager: ExoPlayer format error on network path - MediaPlayer fallback skipped")
            val lowerPath = manager.currentFilePath!!.lowercase()
            if (lowerPath.endsWith(".m2ts") || lowerPath.endsWith(".m2t")) {
                Timber.i("VideoPlayerManager: BD-TS format error - showing informative dialog")
                manager.playerCallback.onBdTsFormatError()
                return true
            }
            if (lowerPath.endsWith(".vob")) {
                Timber.i("VideoPlayerManager: DVD_PS_VOB route error - stopping cascade on current file")
                manager.playerCallback.onNetworkContainerRouteError(
                    manager.currentFilePath!!,
                    NetworkPlaybackContainerHint.DVD_PS_VOB,
                )
                return true
            }
        }

        if (error.errorCode == PlaybackException.ERROR_CODE_TIMEOUT) {
            manager.currentFilePath?.let(VideoPlaybackFailureSessionCache::markFailed)
            val userMessage = manager.currentFilePath
                ?.substringAfterLast('/')
                ?.takeIf { it.isNotBlank() }
                ?.let { manager.context.getString(R.string.video_playback_failed_with_name, it) }
                ?: manager.context.getString(R.string.error_loading_media)
            manager.playerCallback.onBuffering(false)
            manager.playerCallback.onPlaybackError(error, userMessage)
            return true
        }

        return false
    }
}
