package com.sza.fastmediasorter.wear.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.core.notification.NotificationIcons
import com.sza.fastmediasorter.wear.core.notification.WearNotificationIds
import com.sza.fastmediasorter.wear.domain.playback.WearBackgroundPlaybackPolicy
import com.sza.fastmediasorter.wear.domain.playback.WearBackgroundSession
import com.sza.fastmediasorter.wear.domain.playback.WearBackgroundSessionState
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.repository.WearNowPlayingRepository
import com.sza.fastmediasorter.wear.domain.usecase.PublishPlaybackStateUseCase
import com.sza.fastmediasorter.wear.service.helpers.WearPlaybackSetPlayer
import com.sza.fastmediasorter.wear.ui.player.common.PlaybackProgressTicker
import com.sza.fastmediasorter.wear.ui.player.common.wearPlaybackStatePayload
import com.sza.fastmediasorter.wear.ui.player.helpers.StreamPlaybackSessionFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** The typed startForeground overload does not exist below API 29; there the type is simply absent. */
private const val FOREGROUND_SERVICE_TYPE_NONE = 0

/**
 * S2166: owner of the watch's playback session (ADR-2).
 *
 * The player screen starts and stops this service by intent and never binds to it, so minimizing the
 * app cannot end playback that is still audible - a ViewModel-owned player is destroyed by both
 * gestures the owner distinguishes, and only one of them is meant to stop the sound.
 *
 * The player here is this service's own and has exactly one owner, which is what separates it from
 * the process-lived singleton S0725 rejected: that one was shared between two screens and neither
 * could safely release it. This one is released in [onDestroy].
 *
 * Notification content and the session's controls are Media3's, not hand-built (ADR-3) - only the
 * channel and the notification id are ours, the id coming from [WearNotificationIds] so the choice is
 * visible beside every other notification this module can post.
 */
@AndroidEntryPoint
class WearPlaybackService : MediaSessionService() {

    @Inject
    lateinit var streamPlaybackSessionFactory: StreamPlaybackSessionFactory

    @Inject
    lateinit var backgroundSessionState: WearBackgroundSessionState

    @Inject
    lateinit var playbackSetManager: PlaybackSetManager

    @Inject
    lateinit var publishPlaybackStateUseCase: PublishPlaybackStateUseCase

    @Inject
    lateinit var nowPlayingRepository: WearNowPlayingRepository

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var isStopping = false

    /**
     * The hold outlives the screen that opened the stream, so it hangs on the service's own scope and
     * dies with [onDestroy] - the whole point of the hand-off is that the ViewModel's scope is gone.
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * The channel notice is a line of text on the player screen and this service has no screen, so it
     * is dropped here rather than routed: the screen keeps drawing its own while it is open.
     */
    private val streamPlaybackSession by lazy {
        streamPlaybackSessionFactory.create(scope = serviceScope, onChannelReason = { })
    }

    /**
     * The published position is what a returning screen resumes from, so it is pumped while the sound
     * moves rather than written once at the hand-off - the same pump both player screens run.
     */
    private var progressTicker: PlaybackProgressTicker? = null

