package com.sza.fastmediasorter.ui.player

import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
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
class AudioPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        Timber.d("AudioPlaybackService: onCreate")

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
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
        currentPlayer.prepare()
        currentPlayer.play()
    }

    /**
     * MediaSession callback for handling controller requests.
     */
    private inner class AudioSessionCallback : MediaSession.Callback {
        // Default implementation handles play, pause, seek, skip via MediaSession
        // Custom handling can be added here if needed
    }

    override fun onBind(intent: Intent?): IBinder? {
        Timber.d("AudioPlaybackService: onBind")
        return super.onBind(intent)
    }

    companion object {
        private const val TAG = "AudioPlaybackService"
    }
}
