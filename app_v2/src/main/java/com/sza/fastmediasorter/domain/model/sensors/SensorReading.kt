package com.sza.fastmediasorter.domain.model.sensors

/**
 * S1179: the platform's own confidence in the compass.
 *
 * Mirrors the `SensorManager.SENSOR_STATUS_*` constants without importing them, so the domain layer
 * stays free of `android.hardware` and the compass tile can warn about calibration (strategic §7).
 */
enum class SensorAccuracy {
    UNRELIABLE,
    LOW,
    MEDIUM,
    HIGH,
    ;

    companion object {

        /** An unknown or no-contact status maps to [UNRELIABLE] - never to a confident value. */
        fun fromPlatformStatus(status: Int): SensorAccuracy = when (status) {
            PLATFORM_LOW -> LOW
            PLATFORM_MEDIUM -> MEDIUM
            PLATFORM_HIGH -> HIGH
            else -> UNRELIABLE
        }

        private const val PLATFORM_LOW = 1
        private const val PLATFORM_MEDIUM = 2
        private const val PLATFORM_HIGH = 3
    }
}

/**
 * S1179: heading and altitude for the compass tile, in display units.
 *
 * [altitudeMeters] is null when no position fix has supplied one, never 0.0: a tile that cannot
 * tell "no fix yet" from "sea level" draws sea level on a mountain.
 */
data class CompassReading(
    val azimuthDegrees: Float,
    val accuracy: SensorAccuracy,
    val altitudeMeters: Double?,
    val takenAtMillis: Long,
)

/**
 * S1179: one position sample, feeding the speed tile, the altitude tile and both charts.
 *
 * [distanceDeltaMeters] is the distance since the previous sample, not a running total - the total
 * belongs to the series that accumulates it, because only that layer knows when it was last reset
 * (strategic ADR-2).
 */
data class MotionReading(
    val speedKmh: Float?,
    val altitudeMeters: Double?,
    val distanceDeltaMeters: Double,
    val takenAtMillis: Long,
) {
    companion object {

        /**
         * Converts the platform's speed to the unit the owner asked for (strategic §3.1.2). Done
         * once here rather than per tile so two gadgets reading the same fix cannot disagree about
         * rounding (strategic ADR-1).
         */
        fun fromPlatformSpeed(
            rawSpeed: Float?,
            altitudeMeters: Double?,
            distanceDeltaMeters: Double,
            takenAtMillis: Long,
        ): MotionReading = MotionReading(
            speedKmh = rawSpeed?.times(KMH_PER_PLATFORM_UNIT),
            altitudeMeters = altitudeMeters,
            distanceDeltaMeters = distanceDeltaMeters,
            takenAtMillis = takenAtMillis,
        )

        private const val KMH_PER_PLATFORM_UNIT = 3.6f
    }
}

/**
 * S1179: the system step counter's own value.
 *
 * [stepsSinceBoot] is reported verbatim: strategic §2.3 and §5.1 forbid this feature from computing
 * a step total of its own, so nothing here subtracts a baseline or rolls over at midnight.
 */
data class StepReading(
    val stepsSinceBoot: Long,
    val takenAtMillis: Long,
)
