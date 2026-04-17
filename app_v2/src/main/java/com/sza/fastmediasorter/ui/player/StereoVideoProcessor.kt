package com.sza.fastmediasorter.ui.player

import androidx.media3.common.Effect
import androidx.media3.effect.Crop
import com.sza.fastmediasorter.domain.model.StereoMode
import timber.log.Timber

/**
 * Applies stereoscopic rendering to ExoPlayer video output.
 *
 * Architecture note:
 * Full OpenGL-based stereo rendering requires [androidx.media3:media3-effect] (GlEffect API),
 * which is not yet included in the project dependencies.
 *
 * This class is a Phase 1 stub that:
 *  1. Tracks the current [StereoMode] and exposes it to the UI.
 *  2. Provides the hook point for Phase 2 GL implementation (see PLAN/task_3d-sbs-support-implementation.md § 3.2).
 *  3. Does NOT apply any visual transformation yet — video renders as mono until Phase 2.
 *
 * Phase 2 implementation plan:
 *  • Add `media3-effect:1.2.1` to [app_v2/build.gradle.kts].
 *  • Implement [androidx.media3.effect.GlEffect] to perform per-frame crop:
 *       left 50% of texture  →  left viewport (left eye)
 *       right 50% of texture →  right viewport (right eye)
 *  • Wire via [ExoPlayer.setVideoEffects(listOf(stereoVideoProcessor))].
 *
 * Thread safety: [setStereoMode] may be called from the main thread;
 * in Phase 2 the GL callbacks will arrive on the GL thread — use @Volatile or
 * AtomicReference for the mode field.
 */
class StereoVideoProcessor {

    @Volatile
    private var currentMode: StereoMode = StereoMode.MONO

    /**
     * Whether stereo rendering is currently active.
     * Phase 1: always false (stub). Phase 2: reflects GL pipeline state.
     */
    val isStereoActive: Boolean
        get() = currentMode == StereoMode.SBS_FULL || currentMode == StereoMode.SBS_HALF

    /**
     * Update the desired stereo mode.
     *
     * In Phase 2 this will also reconfigure the GL shader program.
     * [StereoMode.AUTO] and [StereoMode.UNKNOWN] must be resolved by the caller
     * (via [StereoDetector]) before calling this method.
     */
    fun setStereoMode(mode: StereoMode) {
        require(mode != StereoMode.AUTO && mode != StereoMode.UNKNOWN) {
            "setStereoMode: caller must resolve AUTO/UNKNOWN via StereoDetector first"
        }

        if (currentMode == mode) return

        val previous = currentMode
        currentMode = mode
        Timber.i("StereoVideoProcessor: mode changed $previous → $mode")

        // TODO (Phase 2): reconfigure GL pipeline here
        if (isStereoActive) {
            Timber.d("StereoVideoProcessor: stereo rendering requested but GL pipeline not yet implemented (Phase 1 stub)")
        }
    }

    /**
     * Returns the currently active mode (resolved — never AUTO or UNKNOWN).
     */
    fun getCurrentMode(): StereoMode = currentMode

    /**
     * Builds the [Effect] to apply for the given [mode].
     *
     * SBS_FULL / SBS_HALF → Crop to the left 50% of the frame (left-eye view).
     *   This fills the phone screen with the correct aspect ratio so the user can
     *   see the content naturally. When using a phone-based VR viewer (Google Cardboard),
     *   the SBS video should be played AS-IS (return null) — the headset's optics split it.
     * OU → Crop to the top 50% of the frame (left-eye view for Over-Under format).
     * MONO / AUTO / UNKNOWN → null (no effect = full-frame pass-through).
     *
     * Called by [VideoPlayerManager.applyStereoEffect] whenever the stereo mode changes.
     */
    fun buildGlEffect(mode: StereoMode): Effect? {
        return when (mode) {
            // Left half of the SBS frame → full screen (left eye, correct AR for non-VR preview)
            StereoMode.SBS_FULL, StereoMode.SBS_HALF -> {
                Timber.d("StereoVideoProcessor: buildGlEffect → Crop left 50% for $mode")
                Crop(
                    /* left  */ -1f,
                    /* right */ 0f,
                    /* bottom */ -1f,
                    /* top   */ 1f
                )
            }
            // Top half of the OU frame → full screen (left eye for Over-Under format)
            StereoMode.OU -> {
                Timber.d("StereoVideoProcessor: buildGlEffect → Crop top 50% for OU")
                Crop(
                    /* left  */ -1f,
                    /* right */ 1f,
                    /* bottom */ 0f,
                    /* top   */ 1f
                )
            }
            // No visual transformation for mono / unresolved modes
            else -> {
                Timber.d("StereoVideoProcessor: buildGlEffect → no effect for $mode")
                null
            }
        }
    }

    /**
     * Release resources. Call when the player is destroyed.
     * Phase 1: no-op. Phase 2: release FBOs and GL textures.
     */
    fun release() {
        currentMode = StereoMode.MONO
        Timber.d("StereoVideoProcessor: released")
    }
}
