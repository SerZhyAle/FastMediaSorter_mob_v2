package com.sza.fastmediasorter.ui.player.helpers

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import timber.log.Timber

/**
 * S1127: Media3 [AnalyticsListener] adapter for the internet-stream player. Translates the low-frequency
 * analytics callbacks (first frame, dropped-frame counter, decoder init, buffering<->ready) into
 * [StreamPlaybackDiagnostics] and emits permanent Timber diagnostics. These are long-lived operational
 * logs, so they carry NO `Sxxxx` probe prefix. No per-frame work: `onDroppedVideoFrames` is Media3's
 * batched counter, not a per-frame callback, and the other events are rare state transitions.
 */
@UnstableApi
internal class StreamDiagnosticsAnalyticsListener(
    private val path: String,
    private val diagnostics: StreamPlaybackDiagnostics,
) : AnalyticsListener {

    override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
        diagnostics.onFirstFrameRendered()
        Timber.i("Stream diag: first frame ttff=%dms path=%s", diagnostics.timeToFirstFrameMs, path)
    }

    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
        diagnostics.onDroppedFrames(droppedFrames)
        Timber.d("Stream diag: dropped=%d total=%d path=%s", droppedFrames, diagnostics.droppedFrames, path)
    }

    override fun onVideoDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        val hardware = isHardwareDecoder(decoderName)
        diagnostics.onDecoderInitialized(decoderName, hardware)
        Timber.i("Stream diag: decoder=%s (%s) path=%s", decoderName, if (hardware) "hw" else "sw", path)
    }

    override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
        when (state) {
            Player.STATE_BUFFERING -> diagnostics.onStallStarted()
            Player.STATE_READY -> diagnostics.onStallEnded()
            else -> Unit
        }
    }

    private companion object {
        /** Google/AOSP reference codecs are software; any vendor decoder name (OMX.qcom, c2.qti, ..) is HW. */
        fun isHardwareDecoder(name: String): Boolean =
            !(name.startsWith("c2.android.") || name.startsWith("OMX.google."))
    }
}
