package com.sza.fastmediasorter.wear.ui.common.dimresponse

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * S3370: pure geometry for the dim overlay's spark pair - no Compose types, so the unit suite
 * proves the pair's shape without a device. The overlays feed it either a live heading or a
 * random azimuth; both take the same path (strategic §2.4).
 */
object TapResponseGeometry {

    /**
     * Screen unit vector for [azimuthDegrees]: azimuth 0 is screen up, growing clockwise
     * (x right, y down).
     */
    fun directionUnitVector(azimuthDegrees: Float): Pair<Float, Float> {
        val radians = Math.toRadians(azimuthDegrees.toDouble())
        return sin(radians).toFloat() to -cos(radians).toFloat()
    }

    /** The 180 deg counterpart of a unit vector from [directionUnitVector]. */
    fun oppositeDirection(unit: Pair<Float, Float>): Pair<Float, Float> = -unit.first to -unit.second

    /** Width along a spark stripe: 0 at both ends, peaking mid-stroke. */
    fun widthProfile(t: Float): Float = sin(PI * t).toFloat()
}
