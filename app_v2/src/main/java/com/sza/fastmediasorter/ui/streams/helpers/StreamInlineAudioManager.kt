package com.sza.fastmediasorter.ui.streams.helpers

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyInfo
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.playback.NowPlayingMetadata
import com.sza.fastmediasorter.core.playback.RadioStreamBufferConfig
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.ui.player.helpers.AudioServiceController
import com.sza.fastmediasorter.ui.player.helpers.StreamDataSourceFactoryProvider
import com.sza.fastmediasorter.ui.streams.StreamTitleFormatter
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber

/**
 * Owns the bottom sticky mini-control for inline radio playback. An AUDIO source plays through the
 * background-capable [AudioServiceController] (so it survives screen-off), the list stays visible and
 * interactive, and ICY now-playing metadata flows into the control text. All inline-audio logic lives
 * here - the Activity only forwards taps (Rule 3).
 */
@UnstableApi
class StreamInlineAudioManager(
    lifecycleOwner: LifecycleOwner,
    private val views: StreamInlineAudioViews,
    private val audioController: AudioServiceController,
    private val callbacks: StreamInlineAudioCallbacks = StreamInlineAudioCallbacks(),
) {

    private var currentSource: StreamSourceEntity? = null
    private var player: Player? = null
    // S0577: the in-app player used when background playback is OFF (no foreground service).
    private var localPlayer: ExoPlayer? = null
    // S0577: true while the active stream is owned by the background AudioPlaybackService (ON path).
    private var usingService = false
    private val nowPlaying = MutableStateFlow<NowPlayingMetadata?>(null)
    // S0593: guard so a single play records "OK" once, even though isPlaying can toggle (re-buffer).
    private var successReported = false
    private val recoveryHandler = Handler(Looper.getMainLooper())
    private var connectionStartedAtMs = 0L
    private var hasSuccessfulPlayback = false
    private var retryAttempt = 0
    private var noSignalVisible = false
    private val toleranceRunnable = Runnable { handleToleranceTimeout() }
    private val retryRunnable = Runnable { retryLocalPlayback() }

    // Audio-sink starvation never surfaces as a state change or error, so radio stutters in the
    // OFF-mode (in-app) player were invisible to logging. Underruns are the ground truth for them.
    private val underrunListener = object : AnalyticsListener {
        override fun onAudioUnderrun(
            eventTime: AnalyticsListener.EventTime,
            bufferSize: Int,
            bufferSizeMs: Long,
            elapsedSinceLastFeedMs: Long
        ) {
            Timber.w(
                "StreamInlineAudioManager: audio underrun bufferSizeMs=%d elapsedSinceLastFeedMs=%d",
                bufferSizeMs,
                elapsedSinceLastFeedMs
            )
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // S0593: first transition to actually-playing = the stream works here -> record OK once.
            if (isPlaying) {
                markPlaybackHealthy()
                reportSuccessfulPlayback()
                callbacks.onPlaybackStateChanged(currentSource, true)
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) callbacks.onPlaybackStateChanged(null, false)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    markPlaybackHealthy()
                    reportSuccessfulPlayback()
                }
                Player.STATE_BUFFERING -> if (hasSuccessfulPlayback) scheduleToleranceTimeout()
            }
        }

        override fun onMetadata(metadata: Metadata) {
            // S1142: OFF/local path - the in-app ExoPlayer delivers raw ICY here; parse into artist/title.
            for (i in 0 until metadata.length()) {
                val entry = metadata.get(i)
                if (entry is IcyInfo) {
                    nowPlaying.value = entry.title?.takeIf { it.isNotBlank() }?.let(NowPlayingMetadata::parse)
                    Timber.d("S1142: inline local ICY, nowPlaying=${nowPlaying.value}")
                }
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            // S1142: background/service path - the MediaController never receives raw onMetadata, only the
            // combined metadata the service pushes (Phase 02). It is a live track once its title differs
            // from the station name; the pre-track state (title still == station) renders station-only.
            if (!usingService) return
            val title = mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
            nowPlaying.value = if (title != null && title != currentSource?.title) {
                NowPlayingMetadata(mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() }, title)
            } else {
                null
            }
            Timber.d("S1142: inline service metadata, nowPlaying=${nowPlaying.value}")
        }

        override fun onPlayerError(error: PlaybackException) {
            val failed = currentSource
            Timber.w(error, "StreamInlineAudioManager: inline audio error - %s", failed?.url)
            if (canRetry(error)) {
                if (!usingService) scheduleLocalRetry()
                return
            }
            stop()
            failed?.let(callbacks.onError)
        }
    }

    init {
        views.playStopButton.setOnClickListener { stop() }
        lifecycleOwner.collectOnLifecycle(nowPlaying) {
            renderTitle()
            val track = nowPlaying.value?.trackLine()
            callbacks.onNowPlayingChanged(playingId, track)
        }
    }

    /**
     * S0778: the bottom sticky mini-control is the last child of an edge-to-edge content column, so
     * under targetSdk 35 it drew beneath the navigation bar / a side display cutout and its stop button
     * was untappable. Pad it inside the safe rect on every edge except top (the toolbar owns the status
     * bar). The shared helper clamps to the live inset values and re-applies on rotation, so a landscape
     * side cutout is covered too. Wiring lives here, not in the Activity (Rule 3).
     */
    fun applyWindowInsets() {
        views.miniControl.applySystemBarInsetPadding(applyTop = false)
    }

    /** Returns the id of the source currently playing inline, or null. */
    val playingId: String? get() = currentSource?.id

    /**
     * S1474: the engine currently playing inline - the in-app one on the OFF path, the service one on the
     * ON path, null when nothing plays. Read-only and borrowed: a reader may take values off it and must
     * never prepare, start, stop or release it, because ownership stays here.
     *
     * It exists so the "about this channel" window can report the station already playing without opening
     * a second connection to a server that may allow only one.
     */
    val playingEngine: Player? get() = player

    /** S0577: true while an OFF-mode (in-app, non-service) stream is playing. */
    val isLocalPlaybackActive: Boolean get() = localPlayer != null

    /** S0577: true while a stream is owned by the background service (ON path). */
    val isServiceAudioActive: Boolean get() = usingService && currentSource != null

    /** S0577: the background-service player for the exit resolver; null in OFF (local) mode. */
    val activeServicePlayer: Player? get() = if (usingService) player else null

    fun play(source: StreamSourceEntity, useBackgroundService: Boolean) {
        stopPlaybackKeepingController()
        currentSource = source
        successReported = false
        hasSuccessfulPlayback = source.lastPlayedAt != null
        connectionStartedAtMs = SystemClock.elapsedRealtime()
        retryAttempt = 0
        noSignalVisible = false
        scheduleToleranceTimeout()
        nowPlaying.value = null
        views.miniControl.isVisible = true
        Timber.d("S1219: mini control shown, width=${views.miniControl.width}")
        callbacks.onPlayingChanged(source.id)
        renderTitle()
        Timber.i("StreamInlineAudioManager: inline audio start - %s (bg=%b)", source.url, useBackgroundService)
        usingService = useBackgroundService
        if (useBackgroundService) {
            audioController.playAudioWithMetadata(Uri.parse(source.url), source.title) { startedPlayer ->
                // S0874: the service connect is async - a stop()/newer play() during the connect window
                // clears currentSource. Without this guard the late callback leaves an orphaned playing
                // service player with all inline-UI state cleared (no reachable stop). Bail if stale.
                if (currentSource?.id != source.id) {
                    startedPlayer.stop()
                    return@playAudioWithMetadata
                }
                player = startedPlayer
                startedPlayer.addListener(playerListener)
            }
        } else {
            // Background playback OFF: mirror local audio - play in-app, with no foreground service and
            // no media notification; StreamsActivity stops this on screen leave / background (S0577).
            // S1015: reuse the shared factory (Icy-MetaData + userinfo Basic-Auth) instead of a local
            // one-off - NetworkAwareMediaSourceFactory's http/https branch already does the same for the
            // background-service twin, and a duplicated factory here had silently dropped the auth fix.
            val httpFactory = StreamDataSourceFactoryProvider.create(views.miniControl.context)
            // S0896: this OFF-mode local player had no audio-focus/becoming-noisy handling, unlike
            // its service-mode twin (audioController.playAudioWithMetadata -> AudioPlaybackService).
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()
            val local = ExoPlayer.Builder(views.miniControl.context)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(httpFactory).setLoadErrorHandlingPolicy(
                        RadioStreamBufferConfig.createLoadErrorHandlingPolicy(views.miniControl.context)
                    )
                )
                .setLoadControl(RadioStreamBufferConfig.createLoadControl(views.miniControl.context))
                .setAudioAttributes(
                    audioAttributes,
                    /* handleAudioFocus= */
                    true
                )
                .setHandleAudioBecomingNoisy(true)
                .build()
            Timber.d("S0896: StreamInlineAudioManager OFF-mode player built with handleAudioFocus")
            local.setMediaItem(MediaItem.fromUri(source.url))
            local.addListener(playerListener)
            local.addAnalyticsListener(underrunListener)
            local.prepare()
            local.playWhenReady = true
            localPlayer = local
            player = local
        }
    }

    fun stop() {
        stopPlaybackKeepingController()
        views.miniControl.isVisible = false
    }

    /** Releases the service connection; call from the Activity's onDestroy. */
    fun release() {
        stop()
        audioController.release()
    }

    /**
     * S0577: detach without stopping the service player, so a background-continue exit keeps the
     * stream playing. The MediaSessionService survives the controller release.
     */
    fun releaseKeepingBackgroundService() {
        player?.removeListener(playerListener)
        player = null
        usingService = false
        currentSource = null
        nowPlaying.value = null
        callbacks.onPlayingChanged(null)
        views.miniControl.isVisible = false
        audioController.release()
    }

    private fun stopPlaybackKeepingController() {
        if (currentSource != null) callbacks.onPlaybackStateChanged(null, false)
        clearRecoveryState()
        player?.removeListener(playerListener)
        val local = localPlayer
        if (local != null) {
            // In-app player owns its own resources - release it, don't merely stop it.
            local.removeAnalyticsListener(underrunListener)
            local.release()
            localPlayer = null
        } else {
            // Service player: quiesce it (stop + drop playWhenReady + clear the playlist) but keep the
            // controller connected for the next play(). This lets AudioPlaybackService.onTaskRemoved's
            // no-active-playback heuristic (!playWhenReady || mediaItemCount == 0) stopSelf() the service.
            player?.let { p ->
                p.playWhenReady = false
                p.stop()
                p.clearMediaItems()
            }
        }
        player = null
        usingService = false
        currentSource = null
        nowPlaying.value = null
        callbacks.onPlayingChanged(null)
    }

    private fun renderTitle() {
        // S1142: single-string `station - Artist - Title` via the shared formatter (ADR-3/ADR-5); the
        // formatter reuses S0691's `Name (Name)` dedup so the mini-control matches the list/grid rendering.
        val station = currentSource?.title ?: return
        val displayTitle = StreamTitleFormatter.nowPlayingLine(station, nowPlaying.value)
        views.titleView.text = if (noSignalVisible) {
            "$displayTitle - [${views.miniControl.context.getString(R.string.streams_no_signal)}]"
        } else {
            displayTitle
        }
    }

    /**
     * Audio is flowing: drop the pending stall timeout and any "no signal" marker it already raised.
     * Deliberately separate from [reportSuccessfulPlayback], which is once-per-play (successReported) -
     * after the first re-buffer that guard made it a no-op, so the marker stayed on screen for the rest
     * of the session while the stream was audibly fine.
     */
    private fun markPlaybackHealthy() {
        recoveryHandler.removeCallbacks(toleranceRunnable)
        retryAttempt = 0
        if (noSignalVisible) {
            noSignalVisible = false
            renderTitle()
        }
    }

    private fun reportSuccessfulPlayback() {
        if (successReported) return
        successReported = true
        hasSuccessfulPlayback = true
        renderTitle()
        currentSource?.let(callbacks.onSuccess)
    }

    private fun scheduleToleranceTimeout() {
        recoveryHandler.removeCallbacks(toleranceRunnable)
        val delay = if (hasSuccessfulPlayback) {
            RadioStreamBufferConfig.PASSIVE_STATUS_TIMEOUT_MS
        } else {
            RadioStreamBufferConfig.DIALOG_TIMEOUT_MS
        }
        recoveryHandler.postDelayed(toleranceRunnable, delay)
    }

    private fun handleToleranceTimeout() {
        val source = currentSource ?: return
        // Never contradict what the user hears. A player that is still playing means the buffering
        // event recovered without a further state change, so the timeout is stale - stay silent.
        if (player?.isPlaying == true) {
            markPlaybackHealthy()
            return
        }
        if (hasSuccessfulPlayback) {
            noSignalVisible = true
            renderTitle()
        } else {
            stop()
            callbacks.onError(source)
        }
    }

    private fun canRetry(error: PlaybackException): Boolean {
        val firstIoCode = PlaybackException.ERROR_CODE_IO_UNSPECIFIED
        val lastIoCode = PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
        return error.errorCode in firstIoCode..lastIoCode && canContinueRetrying()
    }

    private fun scheduleLocalRetry() {
        val delay = (RadioStreamBufferConfig.BASE_RETRY_DELAY_MS shl retryAttempt)
            .coerceAtMost(RadioStreamBufferConfig.MAX_RETRY_DELAY_MS)
        retryAttempt = (retryAttempt + 1).coerceAtMost(MAX_BACKOFF_SHIFT)
        recoveryHandler.removeCallbacks(retryRunnable)
        recoveryHandler.postDelayed(retryRunnable, delay)
    }

    private fun retryLocalPlayback() {
        val local = localPlayer ?: return
        if (currentSource == null || !canContinueRetrying()) return
        local.prepare()
        local.playWhenReady = true
    }

    private fun canContinueRetrying(): Boolean = hasSuccessfulPlayback ||
        elapsedSinceConnectionStart() < RadioStreamBufferConfig.DIALOG_TIMEOUT_MS

    private fun elapsedSinceConnectionStart(): Long =
        SystemClock.elapsedRealtime() - connectionStartedAtMs

    private fun clearRecoveryState() {
        recoveryHandler.removeCallbacks(toleranceRunnable)
        recoveryHandler.removeCallbacks(retryRunnable)
        connectionStartedAtMs = 0L
        retryAttempt = 0
        noSignalVisible = false
    }

    private companion object {
        const val MAX_BACKOFF_SHIFT = 2
    }
}
