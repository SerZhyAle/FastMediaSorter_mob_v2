package com.sza.fastmediasorter.ui.player

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.notification.NotificationIcons
import com.sza.fastmediasorter.core.playback.NowPlayingMetadata
import com.sza.fastmediasorter.core.playback.RadioStreamBufferConfig
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.domain.stats.ViewKind
import com.sza.fastmediasorter.domain.usecase.streams.RecordStreamPlayOutcomeUseCase
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.ui.player.helpers.AudioServiceController
import com.sza.fastmediasorter.ui.player.helpers.NetworkAwareMediaSourceFactory
import com.sza.fastmediasorter.ui.player.helpers.PositionSaveLoop
import com.sza.fastmediasorter.ui.player.helpers.createPlaybackRenderersFactory
import com.sza.fastmediasorter.widget.AudioNowPlayingSnapshotStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Audio-only background playback service using Media3 MediaSessionService.
 *
 * Scope: ONLY for audio files. Video playback is NOT affected -
 * video always goes through Activity ExoPlayer in VideoPlayerManager.
 *
 * This service creates its own ExoPlayer instance for audio playback.
 * When "Background playback" setting is OFF, audio plays through the existing
 * Activity ExoPlayer path (as before) and this service is not started.
 *
 * Lifecycle: starts on audio play (when background setting is ON),
 * survives Activity destruction, stops when playback ends or user stops.
 */
@AndroidEntryPoint
@UnstableApi
class AudioPlaybackService : MediaSessionService() {

    @Inject
    lateinit var playbackPositionRepository: PlaybackPositionRepository

    // S0473: usage-statistics sink. Fire-and-forget; no-ops when collection is disabled. Covers
    // background audio playback (this service is the audio path when background playback is ON).
    @Inject
    lateinit var statsSink: StatsSink

    // Lets the service stream SMB/SFTP/FTP/cloud URIs directly (no mandatory pre-cache to a local file),
    // so background-continue works for network audio the same way it does for local files.
    @Inject
    lateinit var networkMediaSourceFactory: NetworkAwareMediaSourceFactory

    @Inject
    lateinit var streamSourceRepository: StreamSourceRepository

    // S1536: the single writer of the StreamPlayed statistic, so the background path counts a radio
    // play exactly like the inline player does.
    @Inject
    lateinit var recordStreamPlayOutcome: RecordStreamPlayOutcomeUseCase

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    // S1142: last raw ICY StreamTitle pushed into the notification, to skip no-op updates (anti-flicker).
    private var lastIcyTitle: String? = null
    private val autoStopHandler = Handler(Looper.getMainLooper())
    private val autoStopRunnable = Runnable {
        val p = player
        if (p == null || (!p.isPlaying && p.playbackState != Player.STATE_BUFFERING && p.playbackState != Player.STATE_READY)) {
            Timber.d("AudioPlaybackService: auto-stop - no new track loaded within timeout")
            stopSelf()
        }
    }
    // Position persistence for SFTP/SMB/FTP/Cloud audio
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionSaveLoop: PositionSaveLoop? = null
    // Number of tracks skipped in a row due to skippable source errors; reset once a track actually
    // reaches STATE_READY. Bounds the skip loop under REPEAT_MODE_ALL (see S0413 research/03).
    private var consecutiveSkipCount = 0
    private var lastSkipToastElapsedMs = 0L
    private var streamConnectionStartedAtMs = 0L
    private var streamHasSuccessfulPlayback = false
    private var streamRetryAttempt = 0

    // S1536: the stream url whose play is already counted in this listening session, cleared on every
    // media-item transition. Deliberately not streamHasSuccessfulPlayback: that one is seeded from
    // lastPlayedAt, so it is already true for any station played before, and answers a different
    // question - whether reconnecting is allowed, not whether this session was counted.
    private var streamPlayCountedUrl: String? = null

    // Elapsed-time stamp of the first retry since the last successful READY; 0 = not retrying.
    // Bounds the reconnect loop for a stream that already played once (S1291).
    private var streamRetryStartedAtMs = 0L
    private val streamRetryRunnable = Runnable { retryCurrentStream() }

