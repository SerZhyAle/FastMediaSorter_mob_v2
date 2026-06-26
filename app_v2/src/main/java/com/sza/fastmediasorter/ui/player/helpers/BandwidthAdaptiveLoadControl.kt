package com.sza.fastmediasorter.ui.player.helpers

import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.BandwidthMeter

/**
 * S0688: bandwidth-adaptive runtime buffer for the stream player.
 *
 * Media3 1.2.1 has no built-in mechanism that grows/shrinks the buffer in response to a live
 * [BandwidthMeter] estimate - [DefaultLoadControl] reads its durations once at construction. This
 * decorator keeps the delegate's memory/start/rebuffer behaviour but overrides [shouldContinueLoading]
 * to cap the steady cushion at a *dynamic* target derived from the measured throughput: a weak/unstable
 * link widens the cushion (toward a deeper ceiling) so the playhead rides out gaps; a healthy link
 * tightens it back to the modest floor.
 *
 * Live-edge guard (must not regress S0634): for live content the ceiling is clamped to the floor, so
 * the adaptive growth is a no-op and the buffer stays at the proven live-safe depth - a deeper backlog
 * would only pin the playhead next to the expiring segment and revive `BehindLiveWindow` drops. Only
 * non-live streams (VOD, progressive audio/video, RTSP) get the deepening. Liveness is read from
 * `targetLiveOffsetUs` in [shouldStartPlayback]; until that first runs we assume live (conservative).
 *
 * One instance per stream session (the stream player is recreated per `playStreamVideo`), so liveness
 * never changes under a single instance.
 */
