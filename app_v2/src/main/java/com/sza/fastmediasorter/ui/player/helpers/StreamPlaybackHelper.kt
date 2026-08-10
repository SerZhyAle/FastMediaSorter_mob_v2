package com.sza.fastmediasorter.ui.player.helpers

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.playback.RadioStreamBufferConfig
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import com.sza.fastmediasorter.ui.player.VideoTrackSelectionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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

    // S0936: a fresh playback session starts with a full watchdog-recovery budget, and a stale
    // "reconnecting" flag must not leak a RECONNECTING label into the new stream's first buffering.
    streamWatchdogRecoveries = 0
    streamWatchdogReconnecting = false

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

    val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    val builder = ExoPlayer.Builder(context)
        .setLoadControl(loadControl)
        .setBandwidthMeter(bandwidthMeter)
        .setAudioAttributes(audioAttributes, true)
        // S1125: same renderers profile as every other playback path (decoder fallback ON + extension
        // PREFER). The stream path historically built the default factory (fallback off), so a hardware
        // decoder init failure surfaced "channel unavailable" instead of retrying another decoder.
        .setRenderersFactory(createPlaybackRenderersFactory(context))
    // S1128: explicit track selector on the http(s) branch so repeated stalls can cap the video ceiling
    // one rendition down (the built-in ABR reacts to bandwidth only, not to a CPU-decode bottleneck).
    // RTSP has no HLS/DASH rendition ladder, so it keeps the implicit default selector.
    val trackSelector = if (!isRtsp) DefaultTrackSelector(context) else null
    if (!isRtsp) {
        // http(s): let the core auto-detect progressive / HLS / DASH (segmented resolves only where the modules exist).
        // S1512: the same loader policy the audio paths use. Without it the video session ran the stock
        // policy, so the first segment error reached the player and the whole session was rebuilt by the
        // application ladder - while radio on the same network merely paused and resumed.
        builder.setMediaSourceFactory(
            DefaultMediaSourceFactory(context)
                .setDataSourceFactory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(RadioStreamBufferConfig.createLoadErrorHandlingPolicy(context))
        )
        Timber.d("S1512: http(s) video session built with the shared loader policy")
        trackSelector?.let { builder.setTrackSelector(it) }
    }

    val player = builder.build()
    exoPlayer = player
    // S1128: quality step-down policy for this http(s) session, fed by onTracksChanged (rendition
    // inventory) and the post-first-frame stall signal below; applies its cap through trackSelector.
    activeStreamTrackSelector = trackSelector
    activeStreamStepDownController = if (!isRtsp) StreamQualityStepDownController() else null
    // S0893: BandwidthAdaptiveLoadControl (this file's loadControl) is not itself a Player.Listener -
    // only the per-stream listener needs tracking here so release()/onDestroy() can remove it.
    val streamListener = streamPlaybackListener(path, player)
    player.addListener(streamListener)
    activeExtraPlayerListener = streamListener
    // S1127: attach the diagnostics AnalyticsListener alongside the stream listener; tracked on the manager
    // so both teardown paths (releasePlayer/onDestroy) remove it symmetrically and log the session summary.
    val diagnostics = StreamPlaybackDiagnostics { android.os.SystemClock.elapsedRealtime() }
    val analyticsListener = StreamDiagnosticsAnalyticsListener(path, diagnostics)
    player.addAnalyticsListener(analyticsListener)
    activeStreamAnalyticsListener = analyticsListener
    activeStreamDiagnostics = diagnostics
    // S1510: the periodic half of the same diagnostics. Started here and stopped in
    // releaseStreamDiagnostics, so it lives exactly as long as the listener above and can never tick
    // against a released player. The bandwidth meter and the player are read through this closure
    // rather than stored as fields - they are already session-scoped by being captured here.
    activeStreamStatsSampler = startStreamStatsSampler(player, analyticsListener, bandwidthMeter)
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
    // S1144 (ADR-4/ADR-6): seat the channel's remembered track languages before prepare so the first
    // track selection already honours them; null leaves the global default in charge. This function is
    // already suspending, so the DAO read needs no extra scope.
    trackSelectionManager.channelPreference = streamTrackPreferenceUseCase.read(path)
    // S1144 (phase 04): stream-wide defaults sit under the per-channel pick and over the generic
    // player settings, so both are seated together and cleared together in playVideo.
    val streamSettings = settingsRepository.getSettings().first()
    trackSelectionManager.streamDefaults = VideoTrackSelectionManager.StreamDefaults(
        audioIso = streamSettings.streamsDefaultAudioLanguage.isoCodeOrNull(),
        subtitleIso = streamSettings.streamsDefaultSubtitleLanguage.isoCodeOrNull()
    )
    Timber.d(
        "S1144: seated channel=%s defaults=%s",
        trackSelectionManager.channelPreference,
        trackSelectionManager.streamDefaults
    )

    // S1127: open the time-to-first-frame window exactly at prepare, so TTFF excludes setup work above.
    diagnostics.onPrepared()
    player.prepare()
    player.playWhenReady = playWhenReady
    Timber.i("VideoPlayerManager.playStreamVideo: prepared %s (rtsp=%s)", path, isRtsp)
}