    /**
     * Stops the service the moment the sound stops, rather than waiting for the system to reclaim it.
     * Strategic §7 names a notification outliving its sound as a risk to design against: an idle or
     * ended player still holding a media notification tells the owner the watch is playing when it is
     * not, and the notification carries controls that would then do nothing.
     */
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                stopPlaybackAndSelf()
            }
        }

        /**
         * Strategic §7 asks for the wide channel to go back on a pause rather than only on a stop, so
         * a paused stream costs no more radio than a stopped one while the session stays resumable.
         */
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                streamPlaybackSession.withWideChannel()
                progressTicker?.start()
            } else {
                streamPlaybackSession.stop()
                progressTicker?.stop()
                if (!WearBackgroundPlaybackPolicy.keepsBackgroundSession(isPlaying)) {
                    Timber.d("S2166: paused background session releases foreground service")
                    stopPlaybackAndSelf()
                    return
                }
            }
            backgroundSessionState.updateProgress(currentPositionMs(), isPlaying)
            publishPlaybackState(isPlaying)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(WearNotificationIds.BACKGROUND_PLAYBACK)
                .setChannelId(CHANNEL_ID)
                .build()
        )
        val exoPlayer = buildPlayer()
        player = exoPlayer
        progressTicker = PlaybackProgressTicker(serviceScope, exoPlayer) { position ->
            backgroundSessionState.updateProgress(position, exoPlayer.isPlaying)
        }
        // The session sees the set-aware wrapper, the service keeps the raw player: only the wrapper
        // can answer NEXT and PREVIOUS on a one-item player, and only the raw one can be released.
        mediaSession = MediaSession.Builder(this, WearPlaybackSetPlayer(exoPlayer, playbackSetManager))
            .build()
        // Before any item loads: a foreground service that has not posted its notification within the
        // platform's window is killed, and loading the first item is exactly what can outlast it.
        startInForeground()
    }

    /**
     * START_NOT_STICKY: a service the platform restarts arrives with a null intent and an empty
     * player, so there is nothing to resume. Restarting it would only raise a playback notification
     * over a player that is playing nothing.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startHandedOffItem(intent)
            ACTION_STOP -> stopPlaybackAndSelf()
            else -> Timber.w("WearPlaybackService started with an unknown action: %s", intent?.action)
        }
        return START_NOT_STICKY
    }

    /**
     * The screen hands over what it was already playing, so the item arrives resolved - a cached copy
     * for a downloaded network file, the stream uri for a direct one - and the position comes with it.
     * Resolving the item here instead would make the service repeat the screen's whole download branch.
     */
    private fun startHandedOffItem(intent: Intent) {
        val uri = intent.getStringExtra(EXTRA_MEDIA_URI)
        if (uri.isNullOrBlank()) {
            Timber.w("WearPlaybackService: START carried no media uri, nothing to play")
            stopPlaybackAndSelf()
            return
        }
        val exoPlayer = player ?: return
        val streamMediaKind = intent.getStringExtra(EXTRA_STREAM_MEDIA_KIND)
        // Only a direct stream carries a kind; a local file and a downloaded copy need no channel at
        // all, and prepare() on a null kind would claim one for a file already on disk.
        streamMediaKind?.let(streamPlaybackSession::prepare)
        val positionMs = intent.getLongExtra(EXTRA_POSITION_MS, 0L)
        backgroundSessionState.start(
            WearBackgroundSession(
                fileId = intent.getLongExtra(EXTRA_FILE_ID, NO_FILE_ID),
                mediaUri = uri,
                streamMediaKind = streamMediaKind,
                positionMs = positionMs,
                isPlaying = true
            )
        )
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
        exoPlayer.seekTo(positionMs)
        exoPlayer.playWhenReady = true
    }

    private fun currentPositionMs(): Long = player?.currentPosition?.coerceAtLeast(0) ?: 0L

    /**
     * Strategic §5.2: while the app is minimized this service is the only thing that knows what is
     * playing, so the complication and the phone have to read it from here or read a screen's last
     * word - which is a session that already ended.
     *
     * The set's own current file is what names the track: the service holds one media item and no
     * metadata of its own, and the set is the same one the screen paged through.
     */
    private fun publishPlaybackState(isPlaying: Boolean) {
        val file = playbackSetManager.currentSet.value?.current
        val name = file?.name ?: return
        val payload = wearPlaybackStatePayload(
            selected = null,
            isPlaying = isPlaying,
            fileName = name,
            positionMs = currentPositionMs(),
            durationMs = player?.duration?.coerceAtLeast(0) ?: 0L,
            mediaType = "AUDIO"
        )
        serviceScope.launch {
            publishPlaybackStateUseCase(payload)
            nowPlayingRepository.setNowPlaying(file.title?.takeIf { it.isNotBlank() } ?: name, file.artist)
            nowPlayingRepository.setPlaying(isPlaying)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * The watch app was swiped away. Playback the owner cannot see and did not leave running is not
     * what this ticket is for - exiting stops, minimizing does not (strategic §3.3).
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        stopPlaybackAndSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        progressTicker?.stop()
        progressTicker = null
        backgroundSessionState.clear()
        streamPlaybackSession.clear()
        // The scope outlives this callback by exactly one write. Cancelling it here instead would
        // drop the flag clear on the one path that does not go through stopPlaybackAndSelf - the
        // explicit exit, which calls stopService and arrives straight at onDestroy - and the
        // complication would keep saying the watch is playing after the app is gone.
        serviceScope.launch { nowPlayingRepository.clearPlayingFlag() }
            .invokeOnCompletion { serviceScope.cancel() }
        mediaSession?.release()
        mediaSession = null
        player?.removeListener(playerListener)
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun buildPlayer(): ExoPlayer {
        // Matches WearAppModule.provideExoPlayer: the same content type and usage, and audio focus
        // handled by the player, so background playback ducks and pauses like the screen-owned one.
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        val handleAudioFocus = true
        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
        val mediaSourceFactory = com.sza.fastmediasorter.wear.data.network.WearStreamDataSourceFactoryProvider
            .createMediaSourceFactory(this)
        return ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, handleAudioFocus)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { it.addListener(playerListener) }
    }

    private fun startInForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.wear_background_playback))
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .setSilent(true)
            .build()
        ServiceCompat.startForeground(
            this,
            WearNotificationIds.BACKGROUND_PLAYBACK,
            notification,
            foregroundServiceType()
        )
    }

    /**
     * Clearing the flag is the last thing this session owes the complication and the phone, and it
     * runs here rather than in [onDestroy] because that is where the scope carrying it is cancelled -
     * a write launched one line before the cancel is a write that never happens. Every path that ends
     * a session passes through here: the end of the set, an error, the stop command and task removal.
     */
    private fun stopPlaybackAndSelf() {
        if (isStopping) {
            return
        }
        isStopping = true
        serviceScope.launch { nowPlayingRepository.clearPlayingFlag() }
        player?.stop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            FOREGROUND_SERVICE_TYPE_NONE
        }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.wear_background_playback_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    companion object {
        const val ACTION_START = "com.sza.fastmediasorter.wear.action.START_BACKGROUND_PLAYBACK"
        const val ACTION_STOP = "com.sza.fastmediasorter.wear.action.STOP_BACKGROUND_PLAYBACK"

        private const val CHANNEL_ID = "wear_background_playback"
        private const val EXTRA_MEDIA_URI = "com.sza.fastmediasorter.wear.extra.MEDIA_URI"
        private const val EXTRA_POSITION_MS = "com.sza.fastmediasorter.wear.extra.POSITION_MS"
        private const val EXTRA_STREAM_MEDIA_KIND = "com.sza.fastmediasorter.wear.extra.STREAM_MEDIA_KIND"
        private const val EXTRA_FILE_ID = "com.sza.fastmediasorter.wear.extra.FILE_ID"

        /** The id the audio screen uses for "no file", so a session with none matches no screen. */
        const val NO_FILE_ID = -1L

        fun startIntent(
            context: Context,
            fileId: Long,
            mediaUri: String,
            positionMs: Long,
            streamMediaKind: String?
        ): Intent =
            Intent(context, WearPlaybackService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_FILE_ID, fileId)
                .putExtra(EXTRA_MEDIA_URI, mediaUri)
                .putExtra(EXTRA_POSITION_MS, positionMs)
                .putExtra(EXTRA_STREAM_MEDIA_KIND, streamMediaKind)

        fun stopIntent(context: Context): Intent =
            Intent(context, WearPlaybackService::class.java).setAction(ACTION_STOP)
    }
}
