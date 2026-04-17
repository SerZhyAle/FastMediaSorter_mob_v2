package com.sza.fastmediasorter.ui.player

import androidx.media3.common.Effect
import com.sza.fastmediasorter.domain.model.StereoMode
import timber.log.Timber

/**
 * Applies stereoscopic rendering to ExoPlayer video output.
 *
 * Architecture note:
 * FastMediaSorter currently renders into a single PlayerView surface.
 * That surface can preserve an SBS/OU source frame for phone VR viewers,
 * but it CANNOT synthesize stereo by cropping one eye and scaling it fullscreen.
 * Doing so destroys the second eye and degrades the video to mono.
 *
 * This class therefore:
 *  1. Tracks the current [StereoMode] and exposes it to the UI.
 *  2. Preserves SBS/OU frames as-is for phone-based VR viewers (Cardboard-style optics do the split).
 *  3. Provides the hook point for a future dual-viewport renderer if the app ever adds one.
 *
 * Future implementation plan:
 *  • Replace the single-surface approach with explicit dual-eye rendering.
 *  • Only then introduce GL crops / viewports / FBOs.
 *
 * Thread safety: [setStereoMode] may be called from the main thread;
 * in Phase 2 the GL callbacks will arrive on the GL thread — use @Volatile or
 * AtomicReference for the mode field.
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

        // Keep the selected mode cached even before ExoPlayer exists so createPlayer()
        // can reapply the same state after a player recreation/configuration change.
        if (isStereoActive) {
            Timber.d("StereoVideoProcessor: stereo mode requested; preserving source frame for VR playback")
        }
    }

    /**
     * Returns the currently active mode (resolved — never AUTO or UNKNOWN).
     */
    fun getCurrentMode(): StereoMode = currentMode

    /**
     * Builds the [Effect] to apply for the given [mode].
     *
     * SBS_FULL / SBS_HALF / OU → return null.
     *
     * Why: cropping one eye out of a single-surface frame destroys stereoscopy.
     * For phone-based VR viewers the correct behaviour is to preserve the original SBS/OU
     * frame and let the headset optics route each half to the proper eye.
     * MONO / AUTO / UNKNOWN → null (no effect = full-frame pass-through).
     *
     * Called by [VideoPlayerManager.applyStereoEffect] whenever the stereo mode changes.
     */
    fun buildGlEffect(mode: StereoMode): Effect? {
        return when (mode) {
            StereoMode.SBS_FULL, StereoMode.SBS_HALF -> {
                Timber.d("StereoVideoProcessor: buildGlEffect → pass-through for $mode (preserve SBS frame)")
                null
            }
            StereoMode.OU -> {
                Timber.d("StereoVideoProcessor: buildGlEffect → pass-through for OU until dedicated OU renderer exists")
                null
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