/** Cancels and rearms the one-shot frame capture owned by the current stream session. */
internal fun VideoPlayerManager.resetStreamFrameCapture() {
    streamFrameCaptureJob?.cancel()
    streamFrameCaptureJob = null
    streamFrameCaptureAttempted = false
}

/**
 * S1510: builds and starts the session's periodic sampler.
 *
 * The three sources are passed in rather than read from manager fields because all three are already
 * session-scoped locals in [playStreamVideo]; capturing them in the reading closure is what ties the
 * sampler's readings to exactly the session that created it.
 */
@UnstableApi
private fun VideoPlayerManager.startStreamStatsSampler(
    player: ExoPlayer,
    analyticsListener: StreamDiagnosticsAnalyticsListener,
    bandwidthMeter: DefaultBandwidthMeter,
): StreamStatsSampler = StreamStatsSampler(
    scope = managerScope,
    intervalMs = StreamStatsSampler.SAMPLE_INTERVAL_MS,
    readSample = {
        val format = player.videoFormat
        StreamStatsSample(
            atMs = android.os.SystemClock.elapsedRealtime(),
            renderedFrames = analyticsListener.renderedFrames(),
            bitrateEstimateBps = bandwidthMeter.bitrateEstimate,
            width = format?.width ?: 0,
            height = format?.height ?: 0,
            formatBitrateBps = format?.bitrate ?: 0,
        )
    },
).also { it.start() }

/**
 * S1127: detach the stream diagnostics AnalyticsListener and log the one-line session summary.
 * Symmetric with the add in [playStreamVideo]; the removeAnalyticsListener token is co-located here with
 * its add so the per-file listener-symmetry gate stays balanced. Called from both
 * `VideoPlayerLifecycleHelper` teardown paths (releasePlayer / onDestroy).
 */
@UnstableApi
internal fun VideoPlayerManager.releaseStreamDiagnostics(player: ExoPlayer) {
    activeStreamAnalyticsListener?.let { player.removeAnalyticsListener(it) }
    activeStreamDiagnostics?.let { Timber.i("Stream session: %s", it.summary()) }
    // S1510: stopped on the same edge that removes the listener - the sampler holds the player through
    // its reading closure, so an unstopped one would both leak it and log about a session that ended.
    activeStreamStatsSampler?.stop()
    activeStreamStatsSampler = null
    activeStreamAnalyticsListener = null
    activeStreamDiagnostics = null
    // S1128: the track selector and step-down policy are plain per-session fields (not listeners), so
    // teardown just drops the references alongside the diagnostics they share a lifecycle with.
    activeStreamTrackSelector = null
    activeStreamStepDownController = null
}

/**
 * S1128: reads the video rendition ladder from Media3 [Tracks] into the session's step-down policy. Runs
 * once per session: applying a quality cap re-fires `onTracksChanged` with the SAME (full) ladder but a
 * changed selection, and re-inventorying would reset the ceiling index and undo the step-down bookkeeping,
 * so a second call while the ladder is already populated is a no-op. A rendition needs a real size; its
 * bitrate may be unknown (`Format.NO_VALUE`) and is passed through for the controller to handle. An empty
 * result (a mid-transition audio-only track set) is ignored so it never clobbers a good ladder.
 */
@UnstableApi
private fun VideoPlayerManager.inventoryStreamRenditions(tracks: Tracks, path: String) {
    // Skip when there is no controller (RTSP) or the ladder is already populated (the once-per-session
    // guard - see the KDoc): applying a cap re-fires onTracksChanged and re-inventory would reset the ceiling.
    val controller = activeStreamStepDownController?.takeIf { it.renditionCount == 0 } ?: return
    val renditions = mutableListOf<StreamQualityStepDownController.Rendition>()
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_VIDEO) continue
        val mediaGroup = group.mediaTrackGroup
        for (i in 0 until mediaGroup.length) {
            val format = mediaGroup.getFormat(i)
            if (format.width <= 0 || format.height <= 0) continue
            renditions += StreamQualityStepDownController.Rendition(format.width, format.height, format.bitrate)
        }
    }
    if (renditions.isEmpty()) return
    controller.setRenditions(renditions)
    Timber.i(
        "Stream quality: renditions=%d single=%b path=%s",
        controller.renditionCount,
        controller.isSingleQuality,
        path,
    )
}

