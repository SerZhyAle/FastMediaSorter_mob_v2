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
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyInfo
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.playback.NowPlayingMetadata
import com.sza.fastmediasorter.core.playback.RadioStreamBufferConfig
import com.sza.fastmediasorter.core.playback.StreamPlaybackFailureReader
import com.sza.fastmediasorter.core.playback.resilience.StreamEndedOutcome
import com.sza.fastmediasorter.core.playback.resilience.StreamEndedRule
import com.sza.fastmediasorter.core.playback.resilience.StreamFailureReason
import com.sza.fastmediasorter.core.playback.resilience.StreamInlineRetryDecision
import com.sza.fastmediasorter.core.playback.resilience.StreamInlineRetryPolicy
import com.sza.fastmediasorter.core.playback.resilience.toReason
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.domain.player.BroadcastPlaybackPairs
import com.sza.fastmediasorter.domain.player.BroadcastPlaybackSessions
import com.sza.fastmediasorter.domain.player.BroadcastStopSignal
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

    // A paused station keeps currentSource, so its row stays the active one and re-tapping it stops.
    private var paused = false

    private fun updatePaused(value: Boolean) {
        if (paused == value) return
        paused = value
        callbacks.onPausedChanged(value)
    }

    // False for a resume: the listening session was already counted when it started.
    private var countPlay = true

    // Stamped on every STATE_READY, never read at STATE_ENDED: a progressive source learns a duration
    // when its body ends, so at the end a live stream closed by its server looks like a finite file.
    private var liveWhilePlaying = false
    private val recoveryHandler = Handler(Looper.getMainLooper())

    // S1513: the reconnect decision - connect window, attempt counter and the usingService routing -
    // in pure code. The flag below stays here because the stall timeout co-owns it (S1513 Phase 04).
    private val retryPolicy = StreamInlineRetryPolicy()
    private var hasSuccessfulPlayback = false

    // STREAM-BANK 2.10 sections 2.5 and 6.1, read per prepare: until audio flowed since the user's own
    // start, the tolerance timer is the 15 s bound to the dialog, whatever the station did on earlier days.
    private var readySincePrepare = false
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
                    liveWhilePlaying = isLiveOrDurationUnknown()
                    markPlaybackHealthy()
                    reportSuccessfulPlayback()
                }
                Player.STATE_BUFFERING -> if (readySincePrepare) scheduleToleranceTimeout()
                Player.STATE_ENDED -> if (!stopForBroadcast(BroadcastStopSignal.StreamEnded)) handleEndedStream()
            }
        }

        override fun onMetadata(metadata: Metadata) {
            // S1142: OFF/local path - the in-app ExoPlayer delivers raw ICY here; parse into artist/title.
            for (i in 0 until metadata.length()) {
                val entry = metadata.get(i)
                if (entry is IcyInfo) {
                    nowPlaying.value = entry.title?.takeIf { it.isNotBlank() }?.let(NowPlayingMetadata::parse)
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
        }

        override fun onPlayerError(error: PlaybackException) {
            val failed = currentSource
            Timber.w(error, "StreamInlineAudioManager: inline audio error - %s", failed?.url)
            if (BroadcastPlaybackPairs.isPasswordRefusal(error)) {
                stop()
                failed?.let(callbacks.onPasswordRequired)
                return
            }
            val badStatus = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
            if (badStatus && stopForBroadcast(BroadcastStopSignal.HttpStatus(error.responseCodeOrNull()))) return
            // On the service path this is the controller's copy, with no status to read: the policy then
            // answers OwnedByService and the service forwards its own verdict (onServiceStreamFailure).
            val failure = StreamPlaybackFailureReader.read(error)
            val decision = retryPolicy.onFailure(
                failure = failure,
                hasEverPlayed = hasSuccessfulPlayback,
                usingService = usingService,
                nowMs = SystemClock.elapsedRealtime(),
            )
            when (decision) {
                is StreamInlineRetryDecision.RetryAfter -> scheduleLocalRetry(decision.delayMs)
                // S0577: the background service owns this stream and runs its own ladder - the
                // mini-player deliberately schedules nothing rather than open a second connection.
                StreamInlineRetryDecision.OwnedByService -> Unit
                StreamInlineRetryDecision.GiveUp -> {
                    stop()
                    failed?.let { reportFailure(it, failure.toReason()) }
                }
            }
        }
    }

    init {
        audioController.onStreamFailure = ::onServiceStreamFailure
        views.playStopButton.setOnClickListener { onPrimaryButton() }
        views.dismissButton?.setOnClickListener { stop() }
        renderButton()
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

    fun play(source: StreamSourceEntity, useBackgroundService: Boolean) =
        startPlayback(source, useBackgroundService, countAsNewPlay = true)

    private fun startPlayback(source: StreamSourceEntity, useBackgroundService: Boolean, countAsNewPlay: Boolean) {
        stopPlaybackKeepingController()
        currentSource = source
        successReported = false
        countPlay = countAsNewPlay
        liveWhilePlaying = false
        hasSuccessfulPlayback = source.lastPlayedAt != null
        readySincePrepare = false
        retryPolicy.onStreamStarted(SystemClock.elapsedRealtime())
        noSignalVisible = false
        scheduleToleranceTimeout()
        nowPlaying.value = null
        showBar()
        callbacks.onPlayingChanged(source.id)
        renderTitle()
        renderButton()
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
                // Paused during the connect window: keep the item for resume, but play nothing.
                if (paused) quiesceKeepingItem(startedPlayer)
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
        // S4609: from here the service alone answers a lost link or a refusal of this broadcast.
        currentSource?.url?.takeIf { usingService && BroadcastPlaybackSessions.forUrl(it) != null }
            ?.let(BroadcastPlaybackSessions::markLeftToService)
        Timber.d("S4609: screen left, broadcast handed to the service=${usingService && currentSource != null}")
        player?.removeListener(playerListener)
        player = null
        usingService = false
        currentSource = null
        updatePaused(false)
        nowPlaying.value = null
        callbacks.onPlayingChanged(null)
        views.miniControl.isVisible = false
        audioController.release()
    }

    private fun stopPlaybackKeepingController() {
        BroadcastPlaybackSessions.clearLeftToService()
        if (currentSource != null) callbacks.onPlaybackStateChanged(null, false)
        clearRecoveryState()
        val servicePlayer = player?.takeIf { localPlayer == null }
        detachAndReleaseLocal()
        // Service player: quiesce it (stop + drop playWhenReady + clear the playlist) but keep the
        // controller connected for the next play(). This lets AudioPlaybackService.onTaskRemoved's
        // no-active-playback heuristic (!playWhenReady || mediaItemCount == 0) stopSelf() the service.
        servicePlayer?.let { p ->
            p.playWhenReady = false
            p.stop()
            p.clearMediaItems()
        }
        player = null
        usingService = false
        currentSource = null
        updatePaused(false)
        countPlay = true
        nowPlaying.value = null
        callbacks.onPlayingChanged(null)
        renderButton()
    }

    private fun onPrimaryButton() {
        when {
            views.dismissButton == null -> stop()
            paused -> resume()
            else -> pause()
        }
    }

    /**
     * Pause is a stop that keeps the station: a paused live stream must not hold its connection, or the
     * server drops it and the retry ladder would run against a station nobody is listening to. The service
     * player keeps its media item, so a resume is no media-item transition and the service, which counts
     * a play once per item, counts nothing again.
     */
    private fun pause() {
        if (currentSource == null) return
        clearRecoveryState()
        if (usingService) {
            player?.let(::quiesceKeepingItem)
        } else {
            detachAndReleaseLocal()
            player = null
        }
        updatePaused(true)
        Timber.d("S4615: paused service=$usingService")
        // A paused station is not resumed after a restart, as a pause from the notification is not either.
        callbacks.onPlaybackStateChanged(null, false)
        renderTitle()
        renderButton()
    }

    private fun resume() {
        val source = currentSource ?: return
        val servicePlayer = player
        if (!usingService || servicePlayer == null) {
            startPlayback(source, usingService, countAsNewPlay = false)
            return
        }
        updatePaused(false)
        Timber.d("S4615: resume on the service player at the live edge")
        successReported = false
        countPlay = false
        readySincePrepare = false
        retryPolicy.onStreamStarted(SystemClock.elapsedRealtime())
        scheduleToleranceTimeout()
        // The default position of a live item is its live edge, so the listener hears the station now.
        servicePlayer.seekToDefaultPosition()
        servicePlayer.prepare()
        servicePlayer.playWhenReady = true
        renderTitle()
        renderButton()
    }

    // Detaches the listener from whichever engine is current; the in-app one owns its resources, so it
    // is released, never merely stopped.
    private fun detachAndReleaseLocal() {
        player?.removeListener(playerListener)
        val local = localPlayer ?: return
        local.removeAnalyticsListener(underrunListener)
        local.release()
        localPlayer = null
    }

    private fun quiesceKeepingItem(servicePlayer: Player) {
        servicePlayer.playWhenReady = false
        servicePlayer.stop()
    }

    private fun showBar() {
        val appearing = !views.miniControl.isVisible
        views.miniControl.isVisible = true
        // INPUT-PARITY rule 1: a D-pad or keyboard user reaches the new control without hunting for it.
        if (appearing && views.dismissButton != null && !views.miniControl.isInTouchMode) {
            views.playStopButton.requestFocus()
        }
    }

    private fun renderButton() {
        if (views.dismissButton == null) return
        val (icon, label) = if (paused) {
            R.drawable.ic_play to R.string.streams_resume
        } else {
            R.drawable.ic_pause to R.string.streams_pause
        }
        views.playStopButton.setImageResource(icon)
        views.playStopButton.contentDescription = views.miniControl.context.getString(label)
    }

    // A catalog stream on the service path is counted by AudioPlaybackService on its first READY; a
    // broadcast plays a resolved address the service cannot map to a row, so the screen counts it.
    private fun isCountedByService(source: StreamSourceEntity): Boolean =
        usingService && BroadcastPlaybackSessions.forUrl(source.url) == null

    private fun renderTitle() {
        // S1142: single-string `station - Artist - Title` via the shared formatter (ADR-3/ADR-5); the
        // formatter reuses S0691's `Name (Name)` dedup so the mini-control matches the list/grid rendering.
        val station = currentSource?.title ?: return
        val displayTitle = StreamTitleFormatter.nowPlayingLine(station, nowPlaying.value)
        val marker = when {
            paused -> R.string.streams_paused
            noSignalVisible -> R.string.streams_no_signal
            else -> null
        }
        views.titleView.text = marker?.let { "$displayTitle - [${views.miniControl.context.getString(it)}]" }
            ?: displayTitle
    }

    /**
     * Audio is flowing: drop the pending stall timeout and any "no signal" marker it already raised.
     * Deliberately separate from [reportSuccessfulPlayback], which is once-per-play (successReported) -
     * after the first re-buffer that guard made it a no-op, so the marker stayed on screen for the rest
     * of the session while the stream was audibly fine.
     */
    private fun markPlaybackHealthy() {
        readySincePrepare = true
        recoveryHandler.removeCallbacks(toleranceRunnable)
        retryPolicy.onPlaybackHealthy()
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
        val source = currentSource ?: return
        callbacks.onPlaybackStarted(source)
        Timber.d("S4615: playback started counted=${countPlay && !isCountedByService(source)} service=$usingService")
        if (countPlay && !isCountedByService(source)) callbacks.onSuccess(source)
    }

    private fun scheduleToleranceTimeout() {
        recoveryHandler.removeCallbacks(toleranceRunnable)
        val delay = if (readySincePrepare) {
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
        if (readySincePrepare) {
            noSignalVisible = true
            renderTitle()
        } else {
            stop()
            callbacks.onError(source, StreamFailureReason.UNREACHABLE)
        }
    }

    /**
     * STREAM-BANK 2.10 section 2.5: the service ended this station in failure and says why, so the dialog
     * opens now rather than at the tolerance timer. A broadcast row goes to the host's end handling (item U),
     * and a url other than the current station's is another item of the service.
     */
    private fun onServiceStreamFailure(reason: StreamFailureReason?, url: String) {
        val failed = currentSource?.takeIf { usingService && it.url == url } ?: return
        Timber.i("StreamInlineAudioManager: service ended the stream - %s (%s)", url, reason)
        stop()
        reportFailure(failed, reason)
    }

    private fun reportFailure(source: StreamSourceEntity, reason: StreamFailureReason?) {
        if (BroadcastPlaybackSessions.forUrl(source.url) != null) {
            callbacks.onBroadcastFailed(source, reason)
        } else {
            callbacks.onError(source, reason)
        }
    }

    /**
     * S4444: hands a broadcast row's stop to the host instead of the retry ladder, in both the in-app and the
     * service-owned mode; false for any other row, which keeps today's handling.
     */
    private fun stopForBroadcast(signal: BroadcastStopSignal): Boolean {
        val source = currentSource?.takeIf { BroadcastPlaybackSessions.forUrl(it.url) != null } ?: return false
        clearRecoveryState()
        callbacks.onBroadcastStopped(source, signal)
        return true
    }

    // The service path carries no cause chain across its controller, so the status is null there.
    private fun PlaybackException.responseCodeOrNull(): Int? = generateSequence<Throwable>(this) { it.cause }
        .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
        .firstOrNull()
        ?.responseCode

    private fun scheduleLocalRetry(delayMs: Long) {
        recoveryHandler.removeCallbacks(retryRunnable)
        recoveryHandler.postDelayed(retryRunnable, delayMs)
    }

    private fun retryLocalPlayback() {
        val local = localPlayer ?: return
        val mayReconnect = retryPolicy.mayReconnect(hasSuccessfulPlayback, SystemClock.elapsedRealtime())
        if (currentSource == null || !mayReconnect) {
            return
        }
        // Media3 ignores prepare() outside STATE_IDLE, and an ended player would resume at its end.
        if (local.playbackState == Player.STATE_ENDED) {
            local.stop()
            local.seekToDefaultPosition()
        }
        local.prepare()
        local.playWhenReady = true
    }

    private fun isLiveOrDurationUnknown(): Boolean {
        val active = player ?: return false
        return active.isCurrentMediaItemLive || active.duration == C.TIME_UNSET
    }

    /**
     * A live stream whose server closed the response cleanly ends here with no player error, so the
     * retry ladder never runs and the radio stops silently. The rule separates it from a track that
     * finished, which ends like a Stop; a reconnect is fed to the ladder as a lost connection and
     * answered exactly as [playerListener]'s error path answers it.
     *
     * A broadcast row has already left through [stopForBroadcast], so it is never offered to the rule.
     */
    private fun handleEndedStream() {
        val failed = currentSource ?: return
        val outcome = StreamEndedRule.decide(
            isBroadcast = false,
            hasPlayed = successReported,
            wasLiveOrDurationUnknown = liveWhilePlaying,
        )
        Timber.d("S4598: inline stream ended outcome=$outcome liveWhilePlaying=$liveWhilePlaying")
        if (outcome != StreamEndedOutcome.RECONNECT) {
            // A finished track has nothing left to pause or resume, so its bar and row would only advertise
            // audio that is gone. A source that never played keeps its tolerance timer, which answers it
            // with the failure dialog that stop() would cancel.
            Timber.d("S4750: finite track ended played=$successReported service=$usingService")
            if (successReported) stop()
            return
        }
        val decision = retryPolicy.onFailure(
            failure = StreamEndedRule.CONNECTION_LOST,
            hasEverPlayed = hasSuccessfulPlayback,
            usingService = usingService,
            nowMs = SystemClock.elapsedRealtime(),
        )
        Timber.d("StreamInlineAudioManager: live stream closed by server - %s", decision)
        when (decision) {
            is StreamInlineRetryDecision.RetryAfter -> scheduleLocalRetry(decision.delayMs)
            StreamInlineRetryDecision.OwnedByService -> Unit
            StreamInlineRetryDecision.GiveUp -> {
                stop()
                callbacks.onError(failed, StreamFailureReason.UNREACHABLE)
            }
        }
    }

    private fun clearRecoveryState() {
        recoveryHandler.removeCallbacks(toleranceRunnable)
        recoveryHandler.removeCallbacks(retryRunnable)
        retryPolicy.onCleared()
        noSignalVisible = false
    }
}
