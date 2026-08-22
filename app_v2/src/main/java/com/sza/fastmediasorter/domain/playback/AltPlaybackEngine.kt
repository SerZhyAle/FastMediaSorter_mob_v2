package com.sza.fastmediasorter.domain.playback

import android.net.Uri
import android.view.ViewGroup
import com.sza.fastmediasorter.domain.model.MediaFile

/**
 * S1060: flavor extension point for alternative playback engines that must stay outside `src/main`.
 *
 * The contract is deliberately engine-free: no concrete engine symbol may appear in
 * `src/main`, because this source set compiles into every flavor including `standard`, and the
 * flavor boundary is the ticket's legal premise (strategic §3.2, Rule 14). "Can play this" is a
 * question about the media item, never about a codec name, so callers never need to know what a
 * given engine supports.
 *
 * Engines are contributed via Hilt set multibinding (see `AltPlaybackModule`); a caller picks the
 * first engine that answers [canPlay] positively and falls back to the primary player otherwise.
 */
interface AltPlaybackEngine {

    /** Stable identifier for logs and diagnostics, e.g. "noop". Never shown to the user. */
    val engineId: String

    /** Whether this engine can decode [file]. Judged on the item itself, not on a codec name. */
    fun canPlay(file: MediaFile): Boolean

    /**
     * Attach the engine's render output to [container]. The engine adds and owns its own view;
     * calling [release] must remove it again.
     */
    fun attach(container: ViewGroup)

    /** Start playback of [uri] from [startPositionMs]. Requires a prior [attach]. */
    fun play(uri: Uri, startPositionMs: Long)

    fun pause()

    fun resume()

    fun seekTo(positionMs: Long)

    /** Current position in milliseconds, or 0 when idle. */
    val positionMs: Long

    /** Total duration in milliseconds, or 0 when unknown. */
    val durationMs: Long

    val isPlaying: Boolean

    fun setListener(listener: Listener?)

    /** Stop playback, detach the render view and free every native resource. Idempotent. */
    fun release()

    /** Transport events an attached UI needs; delivered on the main thread. */
    interface Listener {

        fun onEnded()

        /** Unrecoverable playback failure; [message] is diagnostic, not user-facing copy. */
        fun onError(message: String)
    }
}