/**
 * S1128: on a resolved stall, ask the step-down policy for a lower ceiling and, if it returns one, cap the
 * track selector so ABR cannot climb back into the stalling rendition. No-op when the policy declines
 * (single-quality, already at the floor, or the stall threshold is not yet reached).
 *
 * S1508: the policy ages stalls out of a decay window, so it is fed `elapsedRealtime` and not wall-clock
 * time - an NTP correction or a manual clock change mid-session would otherwise either expire live stalls
 * or freeze the window open, which is the session-lifetime counter the window replaced.
 */
@UnstableApi
private fun VideoPlayerManager.applyStreamQualityStepDown(path: String) {
    // S1514: the rendition the engine actually settled on, read straight off the player. No new listener
    // is needed for it - the fourth candidate in the ticket asked whether onTracksChanged already carried
    // the signal, and the answer turned out to be simpler still: ExoPlayer reports the current video
    // format directly, in one call, with no subscription to keep in sync.
    val playingFormat = exoPlayer?.videoFormat
    val playing = playingFormat?.takeIf { it.width > 0 && it.height > 0 }?.let {
        StreamQualityStepDownController.Rendition(it.width, it.height, it.bitrate)
    }
    val cap = activeStreamStepDownController?.registerStall(SystemClock.elapsedRealtime(), playing) ?: return
    activeStreamTrackSelector?.let { selector ->
        selector.setParameters(
            selector.buildUponParameters()
                .setMaxVideoSize(cap.maxWidthPx, cap.maxHeightPx)
                .setMaxVideoBitrate(cap.maxBitrateBps)
                .build(),
        )
    }
    // S1514: both numbers, because they are different numbers. The line used to print the ceiling alone,
    // so a step that changed nothing read exactly like one that did - the failure mode the source
    // document calls out as the worst kind of log.
    Timber.i(
        "Stream quality: stepped down to <=%dx%d @%dbps from playing=%s path=%s",
        cap.maxWidthPx,
        cap.maxHeightPx,
        cap.maxBitrateBps,
        playing?.let { "${it.widthPx}x${it.heightPx}" } ?: "unknown",
        path,
    )
}

/**
 * Lean listener for the stream player: forwards buffering/ready to the UI, reads ICY radio metadata,
 * and heals recoverable failures (live-edge desync + transient network drops) by re-anchoring to the
 * live edge and re-preparing within a bounded budget before the error surfaces. Deliberately does NOT
 * reuse the full video listener (poster extraction, watch clock, decoder-failure tracking) - none of it
 * applies to a live stream and the file-poster path is wrong for a network URL.
 */
