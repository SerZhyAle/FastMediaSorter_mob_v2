package com.sza.fastmediasorter.ui.player.helpers

import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
 * The streaming buffer is tuned larger than local playback and prioritises time over size so a relay
 * with variable throughput keeps a steady backlog; a transient network drop is retried once before
 * the error surfaces. RTSP on a flavor without the module surfaces an explicit message instead of
 * silently failing.
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

    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(30_000, 60_000, 2_500, 5_000)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    val builder = ExoPlayer.Builder(context)
        .setLoadControl(loadControl)
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
        player.setMediaItem(MediaItem.fromUri(uri))
    }
    player.prepare()
    player.playWhenReady = playWhenReady
    Timber.i("VideoPlayerManager.playStreamVideo: prepared %s (rtsp=%s)", path, isRtsp)
}

/**
 * Lean listener for the stream player: forwards buffering/ready to the UI, reads ICY radio metadata,
 * and retries a single transient network drop before surfacing the error. Deliberately does NOT reuse
 * the full video listener (poster extraction, watch clock, decoder-failure tracking) - none of it
 * applies to a live stream and the file-poster path is wrong for a network URL.
 */
@UnstableApi
private fun VideoPlayerManager.streamPlaybackListener(path: String): Player.Listener =
    object : Player.Listener {
        private var transientRetryDone = false

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playerCallback.isActivityDestroyed()) return
            when (playbackState) {
                Player.STATE_BUFFERING -> playerCallback.onBuffering(true)
                Player.STATE_READY -> {
                    transientRetryDone = false
                    playerCallback.onBuffering(false)
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
            val transient = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
            if (transient && !transientRetryDone) {
                transientRetryDone = true
                Timber.w(error, "Stream transient network error - retrying once in 3s: %s", path)
                managerScope.launch {
                    delay(STREAM_RETRY_DELAY_MS)
                    exoPlayer?.prepare()
                }
                return
            }
            Timber.w(error, "Stream playback error - surfacing to user: %s", path)
            playerCallback.onBuffering(false)
            playerCallback.onPlaybackError(error)
        }
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

private const val STREAM_RETRY_DELAY_MS = 3_000L
