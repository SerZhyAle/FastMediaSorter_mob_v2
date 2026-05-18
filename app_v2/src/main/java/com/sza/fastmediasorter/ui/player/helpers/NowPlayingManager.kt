package com.sza.fastmediasorter.ui.player.helpers

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.media3.common.Player
import com.bumptech.glide.Glide
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.databinding.ViewMiniNowPlayingBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.player.AudioPlaybackService
import com.sza.fastmediasorter.ui.player.NowPlayingBottomSheetFragment
import com.sza.fastmediasorter.ui.player.model.MediaItemWithMeta
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
 * Entry points are guarded by [BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK].
 */
class NowPlayingManager(
    private val activityBinding: ActivityPlayerUnifiedBinding,
    private val fragmentManager: FragmentManager,
    private val audioServiceController: AudioServiceController
) {

    // View Binding always returns a non-null binding for <include> tags; try/catch guards against
    // unexpected inflation failures on any API level.
    private val miniBar: ViewMiniNowPlayingBinding? = try {
        activityBinding.miniNowPlayingBar
    } catch (e: Exception) {
        Timber.w(e, "NowPlayingManager: Failed to initialize miniBar binding during exception")
        null
    }

    init {
        if (BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK) {
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
        if (!BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK) return

        Timber.d("NowPlayingManager: startPlayback files=${files.size} startIndex=$startIndex")
        val items = files.map { file ->
            MediaItemWithMeta(
                uri = buildPlaybackUri(file),
                title = file.name.substringBeforeLast('.'),
                artist = null,       // ID3 artist extracted natively by ExoPlayer after load
                albumArtUri = null   // cover art resolved lazily in ImageLoadingManager
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
        if (!BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK) return
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
        if (!BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK || miniBar == null) return

        // Never show when the user is directly viewing an audio or video file -
        // they control playback via the player UI itself, not the background bar.
        if (currentMediaType == MediaType.VIDEO || currentMediaType == MediaType.AUDIO) {
            miniBar.root.isVisible = false
            return
        }

        // Panel disabled by the "Show now-playing panel" setting.
        if (!showPanel) {
            miniBar.root.isVisible = false
            return
        }

        if (!AudioPlaybackService.isRunning) {
            miniBar.root.isVisible = false
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
                    miniBar.root.isVisible = false
                    Timber.d("NowPlayingManager: updateBarVisibility - connectForStatus returned null, hiding")
                    return@post
                }
                val state = player.playbackState
                val activelyPlaying = state == Player.STATE_READY || state == Player.STATE_BUFFERING
                if (!activelyPlaying) {
                    // STATE_ENDED / IDLE - service is shutting down, hide bar
                    miniBar.root.isVisible = false
                    Timber.d("NowPlayingManager: updateBarVisibility - playbackState=$state (not active), hiding")
                    return@post
                }
                // Service alive and playing - show and populate bar
                miniBar.root.isVisible = true
                Timber.d("NowPlayingManager: updateBarVisibility - showing bar (playbackState=$state)")
                val meta = player.mediaMetadata
                miniBar.miniTitle.text = meta.title?.toString()
                    ?: activityBinding.root.context.getString(R.string.now_playing_label)
                val artworkUri = meta.artworkUri
                if (artworkUri != null) {
                    Glide.with(activityBinding.root.context)
                        .load(artworkUri)
                        .placeholder(R.drawable.ic_music_note)
                        .error(R.drawable.ic_music_note)
                        .into(miniBar.miniArtwork)
                } else {
                    miniBar.miniArtwork.setImageResource(R.drawable.ic_music_note)
                }
                updateMiniPlayPauseIcon(player.isPlaying)
            }
        }
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
}
