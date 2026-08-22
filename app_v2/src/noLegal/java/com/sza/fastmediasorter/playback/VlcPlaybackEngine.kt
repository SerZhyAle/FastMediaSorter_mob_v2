package com.sza.fastmediasorter.playback

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.playback.AltPlaybackEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1060: [AltPlaybackEngine] over libVLC - software decoding of codecs the primary player refuses
 * (HEVC/VC-1/MPEG-2 on devices without the hardware decoder). Exists only in `src/noLegal/`; the
 * flavor boundary is the ticket's legal premise.
 *
 * Phase 02 scope: local files only. The network bridge (media callbacks over seekable DataSource)
 * and ISO routing are phases 04/05 and widen [canPlay] when they land.
 *
 * Ownership: this class is the single owner of the native handles. [LibVLC] and [MediaPlayer] are
 * created lazily on first use - never at app start - and [release] is idempotent: it tears down
 * the player, the render view and the native library object, and a second call is a no-op.
 */
@Singleton
class VlcPlaybackEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : AltPlaybackEngine {

    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var videoLayout: VLCVideoLayout? = null
    private var hostContainer: ViewGroup? = null
    private var listener: AltPlaybackEngine.Listener? = null

    override val engineId: String = "libvlc"

    override fun canPlay(file: MediaFile): Boolean =
        file.type == MediaType.VIDEO && file.path.startsWith("/")

    override fun attach(container: ViewGroup) {
        detachRenderView()
        val layout = VLCVideoLayout(container.context)
        container.addView(
            layout,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        videoLayout = layout
        hostContainer = container
        // enableSubtitles=false, useTextureView=false: SurfaceView rendering per owner 2026-07-18.
        player().attachViews(layout, null, false, false)
    }

    override fun play(uri: Uri, startPositionMs: Long) {
        val mp = player()
        val media = Media(requireLibVlc(), uri)
        // Hardware first with software fallback inside libVLC; force=false keeps its own choice.
        media.setHWDecoderEnabled(true, false)
        mp.media = media
        media.release()
        mp.play()
        if (startPositionMs > 0) mp.time = startPositionMs
    }

    override fun pause() {
        mediaPlayer?.takeIf { it.isPlaying }?.pause()
    }

    override fun resume() {
        mediaPlayer?.takeIf { !it.isPlaying }?.play()
    }

    override fun seekTo(positionMs: Long) {
        mediaPlayer?.time = positionMs
    }

    override val positionMs: Long
        get() = mediaPlayer?.time ?: 0L

    override val durationMs: Long
        get() = mediaPlayer?.length ?: 0L

    override val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying == true

    override fun setListener(listener: AltPlaybackEngine.Listener?) {
        this.listener = listener
    }

    override fun release() {
        val mp = mediaPlayer
        mediaPlayer = null
        if (mp != null) {
            mp.stop()
            mp.detachViews()
            mp.setEventListener(null)
            mp.release()
        }
        detachRenderView()
        libVlc?.release()
        libVlc = null
        Timber.d("VlcPlaybackEngine released")
    }

    private fun player(): MediaPlayer {
        mediaPlayer?.let { return it }
        val mp = MediaPlayer(requireLibVlc())
        mp.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.EndReached -> listener?.onEnded()
                MediaPlayer.Event.EncounteredError -> listener?.onError("libVLC EncounteredError")
            }
        }
        mediaPlayer = mp
        return mp
    }

    private fun requireLibVlc(): LibVLC =
        libVlc ?: LibVLC(context, arrayListOf("--no-sub-autodetect-file")).also { libVlc = it }

    private fun detachRenderView() {
        videoLayout?.let { layout -> hostContainer?.removeView(layout) }
        videoLayout = null
        hostContainer = null
    }
}
