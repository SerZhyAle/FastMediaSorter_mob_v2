package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.content.Context
import android.view.OrientationEventListener
import android.view.Surface

/**
 * Buckets the physical device angle for the portrait-locked camera host (S0754) and exposes two
 * view-agnostic callbacks: one keeps overlay controls upright, the other updates CameraX targetRotation.
 */
class CameraOrientationManager(
    context: Context,
    private val onIconRotationChanged: (Float) -> Unit,
    private val onTargetRotationChanged: (Int) -> Unit,
) {

    private var currentRotation = Surface.ROTATION_0

    private val listener = object : OrientationEventListener(context.applicationContext) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return
            val nextRotation = when (orientation) {
                in LANDSCAPE_CW_MIN..LANDSCAPE_CW_MAX -> Surface.ROTATION_270
                in INVERTED_MIN..INVERTED_MAX -> Surface.ROTATION_180
                in LANDSCAPE_CCW_MIN..LANDSCAPE_CCW_MAX -> Surface.ROTATION_90
                else -> Surface.ROTATION_0
            }
            if (nextRotation == currentRotation) return
            currentRotation = nextRotation
            dispatch()
        }
    }

    fun enable() {
        if (listener.canDetectOrientation()) listener.enable()
        dispatch()
    }

    fun disable() {
        listener.disable()
    }

    private fun dispatch() {
        onTargetRotationChanged(currentRotation)
        // Overlay counter-rotation must be the negative of the device's clockwise angle reported by
        // OrientationEventListener; the landscape branches previously carried the wrong sign, so
        // both landscape sides netted 180 deg (upside-down) while inverted portrait stayed upright.
        val iconDegrees = when (currentRotation) {
            Surface.ROTATION_90 -> ICON_COMPENSATE_ROTATION_90
            Surface.ROTATION_180 -> ICON_COMPENSATE_ROTATION_180
            Surface.ROTATION_270 -> ICON_COMPENSATE_ROTATION_270
            else -> ICON_COMPENSATE_ROTATION_0
        }
        onIconRotationChanged(iconDegrees)
    }

    private companion object {
        // Quadrant edges (deg) that bucket the OrientationEventListener clockwise angle into a Surface
        // rotation - the canonical CameraX targetRotation mapping (device CW angle -> inverse Surface).
        const val LANDSCAPE_CW_MIN = 45
        const val LANDSCAPE_CW_MAX = 134
        const val INVERTED_MIN = 135
        const val INVERTED_MAX = 224
        const val LANDSCAPE_CCW_MIN = 225
        const val LANDSCAPE_CCW_MAX = 314

        // Counter-rotation (deg, clockwise-positive View.rotation) that keeps portrait-locked overlays
        // upright for each device rotation - the negative of the device's clockwise physical angle.
        const val ICON_COMPENSATE_ROTATION_0 = 0f
        const val ICON_COMPENSATE_ROTATION_90 = 90f
        const val ICON_COMPENSATE_ROTATION_180 = 180f
        const val ICON_COMPENSATE_ROTATION_270 = -90f
    }
}
