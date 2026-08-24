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
            val nextRotation = CameraRotationBucket.bucketFor(orientation)
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

    /**
     * S1986: pins the rotation bucket to [rotation], detaching it from the sensor until the next
     * [enable].
     *
     * Exists because the host cannot turn a real phone: sensor injection is closed on retail firmware
     * (`dumpsys sensorservice data_injection` answers INVALID_OPERATION) and `user_rotation` never
     * reaches a portrait-locked activity. Without this, the whole capture pipeline below the bucket -
     * targetRotation, EXIF, both crops - could only ever be measured in whichever pose the phone
     * happened to be lying in. Reached only through the debug-only broadcast hook; release builds
     * carry no caller, because the class that sends here lives in `src/debug`.
     */
    fun forceRotation(rotation: Int) {
        listener.disable()
        currentRotation = rotation
        dispatch()
    }

    private fun dispatch() {
        onTargetRotationChanged(currentRotation)
        rotationBucketState.value = currentRotation
        onIconRotationChanged(CameraRotationBucket.iconDegreesFor(currentRotation))
    }
}
