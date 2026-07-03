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
                in 45..134 -> Surface.ROTATION_270
                in 135..224 -> Surface.ROTATION_180
                in 225..314 -> Surface.ROTATION_90
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
        onIconRotationChanged(
            when (currentRotation) {
                Surface.ROTATION_90 -> -90f
                Surface.ROTATION_180 -> 180f
                Surface.ROTATION_270 -> 90f
                else -> 0f
            },
        )
    }
}