    // Live-radio stutters leave no trace at state level (playbackState stays READY, no error), so
    // every layer that can interrupt audible output is logged here: sink underruns, decoder churn,
    // mid-stream format changes, silent position jumps, focus-driven suppression/ducking, and every
    // upstream (re)open. Event-driven and cheap - kept permanently, unlike the S1148 telemetry loop.
    private val audioDiagListener = object : AnalyticsListener {
        override fun onAudioUnderrun(
            eventTime: AnalyticsListener.EventTime,
            bufferSize: Int,
            bufferSizeMs: Long,
            elapsedSinceLastFeedMs: Long
        ) {
            Timber.w(
                "AudioPlaybackService: audio underrun bufferSizeMs=%d elapsedSinceLastFeedMs=%d",
                bufferSizeMs,
                elapsedSinceLastFeedMs
            )
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            Timber.i("Audio diag: decoder initialized name=%s initMs=%d", decoderName, initializationDurationMs)
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            Timber.i(
                "Audio diag: input format mime=%s sampleRate=%d ch=%d bitrate=%d",
                format.sampleMimeType,
                format.sampleRate,
                format.channelCount,
                format.bitrate
            )
        }

        override fun onAudioSinkError(eventTime: AnalyticsListener.EventTime, audioSinkError: Exception) {
            Timber.w(audioSinkError, "Audio diag: sink error")
        }

        override fun onAudioCodecError(eventTime: AnalyticsListener.EventTime, audioCodecError: Exception) {
            Timber.w(audioCodecError, "Audio diag: codec error")
        }

        override fun onPositionDiscontinuity(
            eventTime: AnalyticsListener.EventTime,
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            Timber.i(
                "Audio diag: position discontinuity reason=%d old=%d new=%d",
                reason,
                oldPosition.positionMs,
                newPosition.positionMs
            )
        }

        override fun onPlayWhenReadyChanged(
            eventTime: AnalyticsListener.EventTime,
            playWhenReady: Boolean,
            reason: Int
        ) {
            // reason 3 = AUDIO_FOCUS_LOSS: something else took audio focus and paused this player.
            Timber.i("Audio diag: playWhenReady=%b reason=%d", playWhenReady, reason)
        }

        override fun onPlaybackSuppressionReasonChanged(
            eventTime: AnalyticsListener.EventTime,
            playbackSuppressionReason: Int
        ) {
            // reason 1 = TRANSIENT_AUDIO_FOCUS_LOSS: playback silently paused with state still READY.
            Timber.i("Audio diag: suppression reason=%d", playbackSuppressionReason)
        }

        override fun onIsPlayingChanged(eventTime: AnalyticsListener.EventTime, isPlaying: Boolean) {
            Timber.i("Audio diag: isPlaying=%b", isPlaying)
        }

        override fun onVolumeChanged(eventTime: AnalyticsListener.EventTime, volume: Float) {
            // A duck (transient focus loss CAN_DUCK) shows up only here - volume drop, no pause.
            Timber.i("Audio diag: volume=%.2f", volume)
        }

        override fun onLoadStarted(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData
        ) {
            // For a progressive ICY stream there is exactly ONE media load per connection - a second
            // line for the same station means the socket was silently reopened (upstream drop).
            if (mediaLoadData.dataType == C.DATA_TYPE_MEDIA) {
                Timber.i("Audio diag: media load started uri=%s", loadEventInfo.uri)
            }
        }

        override fun onLoadError(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
            error: java.io.IOException,
            wasCanceled: Boolean
        ) {
            Timber.w(error, "Audio diag: load error canceled=%b uri=%s", wasCanceled, loadEventInfo.uri)
        }
    }

