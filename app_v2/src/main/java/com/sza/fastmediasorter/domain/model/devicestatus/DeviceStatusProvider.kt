package com.sza.fastmediasorter.domain.model.devicestatus

/**
 * S1178: the single contract every device metric implements.
 *
 * The refresh period belongs to the metric, not to the view drawing it. A view cannot know that uptime
 * moves every second while total RAM never moves at all, and four gadgets each polling at the view's
 * convenience is the largest performance risk this set carries.
 *
 * Periods are seconds-scale by contract. [MIN_REFRESH_INTERVAL_MS] is the floor, so no implementation
 * can quietly pick a frame-rate period.
 */
interface DeviceStatusProvider<out T> {

    val refreshIntervalMs: Long

    suspend fun read(): T

    companion object {
        /** One second - the shortest period any device metric may ask for. */
        const val MIN_REFRESH_INTERVAL_MS = 1_000L
    }
}
