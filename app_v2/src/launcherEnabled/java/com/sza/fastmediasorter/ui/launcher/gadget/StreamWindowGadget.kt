package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.streams.FaviconAtlasStore
import com.sza.fastmediasorter.data.repository.streams.StreamFrameCache
import com.sza.fastmediasorter.databinding.GadgetLauncherStreamWindowBinding
import com.sza.fastmediasorter.databinding.GadgetLauncherStreamWindowPlayerBinding
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.usecase.streams.GetStreamSourceByIdentityUseCase
import com.sza.fastmediasorter.ui.launcher.gadget.nowplaying.NowPlayingCommand
import com.sza.fastmediasorter.ui.launcher.gadget.nowplaying.OwnSessionNowPlayingSource
import com.sza.fastmediasorter.ui.player.helpers.StreamDataSourceFactoryProvider
import com.sza.fastmediasorter.ui.streams.FaviconAtlasSlicer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import timber.log.Timber
import javax.inject.Inject

/**
 * S2031: one channel of the stream catalog, played and steered from its own desktop cell.
 *
 * Deliberately not a second [StreamsGadget]: that one is a list of the whole catalog and says nothing
 * about what is playing. This cell is bound to ONE channel - it names that channel before it says what
 * its state is, and its play button starts THAT channel.
 *
 * It drives no playback stack of its own. A radio channel goes through the same host command every other
 * desktop cell uses, so there is exactly one place that talks to the playback service.
 */
class StreamWindowGadget @Inject constructor(
    private val getStreamByIdentity: GetStreamSourceByIdentityUseCase,
    private val faviconAtlasStore: FaviconAtlasStore,
    private val streamFrameCache: StreamFrameCache,
) : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_STREAM_WINDOW
    override val defaultSpanW: Int = StreamWindow.AUDIO_SPAN
    override val defaultSpanH: Int = StreamWindow.AUDIO_SPAN
    override val labelRes: Int = R.string.launcher_gadget_stream_window
    override val iconRes: Int = R.drawable.ic_cast

    // S2062: ic_cast fills white and is invisible on the picker's light surface without a tint.
    override val iconTintable: Boolean = true
    override val requiresResourceParam: Boolean = false

    /**
     * Held here, NOT in the view, for the reason [StreamsGadget] states: the slicer decodes the shipped
     * atlas once and crops tiles off the cached bitmap, and a view-scoped slicer would re-decode all of
     * it every time the desktop rebuilt its cells.
     */
    private val faviconSlicer = FaviconAtlasSlicer { faviconAtlasStore.atlasFile() }

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        StreamWindowGadgetView(
            context = container.context,
            host = host,
            identityKey = param,
            getStreamByIdentity = getStreamByIdentity,
            faviconAtlasStore = faviconAtlasStore,
            faviconSlicer = faviconSlicer,
            streamFrameCache = streamFrameCache,
        )
}