    // Route flapping is invisible to ExoPlayer - only this callback sees it.
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            Timber.i("Audio diag: output devices added %s", describeDevices(addedDevices))
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            Timber.i("Audio diag: output devices removed %s", describeDevices(removedDevices))
        }
    }

    private fun describeDevices(devices: Array<AudioDeviceInfo>): String =
        devices.filter { it.isSink }
            .joinToString(prefix = "[", postfix = "]") { "type=${it.type} name=${it.productName}" }

    // "Player time" accounting: the service lifetime is the audio-open window, so wall-clock accrues
    // from the first ready track until the service is destroyed, including paused/buffering. A periodic
    // bank caps the loss to one interval if the process is killed mid-session (e.g. swipe-away). 0 = off.
    private var listenClockStartMs = 0L
    private val listenBankHandler = Handler(Looper.getMainLooper())
    private val listenBankRunnable = object : Runnable {
        override fun run() {
            bankListenTime()
            listenBankHandler.postDelayed(this, LISTEN_BANK_INTERVAL_MS)
        }
    }

    companion object {
        private const val AUTO_STOP_DELAY_MS = 10_000L
        /** How often the accrued audio player time is banked, bounding loss on process kill. */
        private const val LISTEN_BANK_INTERVAL_MS = 30_000L
        /** Suppress repeat skip toasts within this window so a run of bad files does not spam (S0413). */
        private const val SKIP_TOAST_DEBOUNCE_MS = 3_000L
        private const val MAX_STREAM_BACKOFF_SHIFT = 2

        /**
         * S1291: how long reconnect attempts may continue after a stream that already played once
         * stops being reachable. Without this cap the retry loop (<= 8 s apart) runs until the
         * process dies, keeping a foreground service with a wake mode alive all night.
         */
        private const val MAX_STREAM_RETRY_WINDOW_MS = 5 * 60 * 1000L
        private const val STREAM_OUTCOME_OK = "OK"
        /** Matches VideoPlayerManager.POSITION_SAVE_INTERVAL_MS (15 s). */
        private const val POSITION_SAVE_INTERVAL_MS = 15_000L
        const val ACTION_WIDGET_COMMAND = "com.sza.fastmediasorter.action.AUDIO_WIDGET_COMMAND"
        const val EXTRA_WIDGET_COMMAND = "extra_widget_command"
        const val WIDGET_COMMAND_PLAY_PAUSE = "play_pause"
        const val WIDGET_COMMAND_NEXT = "next"
        const val WIDGET_COMMAND_PREVIOUS = "previous"
        const val WIDGET_COMMAND_FAVORITE_REFRESH = "favorite_refresh"

        @Volatile
        var isRunning: Boolean = false

        /** Direction for the next navigation event triggered via hardware media buttons.
         *  Set by ForwardingPlayer when the user presses NEXT or PREVIOUS.
         *  Read (and reset) by PlayerActivity.onAudioServicePlaybackEnded. */
        const val DIRECTION_NEXT = 1
        const val DIRECTION_PREV = -1
        @Volatile
        var pendingDirection: Int = DIRECTION_NEXT

        /** Resource/playlist context of the currently playing audio.
         *  Set by PlayerMediaLoaderManager before starting playback so the notification
         *  contentIntent (sessionActivity) can navigate back to the exact player screen.
         *  -1L means unknown (e.g. legacy single-file mode without resource context). */
        @Volatile
        var currentResourceId: Long = -1L
        @Volatile
        var currentInitialIndex: Int = 0

        /** Original network path (sftp:// / smb:// / ftp://) of the currently playing file.
         *  Set by PlayerMediaLoaderManager before starting playback so position can be
         *  saved/restored using a stable, cache-path-independent key (ADR-2, S0172).
         *  Empty string = local file (position handled by VideoPlayerManager). */
        @Volatile
        var currentOriginalPath: String = ""
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Timber.d("AudioPlaybackService: onCreate")

        // Create notification channel (required for Android 8+)
        MediaNotificationManager.createNotificationChannel(this)

        // S0172: call startForeground immediately so the OS 5-second deadline never fires on
        // cold start (e.g. car media-button restart with no track loaded yet).
        // Media3 DefaultMediaNotificationProvider will replace this placeholder with the real
        // media notification once a MediaSession + track are established.
        Timber.d("S1399: audio-playback cold-start placeholder notification built with the branded status-bar icon")
        val placeholderNotification = NotificationCompat
            .Builder(this, MediaNotificationManager.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("")
            .setSilent(true)
            .build()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                MediaNotificationManager.NOTIFICATION_ID,
                placeholderNotification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(MediaNotificationManager.NOTIFICATION_ID, placeholderNotification)
        }

        setMediaNotificationProvider(
            MediaNotificationManager.createNotificationProvider(this)
        )

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val wakeMode = if (checkSelfPermission(android.Manifest.permission.WAKE_LOCK)
            == PackageManager.PERMISSION_GRANTED
        ) C.WAKE_MODE_LOCAL else C.WAKE_MODE_NONE

        // Smart-mode loader policy: silent connectivity retries instead of fatal restarts.
        // The policy checks the setting at error time, so it is safe to attach unconditionally.
        networkMediaSourceFactory.setLoadErrorHandlingPolicy(
            RadioStreamBufferConfig.createLoadErrorHandlingPolicy(this)
        )
        val exoPlayer = ExoPlayer.Builder(this, createPlaybackRenderersFactory(this))
            .setMediaSourceFactory(networkMediaSourceFactory)
            .setLoadControl(RadioStreamBufferConfig.createLoadControl(this))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(wakeMode)
            .build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                Timber.d("AudioPlaybackService: playbackState=$playbackState")
                when (playbackState) {
                    Player.STATE_ENDED -> {
                        // Don't stopSelf immediately - give Activity time to load next track.
                        // If no new track starts within AUTO_STOP_DELAY_MS, stop the service.
                        Timber.d("AudioPlaybackService: playback ended, scheduling auto-stop in ${AUTO_STOP_DELAY_MS}ms")
                        // S0473: one audio track listened to the end. Listen time is accrued separately
                        // via the listen clock (StatsEvent.PlaybackTime), so this only bumps the count and
                        // must not also report a duration or the player time would be double-counted.
                        statsSink.record(StatsEvent.View(ViewKind.AUDIO))
                        // S0172: stop save loop and persist final position before track ends
                        stopPositionSaving()
                        saveCurrentPosition()
                        AudioNowPlayingSnapshotStore.clear(this@AudioPlaybackService)
                        autoStopHandler.removeCallbacks(autoStopRunnable)
                        autoStopHandler.postDelayed(autoStopRunnable, AUTO_STOP_DELAY_MS)
                    }
                    Player.STATE_READY -> {
                        // New track loaded - cancel auto-stop, start position save loop
                        consecutiveSkipCount = 0
                        startListenClock()
                        autoStopHandler.removeCallbacks(autoStopRunnable)
                        // S0172: begin periodic save once player is ready
                        startPositionSaving()
                        publishWidgetSnapshot()
                        recordCurrentStreamSuccess()
                    }
                    Player.STATE_BUFFERING -> {
                        // New track loading - cancel auto-stop; save loop starts on STATE_READY
                        autoStopHandler.removeCallbacks(autoStopRunnable)
                        publishWidgetSnapshot()
                    }
                    Player.STATE_IDLE -> {
                        stopPositionSaving()
                        AudioNowPlayingSnapshotStore.clear(this@AudioPlaybackService)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    // S0172: persist position on pause so it survives a kill
                    saveCurrentPosition()
                }
                publishWidgetSnapshot()
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                publishWidgetSnapshot()
            }

            // S1142: live ICY now-playing. The service ExoPlayer is where the raw stream metadata
            // arrives (a MediaController does not receive it); push the parsed track into the current
            // MediaItem so the system notification / lock screen reflect it (research 02).
            override fun onMetadata(metadata: Metadata) {
                for (i in 0 until metadata.length()) {
                    val entry = metadata.get(i)
                    if (entry is IcyInfo) {
                        entry.title?.takeIf { it.isNotBlank() }?.let { applyLiveIcyMetadata(it) }
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // S1142: a new station may legitimately repeat a previous track string - forget the last.
                lastIcyTitle = null
                resetStreamRecovery(mediaItem)
                publishWidgetSnapshot()
            }

            override fun onPlayerError(error: PlaybackException) {
                autoStopHandler.removeCallbacks(autoStopRunnable)
                val p = player
                if (p != null && canRetryStream(error)) {
                    scheduleStreamRetry()
                    return
                }
                if (p != null && error.isSkippable() && p.hasNextMediaItem()
                    && consecutiveSkipCount < p.mediaItemCount
                ) {
                    // Per-file parsing/decoding error in a playlist: skip the bad track and continue.
                    consecutiveSkipCount++
                    Timber.w(error, "AudioPlaybackService: skippable source error - advancing to next track")
                    showSkipMessage(currentItemDisplayName(p))
                    p.seekToNextMediaItem()
                    p.prepare()
                    return
                }
                // Fatal error, single file, or the whole queue failed in a row. A cache eviction
                // (FILE_NOT_FOUND, 2xxx) lands here on purpose so a genuine session failure is not masked.
                if (p != null && error.isSkippable() && p.mediaItemCount > 1
                    && consecutiveSkipCount >= p.mediaItemCount
                ) {
                    Toast.makeText(
                        this@AudioPlaybackService,
                        getString(R.string.s0413_audio_queue_unplayable),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                Timber.e(error, "AudioPlaybackService: fatal playback error - stopping service")
                AudioNowPlayingSnapshotStore.clear(this@AudioPlaybackService)
                stopSelf()
            }
        })

        exoPlayer.addAnalyticsListener(audioDiagListener)
        (getSystemService(AUDIO_SERVICE) as? AudioManager)
            ?.registerAudioDeviceCallback(audioDeviceCallback, autoStopHandler)

        player = exoPlayer

        // Wrap player to handle next/previous for single-file playback.
        // When ExoPlayer has only 1 item, seekToNext/Previous are no-ops.
        // ForwardingPlayer seeks to end so STATE_ENDED fires and Activity-side callback advances.
        val wrappedPlayer = object : ForwardingPlayer(exoPlayer) {
            // S1146: a live/unbounded radio stream (ICY .aacp, HLS/DASH live) has no next/previous
            // track and is not seekable. The single-file seek-to-end trick below only rebuffers the
            // live edge (an audible stutter) - STATE_ENDED never fires for live - so skip is a no-op
            // for it, and skip commands are not advertised. isCurrentMediaItemLive can be false for a
            // plain ICY progressive stream, so a non-seekable current item is treated as live too.
            fun isLiveStreamItem(): Boolean =
                exoPlayer.isCurrentMediaItemLive || !exoPlayer.isCurrentMediaItemSeekable

            override fun seekToNext() {
                if (isLiveStreamItem()) {
                    Timber.d("S1146: seekToNext no-op for live stream")
                    return
                }
                if (exoPlayer.mediaItemCount <= 1) {
                    Timber.d("AudioPlaybackService: seekToNext on single file → seeking to end")
                    // Use actual duration when known; fall back to a safe large value so ExoPlayer
                    // clamps to end and fires STATE_ENDED (duration may be C.TIME_UNSET early in playback)
                    val target = exoPlayer.duration.takeIf { it > 0 } ?: Int.MAX_VALUE.toLong()
                    exoPlayer.seekTo(target)
                } else {
                    super.seekToNext()
                }
            }

            override fun seekToPrevious() {
                if (isLiveStreamItem()) {
                    Timber.d("S1146: seekToPrevious no-op for live stream")
                    return
                }
                if (exoPlayer.mediaItemCount <= 1) {
                    if (exoPlayer.currentPosition <= 3000L) {
                        // Near the start: go to previous file - signal Activity via pendingDirection
                        Timber.d("AudioPlaybackService: seekToPrevious near start → pendingDirection=PREV, seeking to end")
                        pendingDirection = DIRECTION_PREV
                        val target = exoPlayer.duration.takeIf { it > 0 } ?: Int.MAX_VALUE.toLong()
                        exoPlayer.seekTo(target)
                    } else {
                        // Further into track: just restart from beginning (standard media-player convention)
                        Timber.d("AudioPlaybackService: seekToPrevious mid-track → restart from beginning")
                        exoPlayer.seekTo(0)
                    }
                } else {
                    super.seekToPrevious()
                }
            }

            // Report SEEK_TO_NEXT / SEEK_TO_PREVIOUS as available for single-file so the notification
            // renders both skip buttons - but NOT for a live radio stream (S1146), where skipping only
            // rebuffers the live edge; there it falls through to ExoPlayer's own (false) availability.
            override fun isCommandAvailable(command: @Player.Command Int): Boolean {
                if (command == COMMAND_SEEK_TO_NEXT || command == COMMAND_SEEK_TO_PREVIOUS) {
                    return if (isLiveStreamItem()) super.isCommandAvailable(command) else true
                }
                return super.isCommandAvailable(command)
            }

            override fun getAvailableCommands(): Player.Commands {
                val base = super.getAvailableCommands()
                if (isLiveStreamItem()) return base
                return base.buildUpon()
                    .add(COMMAND_SEEK_TO_NEXT)
                    .add(COMMAND_SEEK_TO_PREVIOUS)
                    .build()
            }

        }

        // Phase 5: MediaButtonRestartReceiver is registered in the manifest with android:priority=1000.
        // The OS delivers MEDIA_BUTTON intents to that receiver when this session is inactive (service dead).
        // Media3 1.2.1 MediaSession.Builder does not expose setMediaButtonReceiver() - cold-restart
        // is handled entirely by the manifest BroadcastReceiver + PackageManager component toggling.
        //
        // sessionActivity: tapping the notification body (not media buttons) opens the app
        // and routes to PlayerActivity via MainActivity.ACTION_RESUME_PLAYER.
        // MainActivity reads currentResourceId/currentInitialIndex (set before playback starts)
        // to reopen the exact player screen, even if the app process was killed.
        val resumeIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_RESUME_PLAYER
            // FLAG_ACTIVITY_SINGLE_TOP: if MainActivity is already on top, deliver via onNewIntent
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val resumePendingIntent = PendingIntent.getActivity(
            this,
            0,
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, wrappedPlayer)
            .setCallback(AudioSessionCallback())
            .setSessionActivity(resumePendingIntent)
            .build()

        Timber.d("AudioPlaybackService: MediaSession created")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_WIDGET_COMMAND) {
            handleWidgetCommand(intent.getStringExtra(EXTRA_WIDGET_COMMAND))
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = mediaSession?.player
        if (currentPlayer == null || !currentPlayer.playWhenReady
            || currentPlayer.mediaItemCount == 0
            || currentPlayer.playbackState == Player.STATE_ENDED
        ) {
            Timber.d("AudioPlaybackService: task removed, no active playback - stopping")
            stopSelf()
        }
        // If still playing, let the service continue in background
    }

    override fun onDestroy() {
        Timber.d("AudioPlaybackService: onDestroy")
        isRunning = false
        stopListenClock()
        autoStopHandler.removeCallbacks(autoStopRunnable)
        autoStopHandler.removeCallbacks(streamRetryRunnable)

        // Capture position before player is released, then stop the save loop
        val p = player
        val path = currentOriginalPath.takeIf { it.isNotEmpty() }
        val finalPos = p?.currentPosition ?: -1L
        val finalDur = p?.duration ?: -1L
        stopPositionSaving()
        if (path != null && finalDur > 0 && finalPos >= 0) {
            // S0895: serviceScope.cancel() runs on the very next statement below, which would
            // cancel a coroutine launched on serviceScope before the IO dispatcher even starts it -
            // the destroy-edge save was dead in practice. A dedicated scope (not GlobalScope - Rule
            // 19) survives that cancel(); it cancels itself once the write completes or fails.
            val finalSaveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            finalSaveScope.launch {
                try {
                    playbackPositionRepository.savePosition(path, finalPos, finalDur)
                } catch (e: Exception) {
                    Timber.e(e, "AudioPlaybackService: onDestroy save position failed")
                } finally {
                    finalSaveScope.cancel()
                }
            }
        }
        serviceScope.cancel()
        AudioNowPlayingSnapshotStore.clear(this)

        (getSystemService(AUDIO_SERVICE) as? AudioManager)
            ?.unregisterAudioDeviceCallback(audioDeviceCallback)
        p?.removeAnalyticsListener(audioDiagListener)
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }

    // ─── Position save/restore helpers ──────────────────────────────────────

    private fun startPositionSaving() {
        val p = player ?: return
        // S1291 (mirrors the S0854 fix in PlaybackPositionHelper): STATE_READY is re-entered on every
        // seek and rebuffer, so overwriting the field without stopping the previous loop leaves its
        // Handler runnable self-reposting forever, retaining the released player.
        positionSaveLoop?.stop()
        positionSaveLoop = PositionSaveLoop(
            intervalMs = POSITION_SAVE_INTERVAL_MS,
            getPath = { currentOriginalPath.takeIf { it.isNotEmpty() } },
            getPositionMs = { p.currentPosition },
            getDurationMs = { p.duration },
            scope = serviceScope,
            onSave = { path, pos, dur -> playbackPositionRepository.savePosition(path, pos, dur) }
        )
        positionSaveLoop!!.start()
        Timber.d("AudioPlaybackService: position auto-save started for path=$currentOriginalPath")
    }

    private fun stopPositionSaving() {
        positionSaveLoop?.stop()
        positionSaveLoop = null
        Timber.d("AudioPlaybackService: position auto-save stopped")
    }

    private fun saveCurrentPosition() {
        positionSaveLoop?.saveNow()
    }

    // ─── Player-time accounting (S0473 follow-up) ───────────────────────────

    /** Start the listen clock and the periodic bank loop on the first ready track. */
    private fun startListenClock() {
        if (listenClockStartMs != 0L) return
        listenClockStartMs = SystemClock.elapsedRealtime()
        listenBankHandler.postDelayed(listenBankRunnable, LISTEN_BANK_INTERVAL_MS)
    }

    /** Bank elapsed audio player time as a PlaybackTime delta and keep the clock running. */
    private fun bankListenTime() {
        val start = listenClockStartMs
        if (start == 0L) return
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - start
        if (elapsed <= 0L) return
        listenClockStartMs = now
        statsSink.record(StatsEvent.PlaybackTime(ViewKind.AUDIO, elapsed))
    }

    /** Bank the final partial interval and stop the clock; called on service destroy. */
    private fun stopListenClock() {
        listenBankHandler.removeCallbacks(listenBankRunnable)
        bankListenTime()
        listenClockStartMs = 0L
    }

    // Parsing (3xxx) and decoding (4xxx) errors are intrinsic to a single file's bytes/format, so the
    // track can be skipped. IO/runtime errors (e.g. evicted cache -> FILE_NOT_FOUND) may affect the whole
    // session and keep the stop behavior. See S0413 research/01.
    private fun PlaybackException.isSkippable(): Boolean = errorCode in 3000..4999

    private fun canRetryStream(error: PlaybackException): Boolean {
        val ioErrorRange =
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED..PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
        val withinRetryWindow = streamHasSuccessfulPlayback ||
            SystemClock.elapsedRealtime() - streamConnectionStartedAtMs <
            RadioStreamBufferConfig.DIALOG_TIMEOUT_MS
        return error.errorCode in ioErrorRange && withinRetryWindow
    }

    private fun scheduleStreamRetry() {
        val delay = (RadioStreamBufferConfig.BASE_RETRY_DELAY_MS shl streamRetryAttempt)
            .coerceAtMost(RadioStreamBufferConfig.MAX_RETRY_DELAY_MS)
        streamRetryAttempt = (streamRetryAttempt + 1).coerceAtMost(MAX_STREAM_BACKOFF_SHIFT)
        if (streamRetryStartedAtMs == 0L) streamRetryStartedAtMs = SystemClock.elapsedRealtime()
        autoStopHandler.removeCallbacks(streamRetryRunnable)
        autoStopHandler.postDelayed(streamRetryRunnable, delay)
    }

    private fun retryCurrentStream() {
        val currentPlayer = player ?: return
        when {
            // S1291: the user paused while the retry was pending - reconnecting would resurrect
            // playback behind their back (and re-arm playWhenReady, which also blocks swipe-away).
            !currentPlayer.playWhenReady -> {
                Timber.d("AudioPlaybackService: stream retry dropped - playback paused")
                streamRetryStartedAtMs = 0L
            }
            giveUpStreamRetry() -> {
                Timber.w("AudioPlaybackService: stream unreachable - stopping service instead of retrying")
                AudioNowPlayingSnapshotStore.clear(this)
                stopSelf()
            }
            else -> {
                currentPlayer.prepare()
                currentPlayer.playWhenReady = true
            }
        }
    }

    /**
     * S1291: a source that never played gets the short connect window; one that already played gets
     * a bounded reconnect window instead of the former unlimited loop.
     */
    private fun giveUpStreamRetry(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (!streamHasSuccessfulPlayback) {
            return now - streamConnectionStartedAtMs >= RadioStreamBufferConfig.DIALOG_TIMEOUT_MS
        }
        return streamRetryStartedAtMs != 0L && now - streamRetryStartedAtMs >= MAX_STREAM_RETRY_WINDOW_MS
    }

    private fun resetStreamRecovery(mediaItem: MediaItem?) {
        autoStopHandler.removeCallbacks(streamRetryRunnable)
        streamConnectionStartedAtMs = SystemClock.elapsedRealtime()
        streamHasSuccessfulPlayback = false
        streamRetryAttempt = 0
        streamRetryStartedAtMs = 0L
        streamPlayCountedUrl = null
        val streamUrl = mediaItem?.localConfiguration?.uri?.toString() ?: return
        serviceScope.launch {
            streamHasSuccessfulPlayback = streamSourceRepository.getByUrl(streamUrl)?.lastPlayedAt != null
        }
    }

    /**
     * S1142: push the live ICY track into the current MediaItem's metadata so the system notification
     * and lock screen show it. Mirrors the video path (StreamPlaybackHelper.updateNowPlayingTitle):
     * media3 treats a same-URI replaceMediaItem as a metadata-only update, so buffering/recovery are
     * untouched. The static station name is carried into the `station` field so it is not lost. Skips
     * a repeated title (anti-flicker) and swallows a malformed-timeline IllegalStateException.
     */
    private fun applyLiveIcyMetadata(rawIcyTitle: String) {
        if (rawIcyTitle == lastIcyTitle) return
        val p = player
        val current = p?.currentMediaItem
        val parsed = NowPlayingMetadata.parse(rawIcyTitle)
        // One combined guard instead of a chain of early returns (detekt ReturnCount).
        if (parsed == null || p == null || current == null) return
        val index = p.currentMediaItemIndex
        // Preserve the station across successive updates: after the first replace the item title holds
        // the song, so read the already-stored station field first and fall back to the initial title.
        val station = current.mediaMetadata.station ?: current.mediaMetadata.title
        val updated = current.buildUpon()
            .setMediaMetadata(
                current.mediaMetadata.buildUpon()
                    .setTitle(parsed.title)
                    .setArtist(parsed.artist)
                    .setStation(station)
                    .build()
            )
            .build()
        try {
            p.replaceMediaItem(index, updated)
            lastIcyTitle = rawIcyTitle
            Timber.d("S1142: live ICY pushed, artist=${parsed.artist} title=${parsed.title}")
        } catch (e: IllegalStateException) {
            Timber.w(e, "AudioPlaybackService: live ICY metadata update skipped")
        }
    }

    private fun recordCurrentStreamSuccess() {
        val streamUrl = player?.currentMediaItem?.localConfiguration?.uri?.toString() ?: return
        streamRetryAttempt = 0
        streamRetryStartedAtMs = 0L
        // S1536: READY repeats after every rebuffer and reconnect, which for a live stream is routine,
        // so the play counter fires on the first READY of this listening session only. The bullet and
        // the last-played stamp still refresh on every READY, as they did before.
        val firstReady = streamPlayCountedUrl != streamUrl
        streamPlayCountedUrl = streamUrl
        Timber.d("S1536: stream READY firstReady=%s url=%s", firstReady, streamUrl)
        serviceScope.launch {
            val source = streamSourceRepository.getByUrl(streamUrl) ?: return@launch
            // S1291: only a catalog stream earns the reconnect window. Setting this for every media
            // item made an ordinary file (e.g. FILE_NOT_FOUND after cache eviction) retry forever
            // instead of taking the fatal-error path that stops the service.
            streamHasSuccessfulPlayback = true
            if (firstReady) {
                // S1536: the use case owns the StreamPlayed statistic; going to the repository direct
                // is what left background radio out of the "streams played" count entirely.
                recordStreamPlayOutcome.recordPlaySuccess(source.id)
            } else {
                streamSourceRepository.recordPlayOutcome(source.id, STREAM_OUTCOME_OK)
                streamSourceRepository.markPlayed(source.id, System.currentTimeMillis())
            }
        }
    }

    private fun currentItemDisplayName(p: Player): String {
        val item = p.currentMediaItem
        val title = p.mediaMetadata.title?.toString()
            ?: item?.mediaMetadata?.title?.toString()
        if (!title.isNullOrBlank()) return title
        val uri = item?.localConfiguration?.uri?.toString().orEmpty()
        return uri.substringAfterLast('/').substringBeforeLast('.')
    }

    private fun showSkipMessage(fileName: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSkipToastElapsedMs < SKIP_TOAST_DEBOUNCE_MS) return
        lastSkipToastElapsedMs = now
        autoStopHandler.post {
            Toast.makeText(
                this,
                getString(R.string.s0413_audio_track_skipped, fileName),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ────────────────────────────────────────────────────────────────────────

    /**
     * R2 bridge: single entry point for all command-driven playback actions.     *
     * Routes any [com.sza.fastmediasorter.domain.input.CommandId] value to the
     * corresponding player operation. Called by Phase 06 settings UI (binding test)
     * and externally triggered media actions so every entry point shares one code path.
     */
    fun dispatchCommand(commandId: String) {
        val p = player ?: return
        when (commandId) {
            "playback.pause_play" -> if (p.isPlaying) p.pause() else p.play()
            "playback.play"       -> p.play()
            "playback.pause"      -> p.pause()
            "playback.stop"       -> { p.pause(); p.seekTo(0) }
            "navigation.next_file"     -> p.seekToNext()
            "navigation.previous_file" -> p.seekToPrevious()
            else -> Timber.d("AudioPlaybackService: dispatchCommand ignored commandId=%s", commandId)
        }
        publishWidgetSnapshot()
    }

    private fun handleWidgetCommand(command: String?) {
        when (command) {
            WIDGET_COMMAND_PLAY_PAUSE -> dispatchCommand("playback.pause_play")
            WIDGET_COMMAND_NEXT -> dispatchCommand("navigation.next_file")
            WIDGET_COMMAND_PREVIOUS -> dispatchCommand("navigation.previous_file")
            WIDGET_COMMAND_FAVORITE_REFRESH -> publishWidgetSnapshot()
            else -> Timber.d("AudioPlaybackService: widget command ignored command=%s", command)
        }
    }

    private fun publishWidgetSnapshot() {
        val p = player ?: run {
            AudioNowPlayingSnapshotStore.clear(this)
            return
        }
        val active = p.playbackState == Player.STATE_READY || p.playbackState == Player.STATE_BUFFERING
        if (!active) {
            AudioNowPlayingSnapshotStore.clear(this)
            return
        }

        val item = p.currentMediaItem
        val metadata = p.mediaMetadata
        val itemMetadata = item?.mediaMetadata
        val extras = itemMetadata?.extras ?: metadata.extras
        val sourcePath = extras?.getString(AudioServiceController.EXTRA_SOURCE_PATH).orEmpty()
        val mediaUri = sourcePath.ifBlank {
            currentOriginalPath.ifBlank {
                item?.localConfiguration?.uri?.toString().orEmpty()
            }
        }
        val previous = AudioNowPlayingSnapshotStore.read(this)
        val title = metadata.title?.toString()
            ?: itemMetadata?.title?.toString()
            ?: mediaUri.substringAfterLast('/').substringBeforeLast('.')
        val artist = metadata.artist?.toString()
            ?: itemMetadata?.artist?.toString()
            ?: ""

        AudioNowPlayingSnapshotStore.write(
            this,
            AudioNowPlayingSnapshotStore.Snapshot(
                active = true,
                title = title,
                artist = artist,
                artworkUri = (metadata.artworkUri ?: itemMetadata?.artworkUri)?.toString().orEmpty(),
                isPlaying = p.isPlaying,
                mediaUri = mediaUri,
                resourceId = extras?.getLong(AudioServiceController.EXTRA_RESOURCE_ID, currentResourceId)
                    ?: currentResourceId,
                size = extras?.getLong(AudioServiceController.EXTRA_SIZE, 0L) ?: 0L,
                dateModified = extras?.getLong(AudioServiceController.EXTRA_DATE_MODIFIED, 0L) ?: 0L,
                isFavorite = previous.mediaUri == mediaUri && previous.isFavorite
            )
        )
    }

    /**
     * Prepares and starts playback of an audio file.
     * Called internally when the service is started with a media URI.
     */
    fun playAudio(uri: Uri) {
        val currentPlayer = player ?: return
        Timber.d("AudioPlaybackService: playAudio uri=$uri")
        val mediaItem = MediaItem.fromUri(uri)
        currentPlayer.setMediaItem(mediaItem)
        currentPlayer.repeatMode = Player.REPEAT_MODE_OFF  // Play once and stop
        currentPlayer.prepare()
        currentPlayer.play()
    }

    /**
     * Prepares a playlist of audio files and starts from given index.
     */
    fun playAudioPlaylist(uris: List<Uri>, startIndex: Int = 0) {
        val currentPlayer = player ?: return
        Timber.d("AudioPlaybackService: playAudioPlaylist size=${uris.size}, startIndex=$startIndex")
        val mediaItems = uris.map { MediaItem.fromUri(it) }
        currentPlayer.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
        currentPlayer.repeatMode = Player.REPEAT_MODE_ALL  // Loop audio playlist
        currentPlayer.prepare()
        currentPlayer.play()
    }

    /**
     * MediaSession callback for handling controller requests and media button events.
     * Standard player commands (play, pause, seekToNext, seekToPrevious) are routed
     * directly to the ExoPlayer by Media3 - no override needed.
     * This callback ensures all standard commands are available and logs media events.
     */
    private inner class AudioSessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ConnectionResult {
            Timber.d("AudioPlaybackService: MediaSession onConnect from ${controller.packageName}")
            // Explicitly include SEEK_TO_NEXT/PREVIOUS so notification always shows skip buttons
            // even for single-file playback (Activity handles the actual advance).
            val playerCommands = ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .build()
            return ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(playerCommands)
                .setAvailableSessionCommands(ConnectionResult.DEFAULT_SESSION_COMMANDS)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: android.os.Bundle
        ): ListenableFuture<SessionResult> {
            Timber.d("AudioPlaybackService: onCustomCommand action=${customCommand.customAction}")
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        Timber.d("AudioPlaybackService: onBind")
        return super.onBind(intent)
    }
}
