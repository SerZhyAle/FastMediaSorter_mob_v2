package com.sza.fastmediasorter.ui.player

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import com.sza.fastmediasorter.ui.main.MainActivity
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.domain.stats.ViewKind
import dagger.hilt.android.AndroidEntryPoint
import com.sza.fastmediasorter.ui.player.helpers.PositionSaveLoop
import com.sza.fastmediasorter.ui.player.helpers.AudioServiceController
import com.sza.fastmediasorter.widget.AudioNowPlayingSnapshotStore
import com.sza.fastmediasorter.ui.player.helpers.createPlaybackRenderersFactory
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

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
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
    companion object {
        private const val AUTO_STOP_DELAY_MS = 10_000L
        /** Suppress repeat skip toasts within this window so a run of bad files does not spam (S0413). */
        private const val SKIP_TOAST_DEBOUNCE_MS = 3_000L
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
        val placeholderNotification = NotificationCompat
            .Builder(this, MediaNotificationManager.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_audio)
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

        val exoPlayer = ExoPlayer.Builder(this, createPlaybackRenderersFactory(this))
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
                        // S0473: one audio track listened to the end. duration==listened time at
                        // STATE_ENDED; C.TIME_UNSET (early/unknown duration) is reported as 0.
                        val listenedMs = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
                        statsSink.record(StatsEvent.View(ViewKind.AUDIO, durationMs = listenedMs))
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
                        autoStopHandler.removeCallbacks(autoStopRunnable)
                        // S0172: begin periodic save once player is ready
                        startPositionSaving()
                        publishWidgetSnapshot()
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

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                publishWidgetSnapshot()
            }

            override fun onPlayerError(error: PlaybackException) {
                autoStopHandler.removeCallbacks(autoStopRunnable)
                val p = player
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

        player = exoPlayer

        // Wrap player to handle next/previous for single-file playback.
        // When ExoPlayer has only 1 item, seekToNext/Previous are no-ops.
        // ForwardingPlayer seeks to end so STATE_ENDED fires and Activity-side callback advances.
        val wrappedPlayer = object : ForwardingPlayer(exoPlayer) {
            override fun seekToNext() {
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

            // Always report SEEK_TO_NEXT / SEEK_TO_PREVIOUS as available so that
            // DefaultMediaNotificationProvider renders both skip buttons in the notification,
            // even for single-file playback where ExoPlayer would otherwise return false.
            override fun isCommandAvailable(command: @Player.Command Int): Boolean {
                if (command == COMMAND_SEEK_TO_NEXT || command == COMMAND_SEEK_TO_PREVIOUS) return true
                return super.isCommandAvailable(command)
            }

            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
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
        autoStopHandler.removeCallbacks(autoStopRunnable)

        // Capture position before player is released, then stop the save loop
        val p = player
        val path = currentOriginalPath.takeIf { it.isNotEmpty() }
        val finalPos = p?.currentPosition ?: -1L
        val finalDur = p?.duration ?: -1L
        stopPositionSaving()
        if (path != null && finalDur > 0 && finalPos >= 0) {
            serviceScope.launch(Dispatchers.IO) {
                try {
                    playbackPositionRepository.savePosition(path, finalPos, finalDur)
                } catch (e: Exception) {
                    Timber.e(e, "AudioPlaybackService: onDestroy save position failed")
                }
            }
        }
        serviceScope.cancel()
        AudioNowPlayingSnapshotStore.clear(this)

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

    // Parsing (3xxx) and decoding (4xxx) errors are intrinsic to a single file's bytes/format, so the
    // track can be skipped. IO/runtime errors (e.g. evicted cache -> FILE_NOT_FOUND) may affect the whole
    // session and keep the stop behavior. See S0413 research/01.
    private fun PlaybackException.isSkippable(): Boolean = errorCode in 3000..4999

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
