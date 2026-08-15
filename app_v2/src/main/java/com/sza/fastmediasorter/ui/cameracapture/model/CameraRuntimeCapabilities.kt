package com.sza.fastmediasorter.ui.cameracapture.model

import android.util.Range
import android.util.Size
import androidx.camera.core.CameraSelector
import kotlin.math.roundToInt

/**
 * Immutable snapshot of what the currently-bound camera lens can actually do, read once per bind or
 * lens switch. The UI renders from this instead of querying CameraX directly, so unsupported
 * controls are hidden rather than shown as dead buttons (S0545 §3.4, ADR-3).
 *
 * View-agnostic on purpose so the flow manager and future tests can consume it without touching
 * CameraX objects.
 */
data class CameraRuntimeCapabilities(
    val activeLensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val availableLensFacings: List<Int> = emptyList(),
    val hasFlashUnit: Boolean = false,
    val supportsTapToFocus: Boolean = false,
    val minZoomRatio: Float = DEFAULT_ZOOM,
    val maxZoomRatio: Float = DEFAULT_ZOOM,
    val currentZoomRatio: Float = DEFAULT_ZOOM,
    val currentLinearZoom: Float = 0f,
    val zoomPresets: List<Float> = emptyList(),
    val supportsNightMode: Boolean = false,
    val supportsExposureCompensation: Boolean = false,
    val maxExposureCompensationIndex: Int = 0,
    val supportsManualSensor: Boolean = false,
    val isoRange: Range<Int>? = null,
    val shutterRangeNs: Range<Long>? = null,
    val awbModes: List<Int> = emptyList(),
    val supportsHdrExtension: Boolean = false,
    /**
     * S1262: the BOKEH extension is available on the active lens, which is what makes a portrait
     * profile offerable. Defaults false so every existing call site keeps compiling and every device
     * that has not been probed simply does not offer the profile.
     */
    val supportsBokehExtension: Boolean = false,
    val availableAspectRatios: List<Int> = emptyList(),
    val photoResolutions: List<Size> = emptyList(),
    /**
     * S1189: the subset of [photoResolutions] that only the sensor's high-resolution mode delivers.
     * Choosing one of these is slower and heavier, so it stays an explicit user choice (ADR-4).
     */
    val highResolutionPhotoSizes: List<Size> = emptyList(),
    /** Native-ratio -> equivalent-zoom factor (this lens focal / main lens focal); 1.0 for the main lens. */
    val zoomMultiplier: Float = 1f,
    /** The active lens can focus close enough for a usable macro mode (heuristic on min focus distance). */
    val supportsMacro: Boolean = false,
    /**
     * S1189: some other lens of the same facing focuses close enough to serve as a macro lens. Most
     * devices implement macro as dedicated optics rather than a focus lock on the main lens, so
     * macro is offered when either this or [supportsMacro] holds.
     */
    val macroLensAvailable: Boolean = false,
    /** Closest focus distance in diopters (1/m) for macro; 0 when fixed-focus / unknown. */
    val macroFocusDistance: Float = 0f,
    /** Max native ratio reachable including digital crop beyond the lens optical/CameraX max (S0753). */
    val maxDisplayZoomRatio: Float = DEFAULT_ZOOM,
    /**
     * S1189: widest equivalent zoom reachable on this device across every offered lens, not just the
     * bound one. Below 1 on a phone whose ultra-wide is a physical sub-lens - the value the system
     * camera shows as 0.6x while the bound main lens still reports its own floor of 1.
     */
    val minEquivalentZoomRatio: Float = DEFAULT_ZOOM,
    /**
     * S1189: the active lens is the widest of several back lenses. False on a device with a single
     * back camera, so that camera keeps reading as the plain wide lens rather than "ultra-wide".
     */
    val activeLensIsWidest: Boolean = false,
    /** S1189: the active lens is the dedicated close-focus lens, so the label reads "macro". */
    val activeLensIsMacro: Boolean = false,
    /**
     * S1675: display-rounded equivalent floor of each rear lens, ascending. Empty on a device with a
     * single rear lens, which is what keeps a one-button row from appearing where there is nothing to
     * switch between.
     */
    val rearLensEquivalentFloors: List<Float> = emptyList(),
) {
    /** A second lens to flip to exists. */
    val canSwitchLens: Boolean get() = availableLensFacings.size > 1

    /** The active lens faces the user. */
    val isFront: Boolean get() = activeLensFacing == CameraSelector.LENS_FACING_FRONT

    /** The active lens has a usable zoom range (not a fixed 1x). */
    val supportsZoom: Boolean get() = maxZoomRatio > minZoomRatio + ZOOM_EPSILON

    /** The bound lens's own reachable floor, in equivalent-zoom space. */
    val ownEquivalentFloor: Float get() = minZoomRatio * zoomMultiplier

    /**
     * S1261: the device-wide floor ([minEquivalentZoomRatio], e.g. 0.57 on the S25 FE) sits below
     * what the bound lens itself reaches - the zoom row then offers a cross-lens floor pill whose
     * tap switches optics. False when the bound lens covers the floor natively (single-camera
     * devices, POCO after its own floor - strategic goal 4) so the row stays exactly as today.
     */
    val showsCrossLensFloor: Boolean
        get() = !isFront && minEquivalentZoomRatio < ownEquivalentFloor - ZOOM_EPSILON

    /** S1261: the floor pill's printed value - display-rounded equivalent (S1260 rule, 0.57 -> 0.5). */
    val crossLensFloorDisplay: Float get() = roundEquivalentForDisplay(minEquivalentZoomRatio)

    /** The bound lens's own floor, printed by the same rule as the pills, so the two can be matched. */
    val ownEquivalentFloorDisplay: Float get() = roundEquivalentForDisplay(ownEquivalentFloor)

    companion object {
        const val DEFAULT_ZOOM = 1f
        const val ZOOM_EPSILON = 0.01f

        /** Overall display-zoom ceiling (optical + CameraX + digital crop), in equivalent terms. */
        const val MAX_DISPLAY_ZOOM = 30f

        /** Digital-crop multiplier allowed beyond the lens optical/CameraX max (soft zoom). */
        const val DIGITAL_ZOOM_CAP = 4f

        /** Drop a preset sitting above this fraction of the max - redundant with the max button. */
        const val NEAR_MAX_DROP_FACTOR = 0.85f

        /** Zoom presets are displayed and compared to one decimal place. */
        const val ZOOM_STEP_SCALE = 10f

        /** S1260: below this equivalent zoom labels round to half steps, from it upward to wholes. */
        const val DISPLAY_HALF_STEP_LIMIT = 5f

        /** S1260: half-step quantization scale for displayed zoom values below the limit. */
        const val DISPLAY_HALF_STEP_SCALE = 2f

        /** No-capability fallback used before the first successful bind. */
        val NONE = CameraRuntimeCapabilities()

        /**
         * Builds zoom presets clamped to the lens range: the wide preset (when the lens goes below
         * 1x), the 1x/3x/5x/10x/20x/30x steps that fall inside the range, plus the lens maximum so a
         * reachable ceiling preset always exists even when it sits between table values, then
         * de-duplicated and ordered (S0545 §3.4, S0753).
         */
        fun buildZoomPresets(
            minZoom: Float,
            maxZoom: Float,
            multiplier: Float = 1f,
            digitalCap: Float = 1f,
        ): List<Float> {
            if (maxZoom <= minZoom + ZOOM_EPSILON) return emptyList()
            val mult = if (multiplier > 0f) multiplier else 1f
            // The reachable max includes digital crop beyond the optical/CameraX max, capped overall.
            val maxNative = (maxZoom * digitalCap.coerceAtLeast(1f)).coerceAtMost(MAX_DISPLAY_ZOOM / mult)
            // Desired presets in equivalent-zoom terms: below 1x only the lens minimum (minZoom), at/above
            // 1x the 1/3/5/10/20/30 steps, ending at the reachable maximum (S0754 owner feedback).
            val desiredEquiv = listOf(1f, 3f, 5f, 10f, 20f, 30f)
            // S1189: the device's own optical limits always get their own button - they are what marks
            // where real optics end and software zoom begins.
            val nativeBounds = listOf(minZoom, maxZoom).map(::roundToStep)
            val candidates = desiredEquiv.map { it / mult } + minZoom + maxZoom + maxNative
            val presets = candidates
                .filter { it in minZoom..maxNative }
                .map(::roundToStep)
                .distinct()
                .sorted()
            // Adaptive count: drop a step just below the max (redundant with the max button) - e.g. when
            // the max is 3.3 the 3 step is dropped, like the new Samsung camera (S0753 device feedback).
            val ceiling = presets.lastOrNull() ?: return presets
            val kept = presets.filter {
                it in nativeBounds || it == ceiling || it < ceiling * NEAR_MAX_DROP_FACTOR
            }
            // S1260: pills print the display-rounded equivalent, so two presets whose labels collapse
            // to the same text would render as identical buttons - keep one, preferring the native
            // bound (its amber marking carries meaning the plain step does not).
            val byLabel = LinkedHashMap<Float, Float>()
            kept.forEach { preset ->
                val label = roundEquivalentForDisplay(preset * mult)
                val current = byLabel[label]
                if (current == null || (preset in nativeBounds && current !in nativeBounds)) {
                    byLabel[label] = preset
                }
            }
            return byLabel.values.sorted()
        }

        /**
         * S1675: the printed floors of the rear lenses, for the row shown on a lens whose own zoom
         * range is a single point and whose preset row would otherwise vanish. Input is one raw
         * equivalent floor per rear lens (its `minZoomRatio * equivalentMultiplier`); two lenses whose
         * labels collapse to the same value are one button to the user, so they collapse here too.
         *
         * Emptiness is deliberately NOT decided here - the caller drops a single-lens device, because
         * "one rear lens" is a fact about the device rather than about the rounding rule.
         */
        fun buildRearLensFloors(nativeFloors: List<Float>): List<Float> =
            nativeFloors
                .filter { it > 0f }
                .map(::roundEquivalentForDisplay)
                .distinct()
                .sorted()

        /**
         * S1189: presets are shown to one decimal, so anything comparing a preset against a lens
         * limit has to compare at that same precision or the two never match.
         */
        fun roundToStep(value: Float): Float = (value * ZOOM_STEP_SCALE).toInt() / ZOOM_STEP_SCALE

        /**
         * S1260: user-facing zoom values are coarse so the preset row stays readable - below
         * [DISPLAY_HALF_STEP_LIMIT] the nearest half step (1.6 -> 1.5), from it upward the nearest
         * whole number (8.1 -> 8). Display only: the applied native ratio keeps full precision.
         */
        fun roundEquivalentForDisplay(value: Float): Float =
            if (value < DISPLAY_HALF_STEP_LIMIT) {
                (value * DISPLAY_HALF_STEP_SCALE).roundToInt() / DISPLAY_HALF_STEP_SCALE
            } else {
                value.roundToInt().toFloat()
            }
    }
}
