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

    // S0772: heap-bounded buffer cap.
    // ExoPlayer's DefaultAllocator backs the buffer with byte[] on the *Java heap*, not native
    // memory. With setPrioritizeTimeOverSizeThresholds(true) and no explicit targetBufferBytes,
    // DefaultLoadControl buffers maxBufferMs worth of data regardless of bitrate: a ~40 Mbit/s 7K
    // HEVC stream fills ~150 MB, which on a 512 MB-heap headset (Quest 3, LOW tier) drives the heap
    // to its growth limit and OOM-crashes the ExoPlayer:Playback thread.
    //
    // Devices whose Java heap-limit is <= this threshold get a hard, heap-derived byte cap on the
    // video buffer (and prioritizeTimeOverSizeThresholds disabled so the cap is actually enforced
    // below minBufferMs). This matches the LOW-tier classifier in MemoryTier.classify(), which also
    // treats maxHeapMb <= 512 as the dominant low-memory signal.
    private const val HEAP_BOUNDED_TIER_MAX_HEAP_MB = 512L

    // Video buffer byte budget = heapMax / 8, clamped to a sane window. For a 512 MB heap this is
    // 64 MB - enough to avoid stutter on normal-bitrate content (which reaches maxBufferMs in time
    // before the cap binds) while capping pathological high-bitrate content well clear of OOM.
    private const val HEAP_BUDGET_DIVISOR = 8L
    private const val MIN_VIDEO_BUFFER_CAP_MB = 24L
    private const val MAX_VIDEO_BUFFER_CAP_MB = 96L
    private const val BYTES_PER_MB = 1024L * 1024L

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

        // S0772: bound the heap-resident video buffer on low-heap devices. Audio buffers are already
        // tiny (<= 20 s of low-bitrate data) so the cap never binds - leave that path untouched.
        val videoBufferCapBytes = if (!isAudio) heapBoundedVideoBufferCapBytes() else null

        val builder = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minMs, maxMs, playbackMs, rebufferMs)
        if (videoBufferCapBytes != null) {
            // Disable time-over-size priority so the byte cap is enforced even below minBufferMs;
            // for normal-bitrate content the cap is never reached, so buffering behaviour is
            // unchanged (it still fills to maxBufferMs before the byte budget binds).
            builder.setTargetBufferBytes(videoBufferCapBytes)
                .setPrioritizeTimeOverSizeThresholds(false)
            Timber.i(
                "PrefetchLoadControl[%s]: heap-bounded video buffer cap=%dMB (heapMax=%dMB)",
                tag,
                videoBufferCapBytes / BYTES_PER_MB,
                maxHeapMb()
            )
            Timber.d("S0772: video LoadControl heap-bounded cap=%dMB tag=%s", videoBufferCapBytes / BYTES_PER_MB, tag)
        } else {
            builder.setPrioritizeTimeOverSizeThresholds(true)
        }
        return PauseAwareLoadControl(builder.build())
    }

    /**
     * S0772: returns an explicit `targetBufferBytes` budget (in bytes) for the video buffer when the
     * current process runs on a low Java-heap device, or `null` to keep the unbounded time-priority
     * default. Uses `Runtime.maxMemory()` directly - the same heap-limit signal MemoryTier relies on -
     * so the factory stays self-contained and needs no injected MemoryProfile across its 5 call sites.
     */
    private fun heapBoundedVideoBufferCapBytes(): Int? = videoBufferCapBytesForHeap(maxHeapMb())

    private fun maxHeapMb(): Long = Runtime.getRuntime().maxMemory() / BYTES_PER_MB

    /**
     * S0772: pure cap resolver - exposed `internal` so it can be unit-tested without a live JVM heap,
     * mirroring [com.sza.fastmediasorter.core.util.MemoryTier.classify]. Returns the explicit video
     * `targetBufferBytes` budget for a process with [maxHeapMb] Java-heap limit, or `null` to keep the
     * unbounded time-priority default on devices that are not heap-constrained.
     */
    internal fun videoBufferCapBytesForHeap(maxHeapMb: Long): Int? {
        if (maxHeapMb > HEAP_BOUNDED_TIER_MAX_HEAP_MB) return null
        val capMb = (maxHeapMb / HEAP_BUDGET_DIVISOR)
            .coerceIn(MIN_VIDEO_BUFFER_CAP_MB, MAX_VIDEO_BUFFER_CAP_MB)
        return (capMb * BYTES_PER_MB).toInt()
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