@UnstableApi
private fun VideoPlayerManager.streamPlaybackListener(
    path: String,
    sessionPlayer: ExoPlayer,
): Player.Listener =
    object : Player.Listener {
        private val isRtsp = path.startsWith("rtsp://")
        private var behindLiveRecoveries = 0
        private var transientRetries = 0
        // S0685: true while a recovery path (re-anchor / classified retry) is re-establishing the stream,
        // so the next BUFFERING is labelled "reconnecting" instead of a plain buffer fill. Set by the
        // recovery branches in onPlayerError, cleared on a confirmed READY and on hard-fail.
        private var reconnecting = false

        // S1128: gate the quality step-down to genuine stalls only. hadFirstFrame flips once playback has
        // started; stallOpen is armed only by a BUFFERING entered after the first frame (a rebuffer, not
        // the initial fill), mirroring the S1127 stall semantics, and cleared when READY resolves it.
        private var hadFirstFrame = false
        private var stallOpen = false

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playerCallback.isActivityDestroyed()) return
            // S0937: log every stream state transition so a silent stall (stuck BUFFERING with no
            // PlaybackException, so no recovery fires) shows up in a plain Timber harvest, not only a
            // full system logcat. States are low-frequency (not per-frame) - permanent Timber.d adds no
            // hot-path spam. Mirrors VideoPlayerManager.playerListener, which the stream path bypasses.
            Timber.d("Stream state=%s reconnecting=%b path=%s", streamStateLabel(playbackState), reconnecting, path)
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    // S1128: a BUFFERING entered after the first frame is a real stall - arm the
                    // step-down evaluation for the READY that resolves it. Initial fill is excluded.
                    if (hadFirstFrame) stallOpen = true
                    // S0936: arm the buffering-without-ready timeout - a live stall can present as
                    // BUFFERING that never returns to READY, with no PlaybackException to recover from.
                    armStreamBufferingTimeout()
                    playerCallback.onBuffering(true)
                    // S0936: a watchdog-triggered re-prepare buffers through the manager-level flag,
                    // which this listener cannot set locally - both recovery kinds share the label.
                    playerCallback.onStreamWaitPhase(
                        if (reconnecting || streamWatchdogReconnecting) VideoPlayerManager.StreamWaitPhase.RECONNECTING
                        else VideoPlayerManager.StreamWaitPhase.BUFFERING
                    )
                }
                Player.STATE_READY -> {
                    // Reset the recovery budgets only on a confirmed READY, never on BUFFERING: a stream
                    // that flaps buffering<->error must not silently refill its quota and spin forever.
                    behindLiveRecoveries = 0
                    transientRetries = 0
                    reconnecting = false
                    // S0936: the watchdog budget holds the same invariant as the error budgets above.
                    streamWatchdogRecoveries = 0
                    streamWatchdogReconnecting = false
                    // S0936: (re)start the position-stall poll; also clears any pending
                    // buffering-timeout runnable armed above (cancelStreamStallWatchdog is called first).
                    startStreamStallWatchdog()
                    playerCallback.onBuffering(false)
                    playerCallback.onStreamWaitPhase(null)
                    playerCallback.onPlaybackReady()
                    // S1128: a stall just resolved - let the policy decide whether repeated stalls warrant
                    // capping the video ceiling one rendition down. No-op on single-quality / RTSP / floor.
                    if (stallOpen) {
                        stallOpen = false
                        applyStreamQualityStepDown(path)
                    }
                }
                Player.STATE_ENDED -> {
                    cancelStreamStallWatchdog()
                    playerCallback.onPlaybackEnded()
                }
                Player.STATE_IDLE -> cancelStreamStallWatchdog()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // S0937: pairs with the state log above - a silent stall reads as isPlaying=false while the
            // state never returns to READY, distinguishing a genuine freeze from a normal pause/buffer.
            Timber.d("Stream isPlaying=%b path=%s", isPlaying, path)
            playerCallback.onPlaybackStateChanged(isPlaying)
        }

        override fun onTracksChanged(tracks: Tracks) {
            // S1128: inventory the http(s) video rendition ladder into the step-down policy. A single
            // selectable video format means a single-quality media playlist - nothing to step down to.
            inventoryStreamRenditions(tracks, path)
        }

        override fun onRenderedFirstFrame() {
            hadFirstFrame = true
            if (streamFrameCaptureAttempted) return
            streamFrameCaptureAttempted = true
            streamFrameCaptureJob = managerScope.launch {
                delay(STREAM_FRAME_CAPTURE_DELAY_MS)
                if (exoPlayer !== sessionPlayer || currentFilePath != path) return@launch
                val playerView = currentPlayerView ?: return@launch
                val bitmap = PlayerTextureFrameCapture.capture(
                    playerView = playerView,
                    width = STREAM_FRAME_CAPTURE_WIDTH,
                    height = STREAM_FRAME_CAPTURE_HEIGHT,
                    onFailure = { failure ->
                        Timber.w(failure, "Stream frame capture skipped: %s", path)
                    },
                ) ?: return@launch
                val adopted = streamFrameIngestor?.ingest(path, bitmap) == true
                if (adopted && exoPlayer === sessionPlayer && currentFilePath == path) {
                    onStreamFrameIngested?.invoke(path)
                }
            }
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
                        playerCallback.onStreamProgramName(nowPlaying)
                    }
                    else -> Unit
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // S0895: capture the errored instance now - exoPlayer is a mutable property that can be
            // reassigned to a different file's player before the delayed recovery below fires (user
            // navigates away during the backoff window). Acting on the stale reference would yank
            // that file's already-restored position instead of recovering the stream that errored.
            val erroredPlayer = exoPlayer
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
                        if (exoPlayer !== erroredPlayer) return@launch
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
                // S1512: capped like every other retry ladder in the project. Uncapped this reached 16s on
                // the fourth attempt, and it is now the second echelon - the loader has already spent its
                // own retries by the time an error gets here.
                val backoffMs = (STREAM_TRANSIENT_BASE_DELAY_MS shl (transientRetries - 1))
                    .coerceAtMost(RadioStreamBufferConfig.MAX_RETRY_DELAY_MS)
                Timber.w(error, "Stream transient error - retrying in %dms (attempt %d): %s", backoffMs, transientRetries, path)
                managerScope.launch {
                    delay(backoffMs)
                    if (exoPlayer !== erroredPlayer) return@launch
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

/** S0937: human-readable label for a Media3 [Player] playback state - keeps stream state logs greppable. */
private fun streamStateLabel(state: Int): String = when (state) {
    Player.STATE_IDLE -> "IDLE"
    Player.STATE_BUFFERING -> "BUFFERING"
    Player.STATE_READY -> "READY"
    Player.STATE_ENDED -> "ENDED"
    else -> "UNKNOWN($state)"
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
private const val STREAM_FRAME_CAPTURE_DELAY_MS = 750L
private const val STREAM_FRAME_CAPTURE_WIDTH = 640
private const val STREAM_FRAME_CAPTURE_HEIGHT = 360
