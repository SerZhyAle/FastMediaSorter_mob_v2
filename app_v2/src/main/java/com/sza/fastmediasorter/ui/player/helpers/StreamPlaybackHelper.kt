package com.sza.fastmediasorter.ui.player.helpers

import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Internet-stream playback: progressive http(s) audio/video, HLS/DASH VOD (auto-detected where the
 * Media3 modules are on the classpath), and RTSP (RTP-over-RTSP/TCP) behind the flavor-gated
 * [VideoPlayerManager.streamProtocolSupport].
 *
 * Extension function on [VideoPlayerManager] - kept out of the orchestrator file to stay clear of
 * the per-file CFG/LOC ceiling, mirroring the other `play*Video` helpers.
 *
 * For live HLS/DASH the buffer is kept modest and the player carries a [MediaItem.LiveConfiguration]
 * so it tracks the live edge: an oversized backlog only pins the playhead next to the expiring segment
 * and provokes more `BehindLiveWindow` drops. Recoverable failures - a routine live-edge desync
 * (`BehindLiveWindow`) or a transient network drop - are healed silently by re-anchoring to the live
 * edge and re-preparing within a bounded retry budget, instead of surfacing the "channel unavailable"
 * dialog. RTSP on a flavor without the module surfaces an explicit message instead of silently failing.
 */
@UnstableApi
internal suspend fun VideoPlayerManager.playStreamVideo(path: String, playWhenReady: Boolean = true) {
    releasePlayer()

    val isRtsp = path.startsWith("rtsp://")
    val uri: Uri = Uri.parse(path)
    val dataSourceFactory = StreamDataSourceFactoryProvider.create(context)

    // RTSP lives behind the flavor-gated interface; null means this build (lite/photos) lacks the module.
    val rtspSource = if (isRtsp) streamProtocolSupport.createRtspMediaSource(uri, dataSourceFactory) else null
    if (isRtsp && rtspSource == null) {
        Timber.i("VideoPlayerManager.playStreamVideo: RTSP unsupported in this build - path=%s", path)
        playerCallback.showError(context.getString(R.string.streams_error_unsupported_in_build))
        return
    }

    // S0688: bandwidth-adaptive buffer. A shared BandwidthMeter feeds both the player (so the http data
    // source measures live throughput) and the load control, which widens the steady cushion on a weak
    // link and tightens it back on a healthy one. Live content stays clamped to the proven S0685 live-safe
    // depth so live-edge tracking (S0634) cannot regress; only non-live streams get the deepening. The
    // start/post-rebuffer thresholds keep the S0685 values. Radio plays on a separate audio player and
    // never reaches this control, so no radio-vs-live split applies here (S0689 archived as obsolete).
    val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
    val loadControl = BandwidthAdaptiveLoadControl.create(bandwidthMeter)
    Timber.d("S0688: stream load control bandwidth-adaptive (rtsp=%s) path=%s", isRtsp, path)

    val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    val builder = ExoPlayer.Builder(context)
        .setLoadControl(loadControl)
        .setBandwidthMeter(bandwidthMeter)
        .setAudioAttributes(audioAttributes, true)
    if (!isRtsp) {
        // http(s): let the core auto-detect progressive / HLS / DASH (segmented resolves only where the modules exist).
        builder.setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory))
    }

    val player = builder.build()
    exoPlayer = player
    player.addListener(streamPlaybackListener(path))
    currentPlayerView?.player = player

    if (isRtsp) {
        player.setMediaSource(rtspSource!!)
    } else {
        // LiveConfiguration is honoured only when the content is actually live (ignored for VOD/radio),
        // so it is safe to attach unconditionally on the auto-detected http(s) branch. Targeting ~10s
        // off the live edge with a small catch-up speed (not pinned to 1.0) lets the player ride the
        // sliding window without sticking to its expiring tail.
        val liveConfiguration = MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(10_000)
            .setMinOffsetMs(4_000)
            .setMaxOffsetMs(20_000)
            .setMaxPlaybackSpeed(1.02f)
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setLiveConfiguration(liveConfiguration)
            .build()
        player.setMediaItem(mediaItem)
    }
    player.prepare()
    player.playWhenReady = playWhenReady
    Timber.i("VideoPlayerManager.playStreamVideo: prepared %s (rtsp=%s)", path, isRtsp)
}

/**
 * Lean listener for the stream player: forwards buffering/ready to the UI, reads ICY radio metadata,
 * and heals recoverable failures (live-edge desync + transient network drops) by re-anchoring to the
 * live edge and re-preparing within a bounded budget before the error surfaces. Deliberately does NOT
 * reuse the full video listener (poster extraction, watch clock, decoder-failure tracking) - none of it
 * applies to a live stream and the file-poster path is wrong for a network URL.
 */
