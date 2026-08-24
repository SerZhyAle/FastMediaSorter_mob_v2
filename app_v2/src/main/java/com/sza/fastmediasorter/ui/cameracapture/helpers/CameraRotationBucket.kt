package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.view.Surface

/**
 * S1986: the one place that turns a physical device angle into a `Surface.ROTATION_*` bucket.
 *
 * Extracted from [CameraOrientationManager] so it can be proven on the JVM. It has to be, because the
 * host-side sweep that measures the capture pipeline drives the bucket directly through a debug hook -
 * the accelerometer cannot be faked on a retail phone - and therefore never exercises this mapping.
 * A wrong quadrant edge or a flipped sign here would rotate every photo taken in that pose and no
 * device sweep would notice.
 *
 * The values are the canonical CameraX pairing: the listener reports the device's CLOCKWISE angle from
 * its natural orientation, and the target rotation is the INVERSE turn the buffer needs, so 90 degrees
 * of device maps to `ROTATION_270` and not to `ROTATION_90`.
 */
internal object CameraRotationBucket {

    // Quadrant edges (deg) around each of the four resting orientations.
    private const val LANDSCAPE_CW_MIN = 45
    private const val LANDSCAPE_CW_MAX = 134
    private const val INVERTED_MIN = 135
    private const val INVERTED_MAX = 224
    private const val LANDSCAPE_CCW_MIN = 225
    private const val LANDSCAPE_CCW_MAX = 314

    /** Counter-rotation (deg, clockwise-positive `View.rotation`) that keeps an overlay upright. */
    private const val ICON_COMPENSATE_ROTATION_0 = 0f
    private const val ICON_COMPENSATE_ROTATION_90 = 90f
    private const val ICON_COMPENSATE_ROTATION_180 = 180f
    private const val ICON_COMPENSATE_ROTATION_270 = -90f

    /** The `Surface.ROTATION_*` bucket for a clockwise device angle in 0..359. */
    fun bucketFor(deviceAngleDegrees: Int): Int = when (deviceAngleDegrees) {
        in LANDSCAPE_CW_MIN..LANDSCAPE_CW_MAX -> Surface.ROTATION_270
        in INVERTED_MIN..INVERTED_MAX -> Surface.ROTATION_180
        in LANDSCAPE_CCW_MIN..LANDSCAPE_CCW_MAX -> Surface.ROTATION_90
        else -> Surface.ROTATION_0
    }

    /**
     * Degrees an overlay drawn by a portrait-locked host must be turned to stay upright in [bucket].
     *
     * The negative of the device's own clockwise angle. The two landscape branches are the ones worth
     * a test: getting a sign wrong there nets 180 degrees, so the icons are upside down in landscape
     * while inverted portrait still looks right - the combination that hides the mistake.
     */
    fun iconDegreesFor(bucket: Int): Float = when (bucket) {
        Surface.ROTATION_90 -> ICON_COMPENSATE_ROTATION_90
        Surface.ROTATION_180 -> ICON_COMPENSATE_ROTATION_180
        Surface.ROTATION_270 -> ICON_COMPENSATE_ROTATION_270
        else -> ICON_COMPENSATE_ROTATION_0
    }
}
