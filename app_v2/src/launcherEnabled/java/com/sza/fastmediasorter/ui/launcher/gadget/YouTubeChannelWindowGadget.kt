package com.sza.fastmediasorter.ui.launcher.gadget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.GadgetLauncherYoutubeChannelWindowBinding
import com.sza.fastmediasorter.databinding.GadgetLauncherYoutubeChannelWindowPlayerBinding
import com.sza.fastmediasorter.domain.model.youtube.YouTubeChannel
import com.sza.fastmediasorter.domain.model.youtube.YouTubeVideo
import com.sza.fastmediasorter.domain.usecase.youtube.GetLatestChannelVideoUseCase
import com.sza.fastmediasorter.util.resolveActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import timber.log.Timber
import javax.inject.Inject

/**
 * S2032: one YouTube channel, shown and played inside its own desktop cell.
 *
 * Deliberately not a reworked [YouTubeGadget]: strategic ADR-1 keeps that button cell exactly as it
 * ships, because it is already on desktops for a different purpose and rewriting it would silently
 * change the size and behaviour of cells nobody asked to change.
 *
 * The minimum resize bound equals the default size, as it does for every gadget except the clock, so a
 * user cannot shrink the cell below the 3x2 the owner specified (strategic §3.4).
 */
class YouTubeChannelWindowGadget @Inject constructor(
    private val getLatestVideo: GetLatestChannelVideoUseCase,
) : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_YOUTUBE_CHANNEL_WINDOW
    override val defaultSpanW: Int = YouTubeChannelWindow.SPAN_W
    override val defaultSpanH: Int = YouTubeChannelWindow.SPAN_H
    override val minSpanW: Int = YouTubeChannelWindow.SPAN_W
    override val minSpanH: Int = YouTubeChannelWindow.SPAN_H
    override val labelRes: Int = R.string.launcher_gadget_youtube_channel_window
    override val iconRes: Int = R.drawable.ic_youtube
    override val requiresResourceParam: Boolean = false

    override fun isAvailable(): Boolean = true

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        YouTubeChannelWindowGadgetView(
            context = container.context,
            param = param,
            getLatestVideo = getLatestVideo,
        )
}

/**
 * The cell itself: silent until pressed, and one of two things when pressed.
 *
 * Deliberately NOT modelled on [YouTubeGadget]'s view, which loads its page the moment the cell is
 * attached: strategic §2 goal 3 requires this cell to stay silent until the user acts, and strategic
 * §6.6 records that the shipped button cell does the opposite and stays as it is. The shape copied here
 * is [StreamWindowGadget]'s - press to start, press again to stop, release on both exits.
 */
