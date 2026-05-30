package com.sza.fastmediasorter.ui.player.helpers

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import timber.log.Timber

/**
 * ExoPlayer creation and video-effect pipeline setup.
 *
 * Extension functions on [VideoPlayerManager] - extracted here to reduce per-file CFG complexity
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
internal fun VideoPlayerManager.createPlayer(playerView: PlayerView, isAudio: Boolean = false): ExoPlayer {
    releasePlayer()

    val loadControl = PrefetchLoadControlFactory.build(
        plan = activePrefetchPlan,
        useCloudDefaults = false,
        isAudio = isAudio,
        useNetworkAudioDefaults = false,
        tag = if (isAudio) "createPlayer-audio" else "createPlayer"
    )

    val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    val renderersFactory = createPlaybackRenderersFactory(context)

    val player = ExoPlayer.Builder(context, renderersFactory)
        .setLoadControl(loadControl)
        .setAudioAttributes(audioAttributes, true) // handleAudioFocus=true
        .build()

    player.addListener(playerListener)
    player.addListener(loadControl)
    playerView.player = player
    currentPlayerView = playerView
    exoPlayer = player

    // New player instance - reset pipeline state so we don't skip a legitimate setVideoEffects()
    // call on the fresh instance when re-applying previously active effects.
    effectsPipelineActive = false
    videoSizeKnown = false
    pendingEffectsApply = false

    // Reapply the full effect chain to the fresh ExoPlayer instance.
    // Without this, config changes/player recreation silently drop active video adjustments.
    applyConfiguredVideoEffects()

    // Notify subscribers when the ExoPlayer instance is (re)created.
    try {
        onPlayerCreated?.invoke(player)
    } catch (t: Throwable) {
        Timber.w(t, "VideoPlayerManager: onPlayerCreated callback threw")
    }

    Timber.d("VideoPlayerManager: ExoPlayer created")
    return player
}

// ═══════════════════════════════════════════════════════════════════════════
// Video-effect pipeline
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Rebuild and apply the composed video-effect pipeline (stereo crop + hue + brightness).
 *
 * Debounced at 80 ms to coalesce rapid slider updates - Media3 1.2.x crashes
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
        Timber.d("VideoPlayerManager: applyConfiguredVideoEffects - no effects, pipeline already clean, skipping")
        return
    }

    // Media3 1.2.1 deferral: Presentation.createForWidthAndHeight crashes with -1,-1 when
    // setVideoEffects() is called before the decoder emits the first frame. Defer until
    // onVideoSizeChanged fires with valid dimensions.
    if (!videoSizeKnown && effects.isNotEmpty()) {
        pendingEffectsApply = true
        Timber.d("VideoPlayerManager: applyConfiguredVideoEffects deferred - video size not yet known")
        return
    }

    // Cancel any pending deferred call and reschedule with the latest effect list.
    pendingEffectsRunnable?.let { effectsHandler.removeCallbacks(it) }
    val runnable = Runnable {
        pendingEffectsRunnable = null
        exoPlayer?.setVideoEffects(effects)
        effectsPipelineActive = effects.isNotEmpty()
        Timber.d(
            "VideoPlayerManager: applyConfiguredVideoEffects applied - " +
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

// S0207 Phase 01: the former `MEMORY_DEBUG` extension was replaced by the `MemoryProbe`
// channel (`memoryProbe.record(MemoryCheckpoint.PRE_PLAY / AFTER_STATE_READY)`) at the
// VideoPlayerManager call sites. The dedicated extension lived here only to keep
// VideoPlayerManager smaller; with the unified probe channel it is no longer needed.
