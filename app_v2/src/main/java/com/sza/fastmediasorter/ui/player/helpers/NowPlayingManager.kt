package com.sza.fastmediasorter.ui.player.helpers

import android.net.Uri
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import com.bumptech.glide.Glide
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.databinding.ViewMiniNowPlayingBinding
import com.sza.fastmediasorter.domain.model.MediaFile
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
 * All playback commands are delegated — this manager holds no ExoPlayer reference.
 *
 * Entry points are guarded by [BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK].
 */
class NowPlayingManager(
    private val activityBinding: ActivityPlayerUnifiedBinding,
    private val fragmentManager: FragmentManager,
    private val audioServiceController: AudioServiceController
) {

    // Safe initialization: Handle Android 8 (API 26) where nested binding may be null
    // Try nested binding first (API 28+), fall back to manual binding from root view (Android 8)
    private val miniBar: ViewMiniNowPlayingBinding? = try {
        val nestedBinding = activityBinding.miniNowPlayingBar
        if (nestedBinding != null) {
            Timber.d("NowPlayingManager: Using nested binding from ActivityPlayerUnifiedBinding")
            nestedBinding
        } else {
            // Android 8 fallback: manually get the included layout root and bind it
            Timber.d("NowPlayingManager: Nested binding is null, trying manual findViewById fallback for Android 8")
            val miniNowPlayingView = activityBinding.root.findViewById<ViewGroup>(R.id.miniNowPlayingBar)
            miniNowPlayingView?.let {
                Timber.d("NowPlayingManager: Found miniNowPlayingBar view, creating binding manually")
                ViewMiniNowPlayingBinding.bind(it)
            } ?: run {
                Timber.w("NowPlayingManager: Could not find miniNowPlayingBar view in activity hierarchy")
                null
            }
        }
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
     * Called from PlayerActivity.onStart to refresh bar visibility and state.
     */
    fun onStart() {
        if (!BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK) return
        updateBarVisibility()
    }

    /**
     * Update mini bar visibility and content based on service state.
     * Should be called whenever AudioPlaybackService.isRunning may have changed.
     */
    fun updateBarVisibility() {
        if (!BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK || miniBar == null) return

        val running = AudioPlaybackService.isRunning
        miniBar.root.isVisible = running
        Timber.d("NowPlayingManager: updateBarVisibility running=$running")

        if (running) {
            val player = audioServiceController.player
            if (player != null) {
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
