package com.sza.fastmediasorter.domain.model.sensors

/**
 * S4560: the phone's attitude as a unit quaternion in the platform's world frame (x east, y north, z up),
 * plus how fast it is turning.
 *
 * [turnRateDegreesPerSecond] is null on a phone without a gyroscope: the rotation sensor still draws the
 * attitude, but a rate derived from it would lag and jitter, so no number is shown rather than a wrong one.
 */
data class AttitudeReading(
    val x: Float,
    val y: Float,
    val z: Float,
    val w: Float,
    val turnRateDegreesPerSecond: Float?,
)