@UnstableApi
private fun VideoPlayerManager.streamPlaybackListener(path: String): Player.Listener =
    object : Player.Listener {
        private val isRtsp = path.startsWith("rtsp://")
        private var behindLiveRecoveries = 0
        private var transientRetries = 0
        // S0685: true while a recovery path (re-anchor / classified retry) is re-establishing the stream,
        // so the next BUFFERING is labelled "reconnecting" instead of a plain buffer fill. Set by the
        // recovery branches in onPlayerError, cleared on a confirmed READY and on hard-fail.
        private var reconnecting = false

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playerCallback.isActivityDestroyed()) return
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    playerCallback.onBuffering(true)
                    playerCallback.onStreamWaitPhase(
                        if (reconnecting) VideoPlayerManager.StreamWaitPhase.RECONNECTING
                        else VideoPlayerManager.StreamWaitPhase.BUFFERING
                    )
                }
                Player.STATE_READY -> {
                    // Reset the recovery budgets only on a confirmed READY, never on BUFFERING: a stream
                    // that flaps buffering<->error must not silently refill its quota and spin forever.
                    behindLiveRecoveries = 0
                    transientRetries = 0
                    reconnecting = false
                    playerCallback.onBuffering(false)
                    playerCallback.onStreamWaitPhase(null)
                    playerCallback.onPlaybackReady()
                }
                Player.STATE_ENDED -> playerCallback.onPlaybackEnded()
                Player.STATE_IDLE -> Unit
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playerCallback.onPlaybackStateChanged(isPlaying)
        }

        override fun onMetadata(metadata: Metadata) {
            for (i in 0 until metadata.length()) {
                when (val entry = metadata.get(i)) {
                    is IcyHeaders ->
                        Timber.i("Stream ICY headers: name=%s genre=%s bitrate=%d", entry.name, entry.genre, entry.bitrate)
                    is IcyInfo -> {
                        val nowPlaying = entry.title?.takeIf { it.isNotBlank() } ?: continue
                        Timber.i("Stream ICY now-playing: %s", nowPlaying)
                        updateNowPlayingTitle(nowPlaying)
                    }
                    else -> Unit
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // P0 - routine live-edge desync: the manifest's sliding window moved past the playhead. This
            // is NOT a dead channel. A bare prepare() re-prepares at the same expired position and fails
            // again, so seek to the live edge first, then prepare. RTSP has no sliding window, so the seek
            // is gated off it. Budget bounds a genuinely dead window; backoff is linear and short.
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && !isRtsp) {
                if (behindLiveRecoveries < STREAM_MAX_BEHIND_LIVE_RECOVERIES) {
                    behindLiveRecoveries++
                    // S0685: flip the wait label to "reconnecting" up front so the user sees the recovery
                    // even before the player re-enters BUFFERING during the re-prepare.
                    reconnecting = true
                    playerCallback.onStreamWaitPhase(VideoPlayerManager.StreamWaitPhase.RECONNECTING)
                    val backoffMs = (behindLiveRecoveries * 1_000L).coerceAtMost(5_000L)
                    Timber.w(error, "Stream behind live window - re-anchoring to live edge (attempt %d): %s", behindLiveRecoveries, path)
                    managerScope.launch {
                        delay(backoffMs)
                        exoPlayer?.seekToDefaultPosition()
                        exoPlayer?.prepare()
                    }
                    return
                }
            } else if (isRecoverableStreamError(error) && transientRetries < STREAM_MAX_TRANSIENT_RETRIES) {
                // P1 - transient/HTTP-5xx error: bounded retry with exponential backoff. On a live stream
                // re-anchor before re-preparing (a stale live position just fails again); radio/VOD do not.
                transientRetries++
                // S0685: same up-front "reconnecting" hint for the classified-retry path.
                reconnecting = true
                playerCallback.onStreamWaitPhase(VideoPlayerManager.StreamWaitPhase.RECONNECTING)
                val backoffMs = STREAM_TRANSIENT_BASE_DELAY_MS shl (transientRetries - 1)
                Timber.w(error, "Stream transient error - retrying in %dms (attempt %d): %s", backoffMs, transientRetries, path)
                managerScope.launch {
                    delay(backoffMs)
                    if (!isRtsp && exoPlayer?.isCurrentMediaItemLive == true) exoPlayer?.seekToDefaultPosition()
                    exoPlayer?.prepare()
                }
                return
            }
            Timber.w(error, "Stream playback error - surfacing to user: %s", path)
            reconnecting = false
            playerCallback.onBuffering(false)
            playerCallback.onStreamWaitPhase(null)
            playerCallback.onPlaybackError(error)
        }
    }

/**
 * Classifies a stream [PlaybackException] as silently recoverable (bounded retry) vs hard-fail (surface
 * the "channel unavailable" dialog at once). `BehindLiveWindow` is handled by its own path and is not
 * routed here. A bad HTTP status is recoverable only for 429/5xx (server-side, retryable); a 4xx is a
 * permanent client error and surfaces immediately.
 */
@UnstableApi
private fun isRecoverableStreamError(error: PlaybackException): Boolean = when (error.errorCode) {
    PlaybackException.ERROR_CODE_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> true
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> isRetryableHttpStatus(error)
    else -> false
}

/** True only when a bad-HTTP-status error carries a retryable response code (429 or any 5xx). */
private fun isRetryableHttpStatus(error: PlaybackException): Boolean {
    var cause: Throwable? = error.cause
    while (cause != null) {
        if (cause is HttpDataSource.InvalidResponseCodeException) {
            val code = cause.responseCode
            return code == 429 || code in 500..599
        }
        cause = cause.cause
    }
    return false
}

/**
 * Pushes the ICY now-playing title into the current item's MediaMetadata without re-buffering:
 * media3 treats a same-URI MediaItem as a metadata-only update. Best-effort - a malformed timeline
 * must not crash playback.
 */
@UnstableApi
private fun VideoPlayerManager.updateNowPlayingTitle(title: String) {
    val player = exoPlayer ?: return
    val index = player.currentMediaItemIndex
    val current = player.currentMediaItem ?: return
    try {
        val updated = current.buildUpon()
            .setMediaMetadata(
                current.mediaMetadata.buildUpon()
                    .setTitle(title)
                    .build()
            )
            .build()
        player.replaceMediaItem(index, updated)
    } catch (e: IllegalStateException) {
        Timber.w(e, "Stream now-playing metadata update skipped")
    }
}

private const val STREAM_MAX_BEHIND_LIVE_RECOVERIES = 3
private const val STREAM_MAX_TRANSIENT_RETRIES = 4
private const val STREAM_TRANSIENT_BASE_DELAY_MS = 2_000L
