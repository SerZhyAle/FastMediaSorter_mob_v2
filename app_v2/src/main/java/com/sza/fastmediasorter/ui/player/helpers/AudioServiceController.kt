package com.sza.fastmediasorter.ui.player.helpers

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.sza.fastmediasorter.core.debug.MemoryEnduranceTracker
import com.sza.fastmediasorter.domain.model.PlaybackOrderMode
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
    private val controllerLock = Any()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    // S0895: MemoryEnduranceTracker is a single-slot global singleton (one active scenario at a
    // time, no per-caller stack) - only end the scenario this instance actually started, or
    // release() (which can fire even when this controller never connected, e.g. an eager/defensive
    // construction with no playback) would clobber an unrelated subsystem's in-flight scenario
    // (e.g. VideoPlayerManager's own tracked scenario).
    private var ownsEnduranceScenario = false

    /** Whether the controller is currently connected to the service */
    val isConnected: Boolean
        get() = synchronized(controllerLock) { mediaController?.isConnected == true }

    /** Returns the MediaController as a Player (or null if not connected) */
    val player: Player?
        get() = synchronized(controllerLock) { mediaController }

    /**
     * Connect to AudioPlaybackService asynchronously.
     * @param onConnected Callback invoked when connection is established, with the MediaController as Player.
     */
    fun connect(onConnected: (Player) -> Unit) {
        val connectionRequest = getOrCreateControllerFuture("connect")
        when (connectionRequest) {
            is ControllerConnectionRequest.Connected -> {
                onConnected(connectionRequest.controller)
                return
            }
            is ControllerConnectionRequest.FutureRequest -> Unit
        }

        val future = (connectionRequest as ControllerConnectionRequest.FutureRequest).future
        future.addListener({
            try {
                val controller = future.get()
                when (storeResolvedController(future, controller)) {
                    ControllerStoreResult.Stale -> {
                        controller.release()
                        return@addListener
                    }
                    ControllerStoreResult.StoredNew -> {
                        // S0120: record BASELINE for AUD-playback endurance session on first connection
                        MemoryEnduranceTracker.startScenario("AUD-playback")
                        ownsEnduranceScenario = true
                        Timber.d("AudioServiceController: connected successfully")
                    }
                    ControllerStoreResult.ReusedExisting -> Unit
                }
                onConnected(controller)
            } catch (e: Exception) {
                clearFutureOnFailure(future)
                Timber.e(e, "AudioServiceController: failed to connect")
            }
        }, MoreExecutors.directExecutor())
    }

    /**
     * Connect to AudioPlaybackService to read current playback state without starting playback.
     * Safe to call multiple times - immediately invokes [onResult] if already connected.
     *
     * Used by NowPlayingManager to populate the mini bar when the activity opens on a
     * non-audio file (e.g. photo/video) while background audio is still running.
     *
     * @param onResult Invoked with the connected Player, or null if the service could not
     *   be reached (died between [AudioPlaybackService.isRunning] check and connect attempt).
     *   May be called on a background thread - wrap UI ops in Handler(Main).
     */
    fun connectForStatus(onResult: (player: Player?) -> Unit) {
        val connectionRequest = getOrCreateControllerFuture("connectForStatus")
        when (connectionRequest) {
            is ControllerConnectionRequest.Connected -> {
                onResult(connectionRequest.controller)
                return
            }
            is ControllerConnectionRequest.FutureRequest -> Unit
        }

        val future = (connectionRequest as ControllerConnectionRequest.FutureRequest).future
        future.addListener({
            try {
                val controller = future.get()
                when (storeResolvedController(future, controller)) {
                    ControllerStoreResult.Stale -> {
                        controller.release()
                        onResult(null)
                        return@addListener
                    }
                    ControllerStoreResult.StoredNew,
                    ControllerStoreResult.ReusedExisting -> Unit
                }
                Timber.d("AudioServiceController: connectForStatus - connected")
                onResult(controller)
            } catch (e: Exception) {
                clearFutureOnFailure(future)
                Timber.w(e, "AudioServiceController: connectForStatus - failed (service may have died)")
                onResult(null)
            }
        }, MoreExecutors.directExecutor())
    }

    /**
     * Play a single audio file through the service.
     * Connects first if not already connected.
     * @param uri The audio file URI
     * @param onPlayerReady Callback with the MediaController as Player (for binding to PlayerView)
     */
    fun playAudio(
        uri: Uri,
        mimeType: String? = null,
        onPlayerReady: (Player) -> Unit
    ) {
        connect { player ->
            Timber.d("AudioServiceController: playAudio uri=$uri")
            val mediaItemBuilder = MediaItem.Builder()
                .setUri(uri)
            if (mimeType != null) {
                mediaItemBuilder.setMimeType(mimeType)
            }
            val mediaItem = mediaItemBuilder.build()
            player.setMediaItem(mediaItem)
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.prepare()
            player.play()
            onPlayerReady(player)
        }
    }

    /**
     * Play a single audio file through the service with title/artist metadata.
     * The metadata is reflected in the system media notification (lock screen, notification shade).
     * @param uri The audio file URI
     * @param title Track title shown in the notification (filename without extension)
     * @param artist Artist tag, or null
     * @param onPlayerReady Callback with the MediaController as Player
     */
    fun playAudioWithMetadata(
        uri: Uri,
        title: String,
        artist: String? = null,
        mimeType: String? = null,
        streamCredentials: StreamCredentials? = null,
        onPlayerReady: (Player) -> Unit
    ) {
        connect { player ->
            Timber.d("AudioServiceController: playAudioWithMetadata uri=$uri title=$title artist=$artist")
            // S1382: MediaItem.toBundle() drops localConfiguration, so a client MediaController never
            // sees the played URI. requestMetadata is bundled, and it is how the mini bar tells an
            // audio stream from a local file and finds the channel favicon for it.
            val mediaItemBuilder = MediaItem.Builder()
                .setUri(uri)
                .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            if (mimeType != null) {
                mediaItemBuilder.setMimeType(mimeType)
            }
            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
            // For network/cloud streaming the service resolves credentials from these extras
            // (it cannot hit the credentials DB on the player thread). See NetworkAwareMediaSourceFactory.
            streamCredentials?.let { creds ->
                metadataBuilder.setExtras(
                    Bundle().apply {
                        putString(NetworkAwareMediaSourceFactory.EXTRA_CRED_USER, creds.username)
                        putString(NetworkAwareMediaSourceFactory.EXTRA_CRED_PASS, creds.password)
                        creds.domain?.let { putString(NetworkAwareMediaSourceFactory.EXTRA_CRED_DOMAIN, it) }
                        putInt(NetworkAwareMediaSourceFactory.EXTRA_CRED_PORT, creds.port)
                    }
                )
            }
            val mediaItem = mediaItemBuilder
                .setMediaMetadata(metadataBuilder.build())
                .build()
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
            // Idempotent: if the playlist is already loaded at the requested index (e.g., after
            // ExoPlayer auto-advanced and syncAudioServiceIndex() triggered updateUI → playVideo()),
            // skip setMediaItems so the currently-playing track is not interrupted/restarted.
            val alreadyPlaying = player.mediaItemCount == items.size &&
                player.currentMediaItemIndex == startIndex &&
                player.playbackState != Player.STATE_IDLE &&
                player.playbackState != Player.STATE_ENDED
            if (alreadyPlaying) {
                Timber.d("AudioServiceController: already at index=$startIndex in playlist of ${items.size}, skipping restart")
                onPlayerReady(player)
                return@connect
            }
            val mediaItems = items.map { item ->
                val mediaItemBuilder = MediaItem.Builder()
                    .setUri(item.uri)
                if (item.mimeType != null) {
                    mediaItemBuilder.setMimeType(item.mimeType)
                }
                val extras = Bundle().apply {
                    item.sourcePath?.let { putString(EXTRA_SOURCE_PATH, it) }
                    putLong(EXTRA_RESOURCE_ID, item.resourceId)
                    putLong(EXTRA_SIZE, item.size)
                    putLong(EXTRA_DATE_MODIFIED, item.dateModified)
                }
                mediaItemBuilder
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(item.title)
                            .setArtist(item.artist)
                            .setArtworkUri(item.albumArtUri)
                            .setExtras(extras)
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

    fun applyPlaybackOrderMode(mode: PlaybackOrderMode) {
        val player = synchronized(controllerLock) { mediaController } ?: return
        // S0549: on a single-item timeline (audio is frequently loaded as one MediaItem -
        // network/cloud streaming and the playlist fallback), REPEAT_MODE_ALL degenerates into
        // "repeat that one track" via MEDIA_ITEM_TRANSITION_REASON_REPEAT and STATE_ENDED never
        // fires, so the app-level auto-advance (PlayerNavigationCoordinator.nextFile, which owns
        // the SHUFFLE/LOOP_LIST order model) is never invoked. For SHUFFLE/LOOP_LIST on such a
        // timeline use REPEAT_MODE_OFF so the track ends and the app advances with the same order
        // model as manual NEXT. REPEAT_ONE intentionally keeps repeating the single track.
        val singleItem = player.mediaItemCount <= 1
        when (mode) {
            PlaybackOrderMode.LOOP_LIST -> {
                player.repeatMode = if (singleItem) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL
                player.shuffleModeEnabled = false
            }
            PlaybackOrderMode.PLAY_THROUGH -> {
                player.repeatMode = Player.REPEAT_MODE_OFF
                player.shuffleModeEnabled = false
            }
            PlaybackOrderMode.SHUFFLE -> {
                player.repeatMode = if (singleItem) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL
                player.shuffleModeEnabled = !singleItem
            }
            PlaybackOrderMode.REPEAT_ONE -> {
                player.repeatMode = Player.REPEAT_MODE_ONE
                player.shuffleModeEnabled = false
            }
        }
    }

    /**
     * Disconnect from the service and release resources.
     * Must be called when the Activity is destroyed.
     */
    fun release() {
        if (ownsEnduranceScenario) {
            // S0120: emit AUD-playback SUMMARY and schedule cooldown checkpoint
            MemoryEnduranceTracker.endScenario()
            ownsEnduranceScenario = false
        }
        Timber.d("AudioServiceController: releasing")
        synchronized(controllerLock) {
            controllerFuture?.let { MediaController.releaseFuture(it) }
            controllerFuture = null
            mediaController = null
        }
    }

    private fun getConnectedController(): MediaController? = synchronized(controllerLock) {
        mediaController?.takeIf { it.isConnected }
    }

    private fun getOrCreateControllerFuture(reason: String): ControllerConnectionRequest {
        synchronized(controllerLock) {
            mediaController?.takeIf { it.isConnected }?.let { connectedController ->
                return ControllerConnectionRequest.Connected(connectedController)
            }

            val existingFuture = controllerFuture
            if (existingFuture != null && mediaController == null) {
                Timber.d("AudioServiceController: %s - reusing in-flight controller future", reason)
                return ControllerConnectionRequest.FutureRequest(existingFuture)
            }

            existingFuture?.let {
                Timber.d("AudioServiceController: %s - releasing stale controller future", reason)
                MediaController.releaseFuture(it)
                controllerFuture = null
                mediaController = null
            }

            Timber.d("AudioServiceController: %s - connecting to AudioPlaybackService", reason)
            val sessionToken = SessionToken(
                context,
                ComponentName(context, AudioPlaybackService::class.java)
            )
            val future = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture = future
            return ControllerConnectionRequest.FutureRequest(future)
        }
    }

    private fun storeResolvedController(
        future: ListenableFuture<MediaController>,
        controller: MediaController
    ): ControllerStoreResult = synchronized(controllerLock) {
        if (controllerFuture !== future) {
            return ControllerStoreResult.Stale
        }

        if (mediaController !== controller) {
            mediaController = controller
            return ControllerStoreResult.StoredNew
        }
        ControllerStoreResult.ReusedExisting
    }

    private fun clearFutureOnFailure(future: ListenableFuture<MediaController>) {
        synchronized(controllerLock) {
            if (controllerFuture === future && mediaController == null) {
                controllerFuture = null
            }
        }
    }

    companion object {
        const val EXTRA_SOURCE_PATH = "fms.source_path"
        const val EXTRA_RESOURCE_ID = "fms.resource_id"
        const val EXTRA_SIZE = "fms.size"
        const val EXTRA_DATE_MODIFIED = "fms.date_modified"
    }

    private sealed interface ControllerConnectionRequest {
        data class Connected(val controller: MediaController) : ControllerConnectionRequest
        data class FutureRequest(
            val future: ListenableFuture<MediaController>
        ) : ControllerConnectionRequest
    }

    private enum class ControllerStoreResult {
        Stale,
        StoredNew,
        ReusedExisting,
    }
}

/**
 * Credentials the background service needs to stream a network source, resolved by the caller on a
 * coroutine and carried to the service via [MediaItem] metadata extras (the service cannot resolve
 * them on the player thread). Host/share/path come from the URI; these are the auth fields only.
 */
data class StreamCredentials(
    val username: String,
    val password: String,
    val domain: String?,
    val port: Int
)
