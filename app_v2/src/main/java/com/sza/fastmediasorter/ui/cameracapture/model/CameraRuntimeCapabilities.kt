package com.sza.fastmediasorter.ui.cameracapture.model

import androidx.camera.core.CameraSelector

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
    val zoomPresets: List<Float> = emptyList(),
) {
    /** A second lens to flip to exists. */
    val canSwitchLens: Boolean get() = availableLensFacings.size > 1

    /** The active lens has a usable zoom range (not a fixed 1x). */
    val supportsZoom: Boolean get() = maxZoomRatio > minZoomRatio + ZOOM_EPSILON

    companion object {
        const val DEFAULT_ZOOM = 1f
        const val ZOOM_EPSILON = 0.01f

        /** No-capability fallback used before the first successful bind. */
        val NONE = CameraRuntimeCapabilities()

        /**
         * Builds Samsung-familiar zoom presets clamped to the lens range: the wide preset (when the
         * lens goes below 1x), 1x, 2x and the maximum, de-duplicated and ordered (S0545 §3.4).
         */
        fun buildZoomPresets(minZoom: Float, maxZoom: Float): List<Float> {
            if (maxZoom <= minZoom + ZOOM_EPSILON) return emptyList()
            val candidates = listOf(minZoom, 1f, 2f, maxZoom)
            return candidates
                .filter { it in minZoom..maxZoom }
                .map { (it * 10f).toInt() / 10f }
                .distinct()
                .sorted()
        }
    }
}