@SuppressLint("SetJavaScriptEnabled")
private class YouTubeChannelWindowGadgetView(
    context: Context,
    param: String?,
    private val getLatestVideo: GetLatestChannelVideoUseCase,
) : LauncherGadgetView(context) {

    private val binding =
        GadgetLauncherYoutubeChannelWindowBinding.inflate(LayoutInflater.from(context), this)

    /** The channel this cell is bound to, decoded once - null only when the target holds nothing usable. */
    private val channel: YouTubeChannel? = YouTubeChannel.decode(param)

    /** The channel's latest upload, resolved once per attach and re-used by the play button. */
    private var latest: YouTubeVideo? = null

    /**
     * Created on the first press and released on BOTH exits, never held past the cell.
     *
     * A desktop rebuild drops and rebuilds every cell view without stopping the lifecycle, and leaving
     * the launcher stops the lifecycle without detaching the view - a web view released on only one of
     * them keeps a page, its sockets and its decoder alive behind whatever the user opened instead
     * (strategic §3.2 and §7).
     */
    private var playerFace: GadgetLauncherYoutubeChannelWindowPlayerBinding? = null

    private var isPlaying = false

    /** Read once: the embed is a property of the device's WebView, which does not change under the cell. */
    private val embedUsable = YouTubeEmbedAvailability.isEmbedUsable(context)

    init {
        val title = channel?.title.orEmpty()
        binding.youTubeChannelTitle.text = title
        contentDescription = title.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.launcher_gadget_youtube_channel_window)
        if (channel == null) {
            showMessage(R.string.launcher_youtube_channel_window_no_channel)
        }
        applyControlState()
        binding.youTubeChannelPlay.setOnClickListener { onPlayPressed() }
    }

    /**
     * The only network work this cell does on its own: the channel's latest upload, whose cover is the
     * stopped face (strategic §6.4) and whose id is what play starts (strategic §6.3).
     *
     * Suspends for the life of the cell rather than returning, so one `finally` covers both exits.
     */
    override suspend fun CoroutineScope.onActive() {
        val boundChannel = channel ?: return
        try {
            val video = getLatestVideo(boundChannel.channelId)
            if (video == null) {
                showMessage(R.string.launcher_youtube_channel_window_unavailable)
            } else {
                latest = video
                binding.youTubeChannelMessage.isVisible = false
                Glide.with(this@YouTubeChannelWindowGadgetView)
                    .load(video.thumbnailUrl)
                    .into(binding.youTubeChannelPoster)
            }
            awaitCancellation()
        } finally {
            release()
        }
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    private fun onPlayPressed() {
        if (channel == null) return
        if (!embedUsable) {
            openChannelExternally(channel)
            return
        }
        if (isPlaying) stopPlayback() else startPlayback()
    }

    /**
     * Strategic §6.2: playback runs only through the mechanism the service itself provides for
     * embedding, keeping its advertising and its controls. Strategic ADR-4 rules out reading a stream
     * address, so nothing here touches the extraction path that exists in one flavor.
     */
    private fun startPlayback() {
        val video = latest ?: run {
            showMessage(R.string.launcher_youtube_channel_window_unavailable)
            return
        }
        val face = inflatePlayerFace()
        face.youTubeChannelWebView.loadUrl(EMBED_URL + video.videoId)
        face.youTubeChannelPlayerFace.isVisible = true
        binding.youTubeChannelPoster.isVisible = false
        isPlaying = true
        applyControlState()
    }

    /**
     * Pausing keeps the web view attached on purpose: the cell then holds its last frame instead of
     * clearing to nothing, which is the behaviour strategic §3.4 carried over from the stream window.
     */
    private fun stopPlayback() {
        playerFace?.youTubeChannelWebView?.onPause()
        isPlaying = false
        applyControlState()
    }

    private fun inflatePlayerFace(): GadgetLauncherYoutubeChannelWindowPlayerBinding =
        playerFace ?: GadgetLauncherYoutubeChannelWindowPlayerBinding
            .bind(
                binding.youTubeChannelPlayerStub.apply {
                    layoutResource = R.layout.gadget_launcher_youtube_channel_window_player
                }.inflate()
            )
            .also { face ->
                face.youTubeChannelWebView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }
                face.youTubeChannelWebView.webChromeClient = WebChromeClient()
                playerFace = face
            }

    /**
     * Strategic §5 Столп 4: the fallback opens the CHOSEN channel, never the service's front page, and
     * says so when nothing on the device can open it rather than failing in silence (§11 criterion 7).
     */
    private fun openChannelExternally(channel: YouTubeChannel) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(CHANNEL_URL + channel.channelId))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.packageManager.resolveActivityCompat(intent) == null) {
            showMessage(R.string.launcher_youtube_channel_window_no_app)
            return
        }
        runCatching { context.startActivity(intent) }
            .onFailure {
                Timber.w(it, "YouTube channel window: failed to open channel %s", channel.channelId)
                showMessage(R.string.launcher_youtube_channel_window_no_app)
            }
    }

    /**
     * The playing and stopped states differ by icon AND by spoken description, never by colour alone -
     * strategic §3.2 requires both to be distinguishable without it. A cell that cannot embed says it
     * opens the channel, so it does not present itself as a player.
     */
    private fun applyControlState() {
        val descriptionRes = when {
            !embedUsable -> R.string.launcher_youtube_channel_window_open
            isPlaying -> R.string.launcher_youtube_channel_window_stop
            else -> R.string.launcher_youtube_channel_window_play
        }
        binding.youTubeChannelPlay.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        binding.youTubeChannelPlay.contentDescription = context.getString(descriptionRes)
    }

    private fun showMessage(@StringRes messageRes: Int) {
        binding.youTubeChannelMessage.setText(messageRes)
        binding.youTubeChannelMessage.isVisible = true
    }

    private fun release() {
        playerFace?.youTubeChannelWebView?.run {
            stopLoading()
            onPause()
            destroy()
        }
        playerFace = null
        isPlaying = false
    }

    private companion object {
        /** The service's own embed endpoint - the one mechanism strategic §6.2 permits. */
        const val EMBED_URL = "https://www.youtube.com/embed/"

        /** The chosen channel's own page, never the service's front page (strategic §2 goal 4). */
        const val CHANNEL_URL = "https://www.youtube.com/channel/"
    }
}
