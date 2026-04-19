package com.sza.fastmediasorter.ui.player.helpers

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import timber.log.Timber

/**
 * ExoPlayer creation and video-effect pipeline setup.
 *
 * Extension functions on [VideoPlayerManager] — extracted here to reduce per-file CFG complexity
 * for the Kotlin compiler (avoids GC overhead limit during parallel flavor compilation of the
 * full 1 700-line original class with many coroutine state-machines).
 */

// ═══════════════════════════════════════════════════════════════════════════
// ExoPlayer creation
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Create and configure an ExoPlayer instance with standard network buffers.
 * Resets the video-effects pipeline so previously active effects are re-applied
 * to the fresh instance (avoids silent reset on config changes / player recreation).
 */
internal fun VideoPlayerManager.createPlayer(playerView: PlayerView): ExoPlayer {
    releasePlayer()

    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            VideoPlayerManager.MIN_BUFFER_MS,
            VideoPlayerManager.MAX_BUFFER_MS,
            VideoPlayerManager.BUFFER_FOR_PLAYBACK_MS,
            VideoPlayerManager.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    // P1-1: Enable SW decoder fallback so HW codec failures (e.g., VP8 on API 28) are
    // handled transparently without surface-mode errors or startup freezes.
    val renderersFactory = DefaultRenderersFactory(context)
        .setEnableDecoderFallback(true)

    val player = ExoPlayer.Builder(context, renderersFactory)
        .setLoadControl(loadControl)
        .setAudioAttributes(audioAttributes, true) // handleAudioFocus=true
        .build()

    player.addListener(playerListener)
    playerView.player = player
    currentPlayerView = playerView
    exoPlayer = player

    // New player instance — reset pipeline state so we don't skip a legitimate setVideoEffects()
    // call on the fresh instance when re-applying previously active effects.
    effectsPipelineActive = false

    // Reapply the full effect chain to the fresh ExoPlayer instance.
    // Without this, config changes/player recreation silently drop active video adjustments.
    applyConfiguredVideoEffects()

    Timber.d("VideoPlayerManager: ExoPlayer created")
    return player
}

// ═══════════════════════════════════════════════════════════════════════════
// Video-effect pipeline
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Rebuild and apply the composed video-effect pipeline (stereo crop + hue + brightness).
 *
 * Debounced at 80 ms to coalesce rapid slider updates — Media3 1.2.x crashes
 * (TexturePool.freeTexture → IllegalStateException) when setVideoEffects() is called
 * with in-flight frames still pending from the previous pipeline.
 */
internal fun VideoPlayerManager.applyConfiguredVideoEffects() {
    val effects = mutableListOf<Effect>()

    stereoVideoProcessor.buildGlEffect(stereoVideoProcessor.getCurrentMode())?.let(effects::add)
    videoColorProcessor.buildHueEffect()?.let(effects::add)
    videoColorProcessor.buildBrightnessEffect()?.let(effects::add)

    // Guard: skip scheduling when no effects are active and none were previously installed.
    if (effects.isEmpty() && !effectsPipelineActive) {
        Timber.d("VideoPlayerManager: applyConfiguredVideoEffects — no effects, pipeline already clean, skipping")
        return
    }

    // Cancel any pending deferred call and reschedule with the latest effect list.
    pendingEffectsRunnable?.let { effectsHandler.removeCallbacks(it) }
    val runnable = Runnable {
        pendingEffectsRunnable = null
        exoPlayer?.setVideoEffects(effects)
        effectsPipelineActive = effects.isNotEmpty()
        Timber.d(
            "VideoPlayerManager: applyConfiguredVideoEffects applied — " +
                "stereo=${stereoVideoProcessor.getCurrentMode()} " +
                "hue=${videoColorProcessor.getHueAdjustmentDegrees()} " +
                "brightness=${videoColorProcessor.getBrightnessAdjustment()} " +
                "effects=${effects.size}"
        )
    }
    pendingEffectsRunnable = runnable
    effectsHandler.postDelayed(runnable, 80L)
}

// ═══════════════════════════════════════════════════════════════════════════
// Brightness progress ↔ adjustment conversion helpers
// ═══════════════════════════════════════════════════════════════════════════

internal fun VideoPlayerManager.brightnessProgressToAdjustment(progress: Int): Float =
    ((progress.coerceIn(0, 100) - VideoPlayerManager.DEFAULT_BRIGHTNESS_PROGRESS) /
        VideoPlayerManager.DEFAULT_BRIGHTNESS_PROGRESS.toFloat())

internal fun VideoPlayerManager.brightnessAdjustmentToProgress(adjustment: Float): Int =
    ((adjustment.coerceIn(-1f, 1f) * VideoPlayerManager.DEFAULT_BRIGHTNESS_PROGRESS) +
        VideoPlayerManager.DEFAULT_BRIGHTNESS_PROGRESS).toInt()

// ═══════════════════════════════════════════════════════════════════════════
// Memory diagnostics
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Logs current heap and native memory usage for debugging OOM / memory-leak investigations.
 * @param logContext Contextual label for log filtering (e.g., "BEFORE playVideo").
 */
internal fun VideoPlayerManager.logMemoryStats(logContext: String) {
    try {
        val runtime = Runtime.getRuntime()

        val totalMemory = runtime.totalMemory() / (1024 * 1024)
        val freeMemory = runtime.freeMemory() / (1024 * 1024)
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val usedMemory = totalMemory - freeMemory
        val percentUsed = (usedMemory * 100) / maxMemory

        val nativeHeapSize = android.os.Debug.getNativeHeapSize() / (1024 * 1024)
        val nativeHeapAllocated = android.os.Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
        val nativeHeapFree = android.os.Debug.getNativeHeapFreeSize() / (1024 * 1024)

        Timber.i(
            "MEMORY_DEBUG [$logContext] | " +
                "Heap: ${usedMemory}MB/${maxMemory}MB (${percentUsed}%) | " +
                "Native: ${nativeHeapAllocated}MB/${nativeHeapSize}MB (free: ${nativeHeapFree}MB)"
        )
    } catch (e: Exception) {
        Timber.w(e, "MEMORY_DEBUG: Failed to log memory stats (VideoPlayer)")
    }
}
