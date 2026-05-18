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

    internal data class LegacyBufferDurations(
        val minMs: Int,
        val maxMs: Int,
        val playbackMs: Int,
        val rebufferMs: Int,
    )

    fun build(
        plan: PrefetchPlan?,
        useCloudDefaults: Boolean = false,
        isAudio: Boolean = false,
        useNetworkAudioDefaults: Boolean = false,
        tag: String = "",
    ): PauseAwareLoadControl {
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
        } else {
            val legacy = legacyBufferDurations(
                useCloudDefaults = useCloudDefaults,
                isAudio = isAudio,
                useNetworkAudioDefaults = useNetworkAudioDefaults,
            )
            minMs = legacy.minMs
            maxMs = legacy.maxMs
            playbackMs = legacy.playbackMs
            rebufferMs = legacy.rebufferMs

            when {
                isAudio && useNetworkAudioDefaults ->
                    Timber.d("PrefetchLoadControl[%s]: fallback network-audio defaults", tag)
                isAudio ->
                    Timber.d("PrefetchLoadControl[%s]: fallback local-audio defaults", tag)
                useCloudDefaults ->
                    Timber.d("PrefetchLoadControl[%s]: fallback cloud defaults", tag)
                else ->
                    // WHY S0168 §5.4: fallback to standard defaults means no PrefetchPlan was
                    // delivered before createPlayer() - elevate to W for visibility in log analysis.
                    Timber.w("PrefetchLoadControl[%s]: fallback standard defaults", tag)
            }
        }

        val defaultLoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minMs, maxMs, playbackMs, rebufferMs)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        return PauseAwareLoadControl(defaultLoadControl)
    }

    internal fun legacyBufferDurations(
        useCloudDefaults: Boolean,
        isAudio: Boolean,
        useNetworkAudioDefaults: Boolean,
    ): LegacyBufferDurations {
        return when {
            isAudio && useNetworkAudioDefaults -> LegacyBufferDurations(
                minMs = VideoPlayerManager.AUDIO_NETWORK_MIN_BUFFER_MS,
                maxMs = VideoPlayerManager.AUDIO_NETWORK_MAX_BUFFER_MS,
                playbackMs = VideoPlayerManager.AUDIO_NETWORK_BUFFER_FOR_PLAYBACK_MS,
                rebufferMs = VideoPlayerManager.AUDIO_NETWORK_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            isAudio -> LegacyBufferDurations(
                minMs = VideoPlayerManager.AUDIO_MIN_BUFFER_MS,
                maxMs = VideoPlayerManager.AUDIO_MAX_BUFFER_MS,
                playbackMs = VideoPlayerManager.AUDIO_BUFFER_FOR_PLAYBACK_MS,
                rebufferMs = VideoPlayerManager.AUDIO_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            useCloudDefaults -> LegacyBufferDurations(
                minMs = VideoPlayerManager.CLOUD_MIN_BUFFER_MS,
                maxMs = VideoPlayerManager.CLOUD_MAX_BUFFER_MS,
                playbackMs = VideoPlayerManager.CLOUD_BUFFER_FOR_PLAYBACK_MS,
                rebufferMs = VideoPlayerManager.CLOUD_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            else -> LegacyBufferDurations(
                minMs = VideoPlayerManager.MIN_BUFFER_MS,
                maxMs = VideoPlayerManager.MAX_BUFFER_MS,
                playbackMs = VideoPlayerManager.BUFFER_FOR_PLAYBACK_MS,
                rebufferMs = VideoPlayerManager.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
        }
    }
}
