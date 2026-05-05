package com.sza.fastmediasorter.ui.player

import androidx.media3.common.Effect
import androidx.media3.effect.Crop
import com.sza.fastmediasorter.domain.model.StereoMode
import timber.log.Timber

/**
 * Applies stereoscopic rendering to ExoPlayer video output.
 *
 * For standard phone screens, SBS and OU content is displayed by cropping to one fixed eye
 * (right half for SBS, bottom half for OU) and letting the player scale the result to fill
 * the screen. This gives a normal mono view of one eye's perspective — the correct default
 * for non-VR viewing.
 *
 * [StereoMode.MONO] / [StereoMode.AUTO] / [StereoMode.UNKNOWN] → no GL effect (full-frame).
 * Flat SBS: [StereoMode.SBS_FULL] / [StereoMode.SBS_HALF] → right-eye crop via `Crop(0, 1, -1, 1)`.
 * Flat OU: [StereoMode.OU] → bottom-eye crop via `Crop(-1, 1, -1, 0)`.
 * Equirect SBS: [StereoMode.EQUIRECT_360_SBS] / [StereoMode.EQUIRECT_180_SBS] /
 *   [StereoMode.VR180_FISHEYE_SBS] → same right-eye half crop as flat SBS.
 * Equirect OU: [StereoMode.EQUIRECT_360_OU] → same bottom-eye half crop as flat OU.
 * In panel mode, equirect content is not sphere-projected, so the same half-frame crop
 * applies as for flat stereo.
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
     * SBS_FULL / SBS_HALF / EQUIRECT_360_SBS / EQUIRECT_180_SBS / VR180_FISHEYE_SBS →
     * crop the right eye (right half of frame) and let [PlayerView] scale it to fill the screen.
     * The right eye occupies x=[0, 1] in Media3 NDC, so `Crop(0, 1, -1, 1)` extracts exactly
     * the right half. In panel mode equirect content is not sphere-projected, so the same
     * half-frame crop applies as for flat SBS — otherwise both eyes are shown side by side.
     *
     * OU / EQUIRECT_360_OU → crop the bottom eye (bottom half of frame). In GL NDC y increases
     * upward, so the bottom half is y=[-1, 0]. `Crop(-1, 1, -1, 0)` extracts the bottom half.
     *
     * MONO / AUTO / UNKNOWN / EQUIRECT_*_MONO / CYLINDER_180 → null (full-frame pass-through).
     *
     * Called by [VideoPlayerManager.applyStereoEffect] whenever the stereo mode changes.
     */
    fun buildGlEffect(mode: StereoMode): Effect? {
        return when (mode) {
            StereoMode.SBS_FULL, StereoMode.SBS_HALF,
            StereoMode.EQUIRECT_360_SBS, StereoMode.EQUIRECT_180_SBS,
            StereoMode.VR180_FISHEYE_SBS -> {
                // Right eye is the right half of the SBS frame (NDC x = 0..1).
                // Equirect modes in panel mode are not sphere-rendered, so the same right-half
                // crop applies — without it, both SBS eyes appear side by side on screen.
                Timber.d("StereoVideoProcessor: buildGlEffect → SBS right-eye crop for $mode")
                Crop(0f, 1f, -1f, 1f)
            }
            StereoMode.OU, StereoMode.EQUIRECT_360_OU -> {
                // Bottom eye occupies the bottom half of the OU frame (GL NDC y = -1..0).
                // Same logic as flat OU applies for equirect OU in panel mode.
                Timber.d("StereoVideoProcessor: buildGlEffect → OU bottom-eye crop for $mode")
                Crop(-1f, 1f, -1f, 0f)
            }
            // No visual transformation for mono / monoscopic spherical / unresolved modes
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
