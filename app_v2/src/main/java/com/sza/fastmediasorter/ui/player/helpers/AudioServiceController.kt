package com.sza.fastmediasorter.ui.player.helpers

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.sza.fastmediasorter.ui.player.AudioPlaybackService
import com.sza.fastmediasorter.ui.player.model.MediaItemWithMeta
import timber.log.Timber

/**
 * Manages connection to AudioPlaybackService via MediaController.
 *
 * When "Background playback" setting is ON and the current file is AUDIO,
 * this controller connects to the service and provides a Player interface
 * (via MediaController) that can be set to PlayerView.
 *
 * MediaController implements Player, so all existing PlayerView controls
 * (play/pause, seekbar, timeline) work transparently through the service.
 *
 * Video playback is NEVER routed through this controller.
 */
class AudioServiceController(
    private val context: Context
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    /** Whether the controller is currently connected to the service */
    val isConnected: Boolean
        get() = mediaController?.isConnected == true

    /** Returns the MediaController as a Player (or null if not connected) */
    val player: Player?
        get() = mediaController

    /**
     * Connect to AudioPlaybackService asynchronously.
     * @param onConnected Callback invoked when connection is established, with the MediaController as Player.
     */
    fun connect(onConnected: (Player) -> Unit) {
        if (isConnected) {
            mediaController?.let { onConnected(it) }
            return
        }

        Timber.d("AudioServiceController: connecting to AudioPlaybackService")

        val sessionToken = SessionToken(
            context,
            ComponentName(context, AudioPlaybackService::class.java)
        )

        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future

        future.addListener({
            try {
                val controller = future.get()
                mediaController = controller
                Timber.d("AudioServiceController: connected successfully")
                onConnected(controller)
            } catch (e: Exception) {
                Timber.e(e, "AudioServiceController: failed to connect")
            }
        }, MoreExecutors.directExecutor())
    }

    /**
     * Play a single audio file through the service.
     * Connects first if not already connected.
     * @param uri The audio file URI
     * @param onPlayerReady Callback with the MediaController as Player (for binding to PlayerView)
     */
    fun playAudio(uri: Uri, onPlayerReady: (Player) -> Unit) {
        connect { player ->
            Timber.d("AudioServiceController: playAudio uri=$uri")
            val mediaItem = MediaItem.fromUri(uri)
            player.setMediaItem(mediaItem)
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.prepare()
            player.play()
            onPlayerReady(player)
        }
    }

    /**
     * Play a playlist of audio files starting from given index.
     * @param uris List of audio file URIs
     * @param startIndex Index to start from
     * @param onPlayerReady Callback with the MediaController as Player
     */
    fun playAudioPlaylist(uris: List<Uri>, startIndex: Int = 0, onPlayerReady: (Player) -> Unit) {
        connect { player ->
            Timber.d("AudioServiceController: playAudioPlaylist size=${uris.size}, startIndex=$startIndex")
            val mediaItems = uris.map { MediaItem.fromUri(it) }
            player.setMediaItems(mediaItems, startIndex, 0)
            player.repeatMode = Player.REPEAT_MODE_ALL
            player.prepare()
            player.play()
            onPlayerReady(player)
        }
    }

    /**
     * Play a playlist of audio files with per-track metadata (title, artist, album art).
     * Builds MediaItems with full MediaMetadata so the MediaSession and system notification
     * can display track info and cover art.
     *
     * Backward compatibility: [playAudio] and [playAudioPlaylist] remain unchanged.
     *
     * @param items List of tracks with metadata; albumArtUri should be a local file:// URI
     * @param startIndex Index to start from
     * @param onPlayerReady Callback with the MediaController as Player
     */
    fun playAudioPlaylistWithMetadata(
        items: List<MediaItemWithMeta>,
        startIndex: Int = 0,
        onPlayerReady: (Player) -> Unit
    ) {
        connect { player ->
            Timber.d("AudioServiceController: playAudioPlaylistWithMetadata size=${items.size}, startIndex=$startIndex")
            val mediaItems = items.map { item ->
                MediaItem.Builder()
                    .setUri(item.uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(item.title)
                            .setArtist(item.artist)
                            .setArtworkUri(item.albumArtUri)
                            .build()
                    )
                    .build()
            }
            player.setMediaItems(mediaItems, startIndex, 0)
            player.repeatMode = Player.REPEAT_MODE_ALL
            player.prepare()
            player.play()
            onPlayerReady(player)
        }
    }

    /**
     * Disconnect from the service and release resources.
     * Must be called when the Activity is destroyed.
     */
    fun release() {
        Timber.d("AudioServiceController: releasing")
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }
}