@UnstableApi
internal class BandwidthAdaptiveLoadControl private constructor(
    private val delegate: DefaultLoadControl,
    private val bandwidthMeter: BandwidthMeter,
) : LoadControl by delegate {

    // Conservative default: treat the stream as live until shouldStartPlayback proves otherwise, so a
    // live stream is never over-buffered during its initial fill before liveness is known.
    @Volatile
    private var isLive = true

    override fun shouldContinueLoading(
        playbackPositionUs: Long,
        bufferedDurationUs: Long,
        playbackSpeed: Float,
    ): Boolean {
        // Cap at the dynamic target first, then defer to the delegate for its memory/size thresholds.
        if (bufferedDurationUs >= currentTargetBufferUs()) return false
        return delegate.shouldContinueLoading(playbackPositionUs, bufferedDurationUs, playbackSpeed)
    }

    /**
     * Dynamic steady-cushion target (microseconds). Interpolates from the deep ceiling at/below the weak
     * bitrate threshold down to the modest floor at/above the healthy threshold. For live content the
     * ceiling equals the floor, so the result is constant (live-safe) regardless of bandwidth.
     */
    private fun currentTargetBufferUs(): Long {
        val ceilingMs = if (isLive) STREAM_ADAPTIVE_FLOOR_BUFFER_MS else STREAM_ADAPTIVE_VOD_CEILING_MS
        val estimateBps = bandwidthMeter.bitrateEstimate
        val targetMs = when {
            estimateBps <= STREAM_WEAK_BITRATE_BPS -> ceilingMs
            estimateBps >= STREAM_HEALTHY_BITRATE_BPS -> STREAM_ADAPTIVE_FLOOR_BUFFER_MS
            else -> {
                val span = (STREAM_HEALTHY_BITRATE_BPS - STREAM_WEAK_BITRATE_BPS).toDouble()
                val healthyFraction = (estimateBps - STREAM_WEAK_BITRATE_BPS) / span
                val depth = ceilingMs - STREAM_ADAPTIVE_FLOOR_BUFFER_MS
                ceilingMs - (healthyFraction * depth).toInt()
            }
        }
        return targetMs * 1_000L
    }

    override fun shouldStartPlayback(
        timeline: Timeline,
        mediaPeriodId: MediaPeriodId,
        bufferedDurationUs: Long,
        playbackSpeed: Float,
        rebuffering: Boolean,
        targetLiveOffsetUs: Long,
    ): Boolean {
        isLive = targetLiveOffsetUs != C.TIME_UNSET
        return delegate.shouldStartPlayback(
            timeline, mediaPeriodId, bufferedDurationUs, playbackSpeed, rebuffering, targetLiveOffsetUs
        )
    }

    // Kotlin `by delegate` routes Java default methods through LoadControl.DefaultImpls.<method>(this, ..)
    // instead of delegate.<method>(..). LoadControl has cross-calling default overloads
    // (shouldStartPlayback, onTracksSelected) that would recurse through `this` forever (StackOverflowError).
    // The explicit overrides below force dispatch to the DefaultLoadControl delegate. Mirrors PauseAwareLoadControl.
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun shouldStartPlayback(
        bufferedDurationUs: Long,
        playbackSpeed: Float,
        rebuffering: Boolean,
        targetLiveOffsetUs: Long,
    ): Boolean = delegate.shouldStartPlayback(bufferedDurationUs, playbackSpeed, rebuffering, targetLiveOffsetUs)

    override fun onTracksSelected(
        timeline: Timeline,
        mediaPeriodId: MediaPeriodId,
        renderers: Array<out Renderer>,
        trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection>,
    ) {
        delegate.onTracksSelected(timeline, mediaPeriodId, renderers, trackGroups, trackSelections)
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onTracksSelected(
        renderers: Array<out Renderer>,
        trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection>,
    ) {
        delegate.onTracksSelected(renderers, trackGroups, trackSelections)
    }

    companion object {
        /**
         * Builds the adaptive control over a [DefaultLoadControl] whose continue-loading band spans the
         * full [floor, VOD ceiling] range so the delegate permits loading up to the deepest target; the
         * actual per-call cap is applied by [shouldContinueLoading]. The start/rebuffer thresholds keep
         * the proven S0685 values (2.5s start, 8s post-rebuffer fill) and are not adapted.
         */
        fun create(bandwidthMeter: BandwidthMeter): BandwidthAdaptiveLoadControl {
            val delegate = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    STREAM_ADAPTIVE_FLOOR_BUFFER_MS,
                    STREAM_ADAPTIVE_VOD_CEILING_MS,
                    STREAM_ADAPTIVE_BUFFER_FOR_PLAYBACK_MS,
                    STREAM_ADAPTIVE_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
            return BandwidthAdaptiveLoadControl(delegate, bandwidthMeter)
        }
    }
}

// Healthy-signal steady cushion and live ceiling (ms). Equals the S0685 live-safe max, so on a healthy
// signal - and for live content at any signal - the buffer depth is unchanged and live-edge tracking holds.
private const val STREAM_ADAPTIVE_FLOOR_BUFFER_MS = 30_000

// Weak-signal ceiling for non-live streams (ms). The new deepening: VOD/progressive/RTSP may widen the
// cushion this far when throughput collapses, riding out gaps a 30s buffer would stall on.
private const val STREAM_ADAPTIVE_VOD_CEILING_MS = 60_000

// Start/rebuffer thresholds (ms) - proven S0685 values, not adapted. The post-rebuffer fill is the
// highest-leverage anti-restall knob; adapting it risks delaying resume indefinitely on a weak link.
private const val STREAM_ADAPTIVE_BUFFER_FOR_PLAYBACK_MS = 2_500
private const val STREAM_ADAPTIVE_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 8_000

// Bandwidth band (bits/sec) mapping estimate -> target depth. At/below weak -> deepest ceiling; at/above
// healthy -> floor; linear in between. ~1.5 Mbps spans a struggling SD video variant; ~5 Mbps a healthy
// 1080p variant. Below the weak floor (incl. an absent estimate) the cushion opens to its deepest.
private const val STREAM_WEAK_BITRATE_BPS = 1_500_000L
private const val STREAM_HEALTHY_BITRATE_BPS = 5_000_000L
