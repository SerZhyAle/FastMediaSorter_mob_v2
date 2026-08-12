package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.OrientationEventListener
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Buckets the physical device angle for the portrait-locked camera host (S0754) and exposes two
 * view-agnostic callbacks: one keeps overlay controls upright, the other updates CameraX targetRotation.
 *
 * S0924: also publishes the current [Surface] rotation bucket as [rotationBucket] so a late/dynamic
 * consumer (the on-demand settings dialog) can subscribe without a second [OrientationEventListener].
 */
class CameraOrientationManager(
    context: Context,
    private val onIconRotationChanged: (Float) -> Unit,
    private val onTargetRotationChanged: (Int) -> Unit,
) {

    private val appContext = context.applicationContext

    // S1457: seeded from the display rather than a ROTATION_0 literal. The listener corrects this on
    // its first reading, but on a device that has no orientation sensor it never fires at all, and
    // the literal made every capture there claim the device stood in its natural orientation.
    private var currentRotation = displayRotation()

    // S0924: single source of truth for the device rotation bucket, seeded with the initial value.
    private val rotationBucketState = MutableStateFlow(currentRotation)

    /** Current `Surface.ROTATION_*` bucket as observable state for late subscribers (S0924). */
    val rotationBucket: StateFlow<Int> = rotationBucketState.asStateFlow()

    private val listener = object : OrientationEventListener(appContext) {
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
        if (listener.canDetectOrientation()) {
            listener.enable()
        } else {
            // S1457: a device with no orientation sensor - a car head unit, a TV box, part of the
            // emulator fleet - never gets onOrientationChanged, so the display is the only rotation
            // signal left. Re-read on every enable: the screen can have turned since construction.
            currentRotation = displayRotation()
        }
        dispatch()
    }

    /** Current `Surface.ROTATION_*` of the default display; the only signal when no sensor reports. */
    private fun displayRotation(): Int = runCatching {
        val displays = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displays.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
    }.onFailure { Timber.w(it, "CameraOrientationManager: display rotation unavailable") }
        .getOrDefault(Surface.ROTATION_0)

    fun disable() {
        listener.disable()
    }

    private fun dispatch() {
        onTargetRotationChanged(currentRotation)
        rotationBucketState.value = currentRotation
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
