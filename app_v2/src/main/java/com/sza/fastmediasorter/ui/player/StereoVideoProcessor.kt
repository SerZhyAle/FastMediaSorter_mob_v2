package com.sza.fastmediasorter.ui.player

import androidx.media3.common.Effect
import androidx.media3.effect.Crop
import com.sza.fastmediasorter.domain.model.StereoMode
import timber.log.Timber

/**
 * Applies stereoscopic rendering to ExoPlayer video output.
 *
 * For standard phone screens, SBS and OU content is displayed by cropping to the left/top eye
 * and letting the player scale the result to fill the screen. This gives a normal mono view of
 * one eye's perspective — the correct default for non-VR viewing.
 *
 * [StereoMode.MONO] / [StereoMode.AUTO] / [StereoMode.UNKNOWN] → no GL effect (full-frame).
 * [StereoMode.SBS_FULL] / [StereoMode.SBS_HALF] → left-eye crop via `Crop(-1, 0, -1, 1)`.
 * [StereoMode.OU] → top-eye crop via `Crop(-1, 1, 0, 1)`.
 *
 * Thread safety: [setStereoMode] is called from the main thread.
 * [buildGlEffect] is called from [VideoPlayerManager.applyConfiguredVideoEffects] on the main thread.
 */
class StereoVideoProcessor {

    @Volatile
    private var currentMode: StereoMode = StereoMode.MONO

    /**
     * Whether a stereo layout has been requested.
     *
     * This reflects the selected mode, not proof that the renderer has switched to a
     * dual-surface stereo pipeline. With the current single PlayerView implementation,
     * SBS/OU content is preserved as-is for VR viewers.
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
    }

    /**
     * Returns the currently active mode (resolved — never AUTO or UNKNOWN).
     */
    fun getCurrentMode(): StereoMode = currentMode

    /**
     * Builds the [Effect] to apply for the given [mode].
     *
     * SBS_FULL / SBS_HALF → crop the left eye (left half of frame) and let [PlayerView] scale it
     * to fill the screen.  The left eye occupies x=[-1, 0] in Media3 NDC, so `Crop(-1, 0, -1, 1)`
     * extracts exactly the left half. The resulting frame has half the original width, so the
     * player renders it at the correct aspect ratio without any additional transformation.
     *
     * OU → crop the top eye (top half of frame).  In GL NDC y increases upward, so the top half
     * is y=[0, 1].  `Crop(-1, 1, 0, 1)` extracts the top half for standard-screen viewing.
     *
     * MONO / AUTO / UNKNOWN → null (full-frame pass-through, no GL work).
     *
     * Called by [VideoPlayerManager.applyStereoEffect] whenever the stereo mode changes.
     */
    fun buildGlEffect(mode: StereoMode): Effect? {
        return when (mode) {
            StereoMode.SBS_FULL, StereoMode.SBS_HALF -> {
                // Left eye is the left half of the SBS frame (NDC x = -1..0).
                // Cropping to this region and letting the player scale it gives a standard
                // mono view of one eye — the correct default for a regular phone screen.
                Timber.d("StereoVideoProcessor: buildGlEffect → SBS left-eye crop for $mode")
                Crop(-1f, 0f, -1f, 1f)
            }
            StereoMode.OU -> {
                // Top eye occupies the top half of the OU frame (GL NDC y = 0..1).
                Timber.d("StereoVideoProcessor: buildGlEffect → OU top-eye crop")
                Crop(-1f, 1f, 0f, 1f)
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
