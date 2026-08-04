package com.sza.fastmediasorter.ui.cameracapture.helpers

/**
 * Computes a lens's equivalent-zoom multiplier (native ratio -> system-camera-style equivalent)
 * from static lens facts. Pure math, no Android imports, so the rule is unit-testable with the
 * numbers a device report supplies (S1261 research 01).
 *
 * Why raw focal ratios are not enough (defect D2): focal lengths on different sensor sizes are not
 * comparable - the S25 FE ultra-wide (1.74 mm) against the main (5.40 mm) yields 0.32 while the
 * true equivalent floor is 0.57, and the tele (7.00 mm) yields 1.30 while the system camera calls
 * it ~3x. Field-of-view normalization by sensor width fixes both; the parent logical camera's
 * sub-1x floor is authoritative for the widest lens inside it (the platform computed it already).
 */
object LensEquivalentCalculator {

    /** The lens the equivalents are expressed against - the device's main back camera. */
    data class Reference(
        val focalMm: Float,
        val sensorWidthMm: Float = UNKNOWN_MM,
    )

    /** Static facts of the lens whose multiplier is being computed. */
    data class Lens(
        val isBack: Boolean,
        val focalMm: Float,
        val sensorWidthMm: Float = UNKNOWN_MM,
        /** Floor of the logical camera this lens belongs to; own floor for a logical entry. */
        val parentLogicalMinZoom: Float = 0f,
        /** The lens is the widest back sub-lens inside its logical parent. */
        val isWidestInParent: Boolean = false,
    )

    /**
     * Priority order: parent-floor cross-check for the widest sub-lens of a sub-1x logical camera
     * (exact - the platform already solved the optics), then FOV normalization when both sensor
     * widths are known, then the raw focal ratio as the last resort. Front lenses and lenses with
     * no usable focal data stay at 1 (their native scale, matching today's behavior).
     */
    fun multiplier(lens: Lens, reference: Reference): Float {
        val parentFloor = lens.parentLogicalMinZoom
        val fovRatio = if (lens.sensorWidthMm > 0f && reference.sensorWidthMm > 0f) {
            (lens.focalMm / lens.sensorWidthMm) / (reference.focalMm / reference.sensorWidthMm)
        } else {
            0f
        }
        return when {
            !lens.isBack || lens.focalMm <= 0f || reference.focalMm <= 0f -> NEUTRAL
            lens.isWidestInParent && parentFloor > 0f && parentFloor < NEUTRAL -> parentFloor
            fovRatio > 0f -> fovRatio
            else -> lens.focalMm / reference.focalMm
        }
    }

    /** Sensor width is unknown - the FOV path is unavailable for this lens. */
    const val UNKNOWN_MM = 0f

    private const val NEUTRAL = 1f
}
