package com.sza.fastmediasorter.ui.player.helpers

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderCounters
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

    /**
     * S1510: the rendered-frame counter, captured once and read on every later sample.
     *
     * Media3 hands back the very object the renderer keeps writing to for as long as it stays enabled,
     * not a snapshot, which is what makes a periodic difference possible at all. Null until the video
     * renderer comes up, so an audio-only stream simply never has one.
     */
    @Volatile
    var videoCounters: DecoderCounters? = null
        private set

    override fun onVideoEnabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) {
        videoCounters = decoderCounters
    }

    override fun onVideoDisabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) {
        videoCounters = null
    }

    /**
     * S1510: the counter is written on the playback thread and read on another, and the class's own
     * contract says to call this before reading. Without it the sampler can read a stale total and
     * report a dip that never happened.
     */
    fun renderedFrames(): Int? = videoCounters?.let {
        it.ensureUpdated()
        it.renderedOutputBufferCount
    }

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
