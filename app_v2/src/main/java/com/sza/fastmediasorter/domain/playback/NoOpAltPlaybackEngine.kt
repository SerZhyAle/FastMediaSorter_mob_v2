package com.sza.fastmediasorter.domain.playback

import android.net.Uri
import android.view.ViewGroup
import com.sza.fastmediasorter.domain.model.MediaFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1060: the [AltPlaybackEngine] every flavor except `noLegal` gets. Answering "cannot play" to
 * everything is what keeps the alternative-engine fallback path inert outside `noLegal`: the
 * selector never picks this engine, so the primary player handles every file exactly as before.
 */
@Singleton
class NoOpAltPlaybackEngine @Inject constructor() : AltPlaybackEngine {

    override val engineId: String = "noop"

    override fun canPlay(file: MediaFile): Boolean = false

    override fun attach(container: ViewGroup) = Unit

    override fun play(uri: Uri, startPositionMs: Long) = Unit

    override fun pause() = Unit

    override fun resume() = Unit

    override fun seekTo(positionMs: Long) = Unit

    override val positionMs: Long = 0L

    override val durationMs: Long = 0L

    override val isPlaying: Boolean = false

    override fun setListener(listener: AltPlaybackEngine.Listener?) = Unit

    override fun release() = Unit
}
