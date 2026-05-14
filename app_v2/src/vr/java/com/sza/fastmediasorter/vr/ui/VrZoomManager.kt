package com.sza.fastmediasorter.vr.ui

import com.sza.fastmediasorter.ui.player.render.stereoscopic.VrLayerDescriptor
import timber.log.Timber

/**
 * Owns the VR zoom state and converts grip-analog and discrete keyboard inputs
 * into layer-descriptor resize commands.
 *
 * The factor is applied by scaling both width and height of the current
 * [VrLayerDescriptor]; the activity holds the authoritative descriptor and
 * passes a snapshot back through [onZoomChanged] so `OpenXrSessionManager`
 * can submit the new dimensions to the compositor.
 *
 * Clamp bounds (0.3x–3.0x) protect the user from zooming the quad into
 * unusable extremes — matches the UX spec's "sensible range" requirement.
 */
class VrZoomManager(
    private val onZoomChanged: (VrLayerDescriptor) -> Unit,
) {

    @Volatile
    private var currentZoom = 1.0f

    @Volatile
    private var baseDescriptor: VrLayerDescriptor = VrLayerDescriptor()

    /**
     * Remember the current "1.0×" descriptor so analog grip deltas and discrete
     * steps scale from the same base. Call this when the layer descriptor is
     * rebuilt for a new file or stereo mode — otherwise zoom "sticks" to old dims.
     */
    fun setBaseDescriptor(descriptor: VrLayerDescriptor) {
        baseDescriptor = descriptor
        notifyChanged()
    }

    /** Current zoom factor (1.0 = 100%). */
    fun zoom(): Float = currentZoom

    /**
     * Analog grip delta (from [XrInputEventType.ZOOM_DELTA]).
     * Positive delta → grip tightening → zoom-in.
     */
    fun onGripDelta(delta: Float) {
        val next = currentZoom + delta * GRIP_SENSITIVITY
        setZoom(next, reason = "grip")
    }

    /**
     * Discrete step via keyboard `+` / `-`. Snaps to the next [DISCRETE_LEVELS]
     * entry above/below the current zoom; keeps UX predictable across input sources.
     */
    fun onDiscreteStep(increase: Boolean) {
        val levels = DISCRETE_LEVELS
        val target = if (increase) {
            levels.firstOrNull { it > currentZoom + STEP_EPSILON } ?: levels.last()
        } else {
            levels.lastOrNull { it < currentZoom - STEP_EPSILON } ?: levels.first()
        }
        setZoom(target, reason = "discrete-${if (increase) "up" else "down"}")
    }

    /** Reset to 100%. Called by double-click of right thumbstick or `0` key. */
    fun reset() {
        setZoom(1.0f, reason = "reset")
    }

    private fun setZoom(next: Float, reason: String) {
        val clamped = next.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (kotlin.math.abs(clamped - currentZoom) < STEP_EPSILON) return
        currentZoom = clamped
        Timber.d("VrZoom: %.3fx (reason=%s)", clamped, reason)
        notifyChanged()
    }

    private fun notifyChanged() {
        val base = baseDescriptor
        val scaled = base.copy(
            widthMeters = base.widthMeters * currentZoom,
            heightMeters = base.heightMeters * currentZoom,
            radiusMeters = base.radiusMeters * currentZoom,
        )
        onZoomChanged(scaled)
    }

    companion object {
        const val MIN_ZOOM = 0.3f
        const val MAX_ZOOM = 3.0f

        /** Discrete zoom levels for keyboard `+/-`. */
        val DISCRETE_LEVELS = listOf(0.5f, 0.8f, 1.0f, 1.2f, 1.5f, 2.0f, 2.5f, 3.0f)

        /** Analog grip delta → zoom multiplier scale. Tuned so a full 0→1 grip sweep ≈ +0.4×. */
        private const val GRIP_SENSITIVITY = 0.4f

        private const val STEP_EPSILON = 0.001f
    }
}
