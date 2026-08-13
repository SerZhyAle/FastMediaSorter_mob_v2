package com.sza.fastmediasorter.ui.xr.helpers

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.media3.common.text.CueGroup
import com.sza.fastmediasorter.core.xr.runtime.DiagnosticXrRuntime
import java.nio.ByteBuffer
import timber.log.Timber

/**
 * S0986: owns the subtitle-cue bitmap/buffer and pushes rendered cue text to the immersive subtitle
 * quad via [DiagnosticXrRuntime.queueSubtitle]. Kept OUT of `DiagnosticXrActivity` so the Activity
 * does not grow past its LOC ceiling (S0989).
 *
 * Threading: main-thread only, mirroring `renderPanelHud` - ExoPlayer cue callbacks arrive on the
 * app main thread that built the player, and `queueSubtitle` is itself thread-safe (native stores
 * under its own mutex). The bitmap/buffer are reused across cues; an empty cue hides the quad.
 */
class SubtitleCueController(private val runtime: DiagnosticXrRuntime) {

    private val renderer = SubtitleCueRenderer()
    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null
    private var buffer: ByteBuffer? = null

    // Dedup: onCues can re-emit identical text; skip redundant bitmap renders + native uploads.
    private var lastText: String? = null

    /** Extract non-empty cue text from [cueGroup] and push it, or hide the quad for an empty group. */
    fun submit(cueGroup: CueGroup) {
        val text = cueGroup.cues
            .mapNotNull { it.text?.toString()?.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        submitText(text)
    }

    /** Push [text] to the quad, or hide it when blank. Visible for the Activity and unit tests. */
    fun submitText(text: String) {
        if (text == lastText) return
        lastText = text
        if (text.isBlank()) {
            runtime.queueSubtitle(EMPTY, 0, 0)
            return
        }
        val bytes = renderToBytes(text)
        if (bytes == null) {
            runtime.queueSubtitle(EMPTY, 0, 0)
            return
        }
        runtime.queueSubtitle(bytes, SubtitleCueRenderer.WIDTH, SubtitleCueRenderer.HEIGHT)
    }

    private fun renderToBytes(text: String): ByteArray? {
        val bmp = bitmap ?: Bitmap.createBitmap(
            SubtitleCueRenderer.WIDTH, SubtitleCueRenderer.HEIGHT, Bitmap.Config.ARGB_8888
        ).also { bitmap = it; canvas = Canvas(it) }
        val cnv = canvas ?: return null
        renderer.render(cnv, text)
        val buf = reusableBuffer()
        bmp.copyPixelsToBuffer(buf)
        buf.rewind()
        val bytes = ByteArray(buf.remaining())
        buf.get(bytes)
        return bytes
    }

    private fun reusableBuffer(): ByteBuffer {
        val size = SubtitleCueRenderer.WIDTH * SubtitleCueRenderer.HEIGHT * RGBA_BYTES
        val current = buffer
        if (current != null) {
            current.clear()
            return current
        }
        return ByteBuffer.allocateDirect(size).also { buffer = it }
    }

    /** Hide the quad and drop bitmap/buffer references on playback teardown. */
    fun release() {
        runCatching { runtime.queueSubtitle(EMPTY, 0, 0) }
        bitmap?.recycle()
        bitmap = null
        canvas = null
        buffer = null
        lastText = null
    }

    private companion object {
        val EMPTY = ByteArray(0)
        const val RGBA_BYTES = 4
    }
}
