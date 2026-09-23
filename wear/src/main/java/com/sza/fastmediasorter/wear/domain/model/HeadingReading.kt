package com.sza.fastmediasorter.wear.domain.model

/**
 * S3370: the platform's own confidence in the watch heading.
 *
 * Mirrors the platform `SENSOR_STATUS_*` constants without importing them, so the domain layer
 * stays free of framework sensor imports - the same shape as the phone's `SensorAccuracy`.
 */
enum class HeadingAccuracy {
    UNRELIABLE,
    LOW,
    MEDIUM,
    HIGH,
    ;

    companion object {

        /** An unknown or no-contact status maps to [UNRELIABLE] - never to a confident value. */
        fun fromPlatformStatus(status: Int): HeadingAccuracy = when (status) {
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
 * S3370: device heading for the dim overlay's spark pair, in display units.
 *
 * [isTrustworthy] is the fallback trigger the overlay keys on: an untrusted reading draws the pair
 * white on a random azimuth instead of red-to-north/blue-to-south (strategic ADR-2).
 */
data class HeadingReading(
    val azimuthDegrees: Float,
    val accuracy: HeadingAccuracy,
    val takenAtMillis: Long,
) {
    val isTrustworthy: Boolean
        get() = accuracy != HeadingAccuracy.UNRELIABLE
}
