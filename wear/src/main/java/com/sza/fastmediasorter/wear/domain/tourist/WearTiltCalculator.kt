package com.sza.fastmediasorter.wear.domain.tourist

import kotlin.math.atan2
import kotlin.math.sqrt

/** S4146: pitch and roll in degrees; 0/0 is a watch lying flat face up. */
data class WearTilt(
    val pitchDegrees: Float,
    val rollDegrees: Float,
)

/** S4146: the level gauge's arithmetic over a gravity (or accelerometer) vector in device axes. */
object WearTiltCalculator {

    fun tilt(x: Float, y: Float, z: Float): WearTilt {
        val pitch = Math.toDegrees(atan2(y.toDouble(), sqrt((x * x + z * z).toDouble())))
        val roll = Math.toDegrees(atan2(-x.toDouble(), sqrt((y * y + z * z).toDouble())))
        return WearTilt(pitchDegrees = pitch.toFloat(), rollDegrees = roll.toFloat())
    }
}
