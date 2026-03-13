package com.sza.fastmediasorter.ui.player

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import timber.log.Timber

/**
 * Audio-only background playback service using Media3 MediaSessionService.
 *
 * Scope: ONLY for audio files. Video playback is NOT affected —
 * video always goes through Activity ExoPlayer in VideoPlayerManager.
 *
 * This service creates its own ExoPlayer instance for audio playback.
 * When "Background playback" setting is OFF, audio plays through the existing
 * Activity ExoPlayer path (as before) and this service is not started.
 *
 * Lifecycle: starts on audio play (when background setting is ON),
 * survives Activity destruction, stops when playback ends or user stops.
 */
@UnstableApi
class AudioPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        Timber.d("AudioPlaybackService: onCreate")

        // Create notification channel (required for Android 8+)
        MediaNotificationManager.createNotificationChannel(this)

        // Set custom notification provider
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

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(wakeMode)
            .build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                Timber.d("AudioPlaybackService: playbackState=$playbackState")
                if (playbackState == Player.STATE_ENDED) {
                    Timber.d("AudioPlaybackService: playback ended, stopping service")
                    stopSelf()
                }
            }
        })

        player = exoPlayer

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setCallback(AudioSessionCallback())
            .build()

        Timber.d("AudioPlaybackService: MediaSession created")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = mediaSession?.player
        if (currentPlayer == null || !currentPlayer.playWhenReady
            || currentPlayer.mediaItemCount == 0
            || currentPlayer.playbackState == Player.STATE_ENDED
        ) {
            Timber.d("AudioPlaybackService: task removed, no active playback — stopping")
            stopSelf()
        }
        // If still playing, let the service continue in background
    }

    override fun onDestroy() {
        Timber.d("AudioPlaybackService: onDestroy")
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
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
     * directly to the ExoPlayer by Media3 — no override needed.
     * This callback ensures all standard commands are available and logs media events.
     */
    private inner class AudioSessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ConnectionResult {
            Timber.d("AudioPlaybackService: MediaSession onConnect from ${controller.packageName}")
            // Allow all standard player commands including next/previous
            return ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(ConnectionResult.DEFAULT_PLAYER_COMMANDS)
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

    companion object {
        private const val TAG = "AudioPlaybackService"
    }
}
