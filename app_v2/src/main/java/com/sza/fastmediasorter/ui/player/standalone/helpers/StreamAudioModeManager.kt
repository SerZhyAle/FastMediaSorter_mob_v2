package com.sza.fastmediasorter.ui.player.standalone.helpers

import android.app.Activity
import android.content.Intent
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.extractor.metadata.icy.IcyInfo
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.playback.NowPlayingMetadata
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.dialog.StreamInfoDialog
import com.sza.fastmediasorter.ui.player.helpers.AudioWaveParticleView
import com.sza.fastmediasorter.ui.player.helpers.LyricsManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerBindingSafeViews
import com.sza.fastmediasorter.ui.player.standalone.StandaloneHostFactory
import com.sza.fastmediasorter.ui.player.standalone.applyStandaloneOverflowIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * S1143: manages the reduced UI surface, controls, and visualizer lifecycle for audio streams in
 * [com.sza.fastmediasorter.ui.player.standalone.AudioStandaloneActivity].
 * Hides all seek/paging/file-op controls that do not apply to live streams and tracks metadata.
 */
class StreamAudioModeManager(
    private val activity: Activity,
    private val root: View,
    private val lifecycleScope: CoroutineScope,
    private val lyricsManager: LyricsManager,
    private val standaloneHostFactory: StandaloneHostFactory,
    private val getPlayer: () -> Player?,
    private val onShowPlaybackSpeedDialog: () -> Unit,
    private val onShowSleepTimerDialog: () -> Unit,
) {
    private val safeViews = PlayerBindingSafeViews(root)
    private val visualizerView: AudioWaveParticleView?
        get() = safeViews.audioWaveParticleView

    private val _metadata = MutableStateFlow<NowPlayingMetadata?>(null)
    val metadata: StateFlow<NowPlayingMetadata?> = _metadata.asStateFlow()

    private var attachedPlayer: Player? = null
    private var lastSearchedLyricsPair: Pair<String?, String?>? = null

    var streamChannelId: String? = null
    var streamUrl: String? = null
    var streamName: String? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                startVisualizer()
            } else {
                pauseVisualizer()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_ENDED, Player.STATE_IDLE -> stopVisualizer()
            }
        }

        override fun onMetadata(metadata: Metadata) {
            // Direct / local playback path: parse ICY metadata
            for (i in 0 until metadata.length()) {
                val entry = metadata.get(i)
                if (entry is IcyInfo) {
                    val rawTitle = entry.title?.takeIf { it.isNotBlank() }
                    _metadata.value = rawTitle?.let(NowPlayingMetadata::parse)
                }
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            // Background / service playback path: combined metadata
            val title = mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
            val artist = mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() }
            if (title != null) {
                _metadata.value = if (artist != null) {
                    NowPlayingMetadata(artist = artist, title = title)
                } else {
                    NowPlayingMetadata.parse(title)
                }
            }
        }
    }

    /**
     * Attach a playing [Player] instance to observe playback state and metadata.
     */
    fun attachPlayer(player: Player) {
        if (attachedPlayer === player) return
        detachPlayer()
        attachedPlayer = player
        player.addListener(playerListener)
        if (player.isPlaying) {
            startVisualizer()
        }
    }

    /**
     * Detach listener and reset visualizer.
     */
    fun detachPlayer() {
        attachedPlayer?.removeListener(playerListener)
        attachedPlayer = null
        stopVisualizer()
    }

    /**
     * Apply stream mode visibility rules: hide forbidden transport, paging and file-op controls.
     */
    fun apply() {
        // Transport row buttons & seekbar (forbidden for live stream)
        safeViews.setVisibleIfPresent(R.id.exo_repeat, false)
        safeViews.setVisibleIfPresent(R.id.exo_prev_file, false)
        safeViews.setVisibleIfPresent(R.id.exo_next_file, false)
        safeViews.setVisibleIfPresent(R.id.btnRewind10, false)
        safeViews.setVisibleIfPresent(R.id.btnForward30, false)
        safeViews.setVisibleIfPresent(androidx.media3.ui.R.id.exo_progress, false)
        safeViews.setVisibleIfPresent(androidx.media3.ui.R.id.exo_position, false)
        safeViews.setVisibleIfPresent(androidx.media3.ui.R.id.exo_duration, false)

        // Command panel buttons (forbidden for live stream)
        safeViews.setVisibleIfPresent(R.id.btnDeleteCmd, false)
        safeViews.setVisibleIfPresent(R.id.btnRenameCmd, false)
        safeViews.setVisibleIfPresent(R.id.btnFavorite, false)
        safeViews.setVisibleIfPresent(R.id.btnPagePrev, false)
        safeViews.setVisibleIfPresent(R.id.btnPageNext, false)
        safeViews.setVisibleIfPresent(R.id.btnPageRandom, false)
        safeViews.setVisibleIfPresent(R.id.btnPageSlideshow, false)

        // Copy/Move destination panels
        safeViews.setVisibleIfPresent(R.id.bottomPanelsContainer, false)
        safeViews.setVisibleIfPresent(R.id.copyToPanel, false)
        safeViews.setVisibleIfPresent(R.id.moveToPanel, false)

        // Ensure track info container is visible
        safeViews.setVisibleIfPresent(R.id.streamTrackInfoContainer, true)
    }

    /**
     * Setup stream mode toolbar action buttons and popup overflow menu.
     */
    fun setupControls() {
        safeViews.findNullable<View>(R.id.btnShareCmd)?.apply {
            isVisible = true
            setOnClickListener { shareStreamUrl() }
        }
        safeViews.findNullable<View>(R.id.btnInfoCmd)?.apply {
            isVisible = true
            setOnClickListener { showStreamInfo() }
        }
        safeViews.findNullable<View>(R.id.btnOverflowMenu)?.apply {
            isVisible = true
            setOnClickListener { anchor ->
                val popup = PopupMenu(activity, anchor)
                popup.inflate(R.menu.overflow_menu_standalone_player)
                popup.applyStandaloneOverflowIcons()
                // S1143: stream mode overflow menu: show only About channel, Lyrics, Sleep timer, Share, Speed
                listOf(
                    R.id.menu_open_in_fms,
                    R.id.menu_edit_section_standalone,
                    R.id.menu_rename_standalone,
                    R.id.menu_autorotate_standalone,
                    R.id.menu_black_screen,
                    R.id.menu_google_lens,
                    R.id.menu_ocr_image,
                    R.id.menu_youtube_music,
                    R.id.menu_translate_image,
                    R.id.menu_image_text_settings,
                    R.id.menu_print,
                    R.id.menu_save_frame
                ).forEach { popup.menu.findItem(it)?.isVisible = false }
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.menu_stream_about -> {
                            showStreamInfo()
                            true
                        }
                        R.id.menu_lyrics -> {
                            showStreamLyrics()
                            true
                        }
                        R.id.menu_sleep_timer -> {
                            onShowSleepTimerDialog()
                            true
                        }
                        R.id.menu_stream_share -> {
                            shareStreamUrl()
                            true
                        }
                        R.id.menu_playback_speed -> {
                            onShowPlaybackSpeedDialog()
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
        }
    }

    /**
     * Search and display lyrics for currently playing stream track or fallback channel name.
     */
    fun showStreamLyrics() {
        val meta = metadata.value
        val title = meta?.title ?: streamName ?: streamUrl ?: return
        val artist = meta?.artist
        searchLyricsForStream(title, artist)
    }

    /**
     * Search lyrics via [LyricsManager] with given title and artist.
     */
    fun searchLyricsForStream(title: String, artist: String?) {
        lastSearchedLyricsPair = Pair(artist, title)
        val streamFile = MediaFile(
            name = title,
            path = streamUrl ?: "",
            type = MediaType.AUDIO,
            size = 0L,
            createdDate = 0L,
        )
        lyricsManager.searchAndShowLyrics(
            currentFile = streamFile,
            resolvedTitle = title,
            resolvedArtist = artist,
        )
    }

    /**
     * Auto-refresh lyrics when stream track changes while the lyrics viewer is open.
     */
    fun checkLyricsAutoRefresh(meta: NowPlayingMetadata?) {
        val title = meta?.title ?: streamName ?: streamUrl
        if (title != null && lyricsManager.isViewerVisible) {
            val artist = meta?.artist
            val currentPair = Pair(artist, title)
            if (currentPair != lastSearchedLyricsPair) {
                searchLyricsForStream(title, artist)
            }
        }
    }

    /**
     * Display stream information dialog.
     */
    @androidx.media3.common.util.UnstableApi
    fun showStreamInfo() {
        val url = streamUrl ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        val player = getPlayer()
        lifecycleScope.launch {
            val source = standaloneHostFactory.getStreamSource(url)
            val outcome = source?.let { standaloneHostFactory.getStreamPlayOutcome(it.id) }
            StreamInfoDialog(
                context = activity,
                entity = source,
                url = url,
                playingEngine = player,
                lastPlayOutcome = outcome,
            ).show()
        }
    }

    /**
     * Share stream URL via system share sheet.
     */
    fun shareStreamUrl() {
        val url = streamUrl ?: return
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        activity.startActivity(Intent.createChooser(sendIntent, activity.getString(R.string.share)))
    }

    /**
     * Render track metadata or station name into header labels.
     */
    fun renderTrackInfo(meta: NowPlayingMetadata?, fallbackName: String) {
        val tvTitle = safeViews.tvStreamTrackTitle
        val tvArtist = safeViews.tvStreamTrackArtist
        if (meta != null) {
            tvTitle?.text = meta.title
            tvArtist?.text = meta.artist ?: ""
            tvArtist?.isVisible = !meta.artist.isNullOrBlank()
        } else {
            tvTitle?.text = fallbackName
            tvArtist?.text = ""
            tvArtist?.isVisible = false
        }
    }

    /**
     * Start wave/particle animation when audio stream starts playing.
     */
    fun startVisualizer() {
        visualizerView?.let { v ->
            v.visibility = View.VISIBLE
            v.startAnimation()
        }
    }

    /**
     * Pause wave/particle animation when playback is paused.
     */
    fun pauseVisualizer() {
        visualizerView?.pauseAnimation()
    }

    /**
     * Stop and reset visualizer when playback stops or finishes.
     */
    fun stopVisualizer() {
        visualizerView?.let { v ->
            v.stopAndReset()
            v.visibility = View.GONE
        }
    }
}