@UnstableApi
private class StreamWindowGadgetView(
    context: Context,
    private val host: LauncherGadgetHost,
    private val identityKey: String?,
    private val getStreamByIdentity: GetStreamSourceByIdentityUseCase,
    private val faviconAtlasStore: FaviconAtlasStore,
    private val faviconSlicer: FaviconAtlasSlicer,
    private val streamFrameCache: StreamFrameCache,
) : LauncherGadgetView(context) {

    private val binding =
        GadgetLauncherStreamWindowBinding.inflate(LayoutInflater.from(context), this)

    private val ownSession = OwnSessionNowPlayingSource(context)

    /** The channel this cell is bound to, or null until it is resolved (and while it is unknown). */
    private var source: StreamSourceEntity? = null

    /**
     * The video face, inflated at most once and only for a video channel.
     *
     * A radio cell never holds it, which is the whole reason the stub is in the layout: a desktop full of
     * radio channels then holds no video surface at all.
     */
    private var playerFace: GadgetLauncherStreamWindowPlayerBinding? = null

    /**
     * Created on the first tap and released on both exits, never held past the cell.
     *
     * A desktop rebuild drops and rebuilds every cell view with no recycler callback to hook, so detach
     * is one honest place to hand a codec back - and leaving the launcher stops the lifecycle without
     * detaching, so the active scope's exit is the other. A player released on only one of them keeps
     * decoding behind whatever the user opened instead.
     */
    private var player: ExoPlayer? = null

    /** Held so the release path can detach it: a listener is added per player, so it is removed per player. */
    private var errorListener: Player.Listener? = null

    init {
        binding.streamWindowPlayPause.setOnClickListener { playOrPause() }
    }

    /**
     * Polls rather than subscribes, exactly as the media windows do and for the same reason: the service
     * publishes its state as a snapshot blob with no change feed. The loop is bounded by the base class -
     * it runs only while this cell is attached and the launcher is STARTED.
     */
    override suspend fun CoroutineScope.onActive() {
        val resolved = identityKey?.let { getStreamByIdentity(it) }
        if (resolved == null) {
            showUnavailable()
            return
        }
        source = resolved
        Timber.d("S2031: cell bound to ${resolved.title} kind=${resolved.mediaKind}")
        binding.streamWindowTitle.text = resolved.title
        val tile = faviconTile(resolved)
        if (tile != null) {
            binding.streamWindowIcon.setImageBitmap(tile)
            // A favicon must not be tinted; the fallback ic_cast fills white and would vanish without it.
            binding.streamWindowIcon.imageTintList = null
        }
        if (StreamWindow.isVideoKind(resolved.mediaKind)) {
            showVideoFace(resolved, tile)
            return
        }
        while (isActive) {
            render()
            delay(StreamWindow.POLL_MS)
        }
    }

    /**
     * The video face: the channel's last frame while stopped, its player while running.
     *
     * Suspends for the life of the cell rather than returning, so the `finally` covers the two exits the
     * class KDoc of [player] names.
     */
    private suspend fun showVideoFace(source: StreamSourceEntity, tile: Bitmap?) {
        val face = inflatePlayerFace()
        binding.streamWindowAudioFace.isVisible = false
        face.streamWindowPlayerTitle.text = source.title
        // The channel's own last frame when the app has one, otherwise its icon - strategic §6 item 3
        // rules out a third kind of placeholder.
        face.streamWindowPoster.setImageBitmap(streamFrameCache.get(source.url) ?: tile)
        face.streamWindowPlayer.setOnClickListener { toggleVideo(source, face) }
        try {
            awaitCancellation()
        } finally {
            release()
        }
    }

    private fun inflatePlayerFace(): GadgetLauncherStreamWindowPlayerBinding =
        playerFace ?: GadgetLauncherStreamWindowPlayerBinding
            .bind(
                binding.streamWindowPlayerStub.apply {
                    layoutResource = R.layout.gadget_launcher_stream_window_player
                }.inflate()
            )
            .also { playerFace = it }

    /**
     * Starts the channel on the first tap and pauses it on the next.
     *
     * Pausing keeps the player attached on purpose: the surface then holds the last decoded frame, which
     * is the "freezes on a thumbnail" strategic §2.4 asks for. Nothing starts before a tap - a home
     * screen may not begin playing on its own (strategic §2.5).
     */
    private fun toggleVideo(source: StreamSourceEntity, face: GadgetLauncherStreamWindowPlayerBinding) {
        Timber.d("S2031: video tap, running=${player != null}")
        val running = player
        if (running != null) {
            // The poster stays hidden from the first start on: a paused player keeps its last decoded
            // frame on the surface, and re-showing the stored one would replace what the user just saw
            // with an older picture (strategic §2.4).
            running.playWhenReady = !running.playWhenReady
            return
        }
        binding.streamWindowMessage.isVisible = false
        val started = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(StreamDataSourceFactoryProvider.create(context)))
            .build()
        player = started
        face.streamWindowPlayer.player = started
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = failVideo(face)
        }
        errorListener = listener
        started.addListener(listener)
        started.setMediaItem(MediaItem.fromUri(Uri.parse(source.url)))
        // Audible by contract, not by oversight: the user picked a broadcast and pressed play
        // (strategic ADR-4), unlike the local video window which starts by itself and so stays muted.
        started.playWhenReady = true
        started.prepare()
        face.streamWindowPoster.isVisible = false
    }

    /**
     * A stream that failed hands the codec back and returns the cell to its stopped state, so the next
     * tap is a fresh attempt rather than a tap on a dead player.
     */
    private fun failVideo(face: GadgetLauncherStreamWindowPlayerBinding) {
        release()
        face.streamWindowPoster.isVisible = true
        binding.streamWindowMessage.setText(R.string.launcher_home_cell_unavailable)
        binding.streamWindowMessage.isVisible = true
        // The message is not clickable, so the tap that clears it reaches the player underneath.
        binding.streamWindowMessage.isClickable = false
    }

    /** Idempotent: the active scope's exit and a detach can both reach it, in either order. */
    private fun release() {
        playerFace?.streamWindowPlayer?.player = null
        errorListener?.let { player?.removeListener(it) }
        errorListener = null
        player?.release()
        player = null
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    /** The catalog's own icon for the channel, cropped from the packed atlas - never fetched online. */
    private suspend fun faviconTile(source: StreamSourceEntity): Bitmap? {
        val coords = runCatching { faviconAtlasStore.coords() }.getOrDefault(emptyMap())
        return coords[source.url]?.let { faviconSlicer.tileFor(it) }
    }

    /**
     * The transport state of THIS channel, not of whatever the service happens to play.
     *
     * The snapshot carries the title the channel was started with, which is the only thing tying a
     * playing session back to a cell - the service knows nothing about desktop cells.
     */
    private fun render() {
        val state = ownSession.read()
        val playingThisChannel = state.isPlaying && state.title == source?.title
        binding.streamWindowPlayPause.setImageResource(
            if (playingThisChannel) R.drawable.ic_pause else R.drawable.ic_play
        )
        binding.streamWindowState.text = when {
            playingThisChannel -> state.title
            else -> context.getString(R.string.launcher_gadget_stream_window_stopped)
        }
    }

    /**
     * Pauses the running session when it is this channel, and otherwise asks the desktop to start this
     * channel.
     *
     * Starting goes through the host command rather than the service directly: a stopped service cannot
     * be started from the background on API 26+, and that command is also what keeps the user's
     * persistent-audio setting in charge of whether playback happens without a screen (strategic ADR-3).
     */
    private fun playOrPause() {
        val identity = identityKey ?: return
        val state = ownSession.read()
        Timber.d("S2031: radio tap, playing=${state.isPlaying} title=${state.title}")
        if (state.isPlaying && state.title == source?.title) {
            ownSession.send(NowPlayingCommand.PLAY_PAUSE)
        } else {
            host.run(LauncherCellCommand.Stream(identity))
        }
    }

    /** A cell can outlive the catalog row it was created from, so it says so instead of drawing blank. */
    private fun showUnavailable() {
        binding.streamWindowAudioFace.isVisible = false
        binding.streamWindowMessage.setText(R.string.launcher_home_cell_unavailable)
        binding.streamWindowMessage.isVisible = true
    }
}
