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

        // Honest log levels for recoverable / gracefully-handled error classes. Each of these is
        // resolved further down (mark + clean advance, or recover in place) - the same quality the
        // decoder-failure path already achieves. Logging them at ERROR would misrepresent severity
        // and mirror them to the on-screen debug error notification (S0341) as if playback broke.
        val isBufferingHang = error.errorCode == PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK
        val isNetworkTimeout = error.errorCode == PlaybackException.ERROR_CODE_TIMEOUT
        val isSeekIndexFailureClass = error.errorCode == PlaybackException.ERROR_CODE_UNSPECIFIED &&
            generateSequence<Throwable>(error) { it.cause }.any { it is ArrayIndexOutOfBoundsException }

        if (isMediaCodecError) {
            // S0213 Pillar A: arm cooldown so the very next replay of this same source is
            // short-circuited by PlayerMediaLoaderManager before media3 rebuilds its graph.
            manager.currentFilePath?.let(manager.decoderFailureTracker::markFailed)
            Timber.w("VideoPlayerManager: MediaCodec error - errorCode=${error.errorCode}, cause=${error.cause?.javaClass?.simpleName}")
        } else if (isBufferingHang) {
            Timber.d("S0344: SMB streaming buffering hang classified at warn level")
            Timber.w("VideoPlayerManager: buffering hang (stuck buffering, not loading) - errorCode=${error.errorCode}, will mark file and advance")
        } else if (isNetworkTimeout) {
            Timber.w("VideoPlayerManager: playback timeout - errorCode=${error.errorCode}, will mark file and advance")
        } else if (isSeekIndexFailureClass) {
            Timber.w("VideoPlayerManager: seek index failure - errorCode=${error.errorCode}, will recover playback in place")
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

        // ERROR_CODE_TIMEOUT (network/IO timeout) and ERROR_CODE_FAILED_RUNTIME_CHECK
        // (ExoPlayer watchdog "Playback stuck buffering and not loading" - errorCode=1004,
        // typically a truncated/corrupt local file whose moov/data never loads) both mean the
        // current file cannot start. Treat them alike: mark the file failed for the session and
        // show a named message instead of the generic skip toast.
        if (error.errorCode == PlaybackException.ERROR_CODE_TIMEOUT ||
            error.errorCode == PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK) {
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

        // Seek failure on an otherwise-playable file. AVI files with an empty/absent track index
        // crash the extractor with ArrayIndexOutOfBoundsException when the seek point is computed;
        // media3 surfaces it as ERROR_CODE_UNSPECIFIED (1000, "Unexpected runtime error"). This is
        // not a source/open failure - the file plays, only exact seeking is impossible. Recover
        // playback in place at the last genuine playback position instead of treating it as a file
        // failure and auto-navigating to the next file (which the user perceives as a crash).
        // Guarded by lastGoodPositionMs > 0: if the file never produced a real playback position,
        // the failure is at open time and must propagate normally.
        val isSeekIndexFailure = error.errorCode == PlaybackException.ERROR_CODE_UNSPECIFIED &&
            generateSequence<Throwable>(error) { it.cause }
                .any { it is ArrayIndexOutOfBoundsException }
        if (isSeekIndexFailure && manager.lastGoodPositionMs > 0L && !manager.playerCallback.isActivityDestroyed()) {
            Timber.d("S0342: AVI seek index failure recovered in place")
            val resumePosition = manager.lastGoodPositionMs
            Timber.w("VideoPlayerManager: seek index failure (empty/absent track index) - resuming at ${resumePosition}ms instead of skipping file")
            manager.playerCallback.onBuffering(false)
            manager.exoPlayer?.let { player ->
                player.prepare()
                player.seekTo(resumePosition)
                player.play()
            }
            return true
        }

        return false
    }
}
