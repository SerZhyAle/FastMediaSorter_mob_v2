package com.sza.fastmediasorter.ui.player.helpers

import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import com.sza.fastmediasorter.domain.model.PrefetchPlan
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import timber.log.Timber

/**
 * Builds an ExoPlayer [LoadControl] from an optional [PrefetchPlan].
 *
 * When [plan] is non-null, uses the plan's _Sec fields × 1000 for
 * `setBufferDurationsMs(minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs)`.
 *
 * When [plan] is null (no settings yet / non-video sessions / tests), falls back to the
 * per-protocol legacy constants on [VideoPlayerManager]. Callers pass [useCloudDefaults] = true
 * to pick the cloud-specific legacy defaults.
 *
 * See: PLAN/spec_adaptive-playback-strategy.md §5.5.
 */
internal object PrefetchLoadControlFactory {

    fun build(plan: PrefetchPlan?, useCloudDefaults: Boolean = false, tag: String = ""): LoadControl {
        val minMs: Int
        val maxMs: Int
        val playbackMs: Int
        val rebufferMs: Int

        if (plan != null) {
            minMs = (plan.minPrefetchSec.coerceAtLeast(1)) * 1_000
            maxMs = (plan.maxBufferSec.coerceAtLeast(plan.targetPrefetchSec)) * 1_000
            playbackMs = (plan.minPrefetchSec.coerceAtLeast(1)) * 1_000
            rebufferMs = (plan.rebufferPrefetchSec.coerceAtLeast(plan.minPrefetchSec)) * 1_000
            Timber.d(
                "PrefetchLoadControl[%s]: plan viability=%s min=%dms max=%dms rebuffer=%dms",
                tag, plan.viability, minMs, maxMs, rebufferMs
            )
        } else if (useCloudDefaults) {
            minMs = VideoPlayerManager.CLOUD_MIN_BUFFER_MS
            maxMs = VideoPlayerManager.CLOUD_MAX_BUFFER_MS
            playbackMs = VideoPlayerManager.CLOUD_BUFFER_FOR_PLAYBACK_MS
            rebufferMs = VideoPlayerManager.CLOUD_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            Timber.d("PrefetchLoadControl[%s]: fallback cloud defaults", tag)
        } else {
            minMs = VideoPlayerManager.MIN_BUFFER_MS
            maxMs = VideoPlayerManager.MAX_BUFFER_MS
            playbackMs = VideoPlayerManager.BUFFER_FOR_PLAYBACK_MS
            rebufferMs = VideoPlayerManager.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            Timber.d("PrefetchLoadControl[%s]: fallback standard defaults", tag)
        }

        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(minMs, maxMs, playbackMs, rebufferMs)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }
}
