package com.sza.fastmediasorter.ui.player.helpers

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import com.bumptech.glide.Glide
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.repository.streams.FaviconAtlasStore
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.databinding.ViewMiniNowPlayingBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.MidiPlaybackPolicy
import com.sza.fastmediasorter.ui.browse.InlinePlaybackAnimator
import com.sza.fastmediasorter.ui.player.AudioPlaybackService
import com.sza.fastmediasorter.ui.player.NowPlayingBottomSheetFragment
import com.sza.fastmediasorter.ui.player.model.MediaItemWithMeta
import com.sza.fastmediasorter.ui.streams.FaviconAtlasSlicer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * Manages the mini Now Playing bar inside PlayerActivity and launches
 * NowPlayingBottomSheetFragment on tap.
 *
 * Also provides [startPlayback] which builds [MediaItemWithMeta] items and
 * delegates to [AudioServiceController.playAudioPlaylistWithMetadata], enabling
 * per-track title/artist metadata in the MediaSession and system notification.
 *
 * Constructor-instantiated in PlayerActivity (same pattern as SleepTimerManager).
 * All playback commands are delegated - this manager holds no ExoPlayer reference.
 *
 * Entry points are guarded by [persistentAudioCompiledIn].
 */
class NowPlayingManager(
    private val activityBinding: ActivityPlayerUnifiedBinding,
    private val fragmentManager: FragmentManager,
    private val audioServiceController: AudioServiceController,
    // S1379: resolved by the host from the capability contract - shared code must not read the
    // build flag itself (CLAUDE.md Rule 14).
    private val persistentAudioCompiledIn: Boolean,
    private val faviconAtlasStore: FaviconAtlasStore,
    // S1382: the atlas sidecar and the tile decode are suspend calls, so the bar needs the host's
    // lifecycle-bound scope rather than a scope of its own.
    private val scope: CoroutineScope
) {

    // View Binding always returns a non-null binding for <include> tags; try/catch guards against
    // unexpected inflation failures on any API level.
    private val miniBar: ViewMiniNowPlayingBinding? = try {
        activityBinding.miniNowPlayingBar
    } catch (e: Exception) {
        Timber.w(e, "NowPlayingManager: Failed to initialize miniBar binding during exception")
        null
    }

    // The player currently observed by [playerListener]. Set in [attachListener], cleared in
    // [detachListener] - keeps the mini bar live while it is shown, mirroring NowPlayingViewModel.
    private var observedPlayer: Player? = null

    // S1382: one animator per bar, bound to the single artwork view. Re-creating it inside
    // populateBarContent() would restart the turn from zero on every track change.
    private val noteAnimator: InlinePlaybackAnimator? =
        miniBar?.let { InlinePlaybackAnimator(it.miniArtwork, NOTE_TURN_MS) }

    // True while the artwork slot holds the fallback note rather than album art or a channel icon.
    // Only the note rotates, so the playback-state listener must know which of the three is on screen.
    private var noteShown = false

    // The store + slicer pair four other screens already use for stream favicons. Lazy because most
    // player sessions never play a stream.
    private val faviconSlicer by lazy { FaviconAtlasSlicer { faviconAtlasStore.atlasFile() } }

    // The sidecar map is read once per bar: an ICY stream rewrites its metadata every song, and
    // re-reading the JSON on each title change would cost a file read per song.
    private var faviconCoords: Map<String, Int>? = null

    // URL whose channel icon is on screen, so a title change does not re-slice the same tile.
    private var appliedIconUrl: String? = null

    private val playerListener = object : Player.Listener {
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            observedPlayer?.let { populateBarContent(it) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            observedPlayer?.let { populateBarContent(it) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateMiniPlayPauseIcon(isPlaying)
            if (noteShown) applyNoteRotation(isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // Playback finished or session went idle - the track is no longer "now playing".
            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                hideBar()
            }
        }
    }

    init {
        if (persistentAudioCompiledIn) {
            if (miniBar != null) {
                Timber.d("NowPlayingManager: Initializing mini now playing bar listeners")
                miniBar.root.setOnClickListener { showBottomSheet() }
                miniBar.miniPlayPause.setOnClickListener {
                    audioServiceController.player?.let { player ->
                        if (player.isPlaying) player.pause() else player.play()
                        updateMiniPlayPauseIcon(player.isPlaying.not())
                    }
                }
            } else {
                Timber.w("NowPlayingManager: miniBar binding is null - mini now playing bar will not be available")
            }
        }
    }

    /**
     * Build a metadata-enriched playlist and start playback via [AudioServiceController].
     *
     * @param files All audio files in the current list (same order as displayed in the player).
     * @param startIndex Index of the track to play first.
     * @param onPlayerReady Callback forwarded from the caller for binding the player to PlayerView.
     */
    fun startPlayback(
        files: List<MediaFile>,
        startIndex: Int,
        onPlayerReady: (androidx.media3.common.Player) -> Unit
    ) {
        if (!persistentAudioCompiledIn) return

        Timber.d("NowPlayingManager: startPlayback files=${files.size} startIndex=$startIndex")
        val items = files.map { file ->
            val mimeType = if (MidiPlaybackPolicy.isMidiPath(file.path)) MimeTypes.AUDIO_MIDI else null
            MediaItemWithMeta(
                uri = buildPlaybackUri(file),
                title = file.title?.takeIf { it.isNotBlank() } ?: file.name.substringBeforeLast('.'),
                artist = file.artist,
                albumArtUri = null,  // cover art resolved lazily in ImageLoadingManager
                mimeType = mimeType,
                sourcePath = file.path,
                resourceId = file.resourceId ?: AudioPlaybackService.currentResourceId,
                size = file.size,
                dateModified = file.lastModified.takeIf { it > 0L } ?: file.createdDate
            )
        }
        audioServiceController.playAudioPlaylistWithMetadata(items, startIndex, onPlayerReady)
    }

    private fun buildPlaybackUri(file: MediaFile): Uri {
        file.contentUri
            ?.takeIf { it.isNotBlank() }
            ?.let { return Uri.parse(it) }

        val parsed = Uri.parse(file.path)
        return if (parsed.scheme.isNullOrBlank()) Uri.fromFile(File(file.path)) else parsed
    }

    /**
     * Called from PlayerActivity.onResume to refresh bar visibility and state.
     *
     * @param currentMediaType The type of the file currently loaded in the player.
     *   The mini bar is suppressed entirely during VIDEO playback - the user expects
     *   full-screen video with touch zones, not a background-audio overlay.
     */
    fun onStart(currentMediaType: MediaType? = null, showPanel: Boolean = false) {
        if (!persistentAudioCompiledIn) return
        updateBarVisibility(currentMediaType, showPanel)
    }

    /**
     * Update mini bar visibility and content based on service state.
     * Suppressed when [currentMediaType] is [MediaType.VIDEO] - the user expects
     * full-screen video; showing a background-audio overlay there is wrong UX.
     *
     * Should be called whenever AudioPlaybackService.isRunning may have changed.
     */
    fun updateBarVisibility(currentMediaType: MediaType? = null, showPanel: Boolean = false) {
        if (!persistentAudioCompiledIn || miniBar == null) return

        // Never show when the user is directly viewing an audio or video file -
        // they control playback via the player UI itself, not the background bar.
        if (currentMediaType == MediaType.VIDEO || currentMediaType == MediaType.AUDIO) {
            hideBar()
            return
        }

        // Panel disabled by the "Show now-playing panel" setting.
        if (!showPanel) {
            hideBar()
            return
        }

        if (!AudioPlaybackService.isRunning) {
            hideBar()
            Timber.d("NowPlayingManager: updateBarVisibility - service not running, hiding bar")
            return
        }

        // Service is running but MediaController may not be connected yet (e.g. when opening
        // a photo PlayerActivity while audio plays in background). Connect status-only - no playback
        // is started - then inspect the real playbackState before showing the bar.
        Timber.d("NowPlayingManager: updateBarVisibility - service running, connecting for status")
        audioServiceController.connectForStatus { player ->
            Handler(Looper.getMainLooper()).post {
                if (player == null) {
                    // Service died during connect attempt
                    hideBar()
                    Timber.d("NowPlayingManager: updateBarVisibility - connectForStatus returned null, hiding")
                    return@post
                }
                val state = player.playbackState
                val activelyPlaying = state == Player.STATE_READY || state == Player.STATE_BUFFERING
                if (!activelyPlaying) {
                    // STATE_ENDED / IDLE - service is shutting down, hide bar
                    hideBar()
                    Timber.d("NowPlayingManager: updateBarVisibility - playbackState=$state (not active), hiding")
                    return@post
                }
                // Service alive and playing - show, populate, and observe for live track changes
                miniBar.root.isVisible = true
                Timber.d("NowPlayingManager: updateBarVisibility - showing bar (playbackState=$state)")
                attachListener(player)
                populateBarContent(player)
            }
        }
    }

    /** Populate title, artwork, and play/pause icon from the current player metadata. */
    private fun populateBarContent(player: Player) {
        val bar = miniBar ?: return
        val meta = player.mediaMetadata
        bar.miniTitle.text = meta.title?.toString()
            ?: activityBinding.root.context.getString(R.string.now_playing_label)
        val artworkUri = meta.artworkUri
        // A local file can read as non-seekable for a frame while its timeline is still empty, so the
        // stream branch falls back to the rotating note rather than leaving the slot blank.
        if (player.isLiveStreamItem()) {
            tryShowStreamIcon(player, player.isPlaying)
        } else {
            appliedIconUrl = null
            if (artworkUri != null) {
                showStillArtwork(artworkUri)
            } else {
                showRotatingNote(player.isPlaying)
            }
        }
        updateMiniPlayPauseIcon(player.isPlaying)
    }

    /** Album art carries the recognition, so it stands still - any running rotation is dropped first. */
    private fun showStillArtwork(artworkUri: Uri) {
        val bar = miniBar ?: return
        noteAnimator?.stopNote()
        noteShown = false
        Glide.with(activityBinding.root.context)
            .load(artworkUri)
            .placeholder(R.drawable.ic_music_note)
            .error(R.drawable.ic_music_note)
            .into(bar.miniArtwork)
    }

    /**
     * S1146's live-stream predicate, evaluated on the client controller: Timeline.Window carries
     * both isSeekable and liveConfiguration across the session hop, so it holds here as well as
     * service-side. A plain ICY progressive stream reports isLive false, which is why a
     * non-seekable item counts as live too.
     */
    private fun Player.isLiveStreamItem(): Boolean =
        isCurrentMediaItemLive || !isCurrentMediaItemSeekable

    /**
     * Puts the channel's own favicon in the artwork slot. The rotating note stays up until the tile
     * is decoded, and remains when the channel has no icon at all.
     */
    private fun tryShowStreamIcon(player: Player, isPlaying: Boolean) {
        val url = player.currentMediaItem?.requestMetadata?.mediaUri?.toString()
        if (url == null || !player.isLiveStreamItem()) {
            showRotatingNote(isPlaying)
            return
        }
        if (url == appliedIconUrl) return
        showRotatingNote(isPlaying)
        scope.launch {
            val coords = faviconCoords ?: faviconAtlasStore.coords().also { faviconCoords = it }
            val index = coords[url]
            if (index == null) return@launch
            val tile = faviconSlicer.tileFor(index) ?: return@launch
            // The decode is off the main thread, so the track may have moved on meanwhile.
            val current = observedPlayer?.currentMediaItem?.requestMetadata?.mediaUri?.toString()
            if (current != url) return@launch
            noteAnimator?.stopNote()
            noteShown = false
            miniBar?.miniArtwork?.setImageBitmap(tile)
            appliedIconUrl = url
        }
    }

    /** The fallback note is the only rotating state: turning means "playing right now". */
    private fun showRotatingNote(isPlaying: Boolean) {
        val bar = miniBar ?: return
        // A still-pending artwork load would otherwise land on top of the note a moment later.
        Glide.with(activityBinding.root.context).clear(bar.miniArtwork)
        bar.miniArtwork.setImageResource(R.drawable.ic_music_note)
        noteShown = true
        applyNoteRotation(isPlaying)
    }

    /**
     * Freezes rather than stops on pause: [InlinePlaybackAnimator.stopNote] resets the angle to zero,
     * and the note has to continue from where it stood when playback resumes.
     */
    private fun applyNoteRotation(isPlaying: Boolean) {
        if (isPlaying) {
            noteAnimator?.startNote()
            noteAnimator?.resumeNote()
        } else {
            noteAnimator?.pauseNote()
        }
    }

    /**
     * Observe [player] so the mini bar reflects live track/state changes (auto-advance, skip,
     * new playlist) instead of a one-shot snapshot. Idempotent per player instance.
     */
    private fun attachListener(player: Player) {
        if (observedPlayer === player) return
        detachListener()
        observedPlayer = player
        player.addListener(playerListener)
    }

    /** Stop observing the player - symmetric teardown for [attachListener]. */
    private fun detachListener() {
        observedPlayer?.removeListener(playerListener)
        observedPlayer = null
    }

    /**
     * The single teardown for the bar. Every hide path routes through it so a branch added later
     * cannot forget to cancel the infinite note animation - the leak shape S1302 was opened for.
     */
    private fun hideBar() {
        miniBar?.root?.isVisible = false
        noteAnimator?.stopNote()
        noteShown = false
        appliedIconUrl = null
        detachListener()
    }

    /**
     * Called from PlayerActivity.onPause (via the lifecycle bridge) - symmetric to [onStart].
     * Detaches the live player listener so the bar does not update off-screen and no reference
     * to the activity's views leaks through the controller across the pause/resume edge.
     */
    fun onStop() {
        // No Choreographer frames while the activity is off-screen - the bar is not visible anyway.
        noteAnimator?.stopNote()
        noteShown = false
        detachListener()
    }

    private fun updateMiniPlayPauseIcon(isPlaying: Boolean) {
        miniBar?.apply {
            miniPlayPause.setImageResource(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
            miniPlayPause.contentDescription = activityBinding.root.context.getString(
                if (isPlaying) R.string.now_playing_pause_desc else R.string.now_playing_play_desc
            )
        }
    }

    private fun showBottomSheet() {
        if (fragmentManager.findFragmentByTag(NowPlayingBottomSheetFragment.TAG) != null) return
        Timber.d("NowPlayingManager: showing NowPlayingBottomSheetFragment")
        NowPlayingBottomSheetFragment.newInstance()
            .show(fragmentManager, NowPlayingBottomSheetFragment.TAG)
    }

    companion object {
        private const val NOTE_TURN_MS = 3000L
    }
}
