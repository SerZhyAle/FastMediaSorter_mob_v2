package com.sza.fastmediasorter.domain.model.networkmonitor

/**
 * S1433: how fast the whole device is moving bytes right now, in bytes per second.
 *
 * Device-wide and not per-app, which is what the platform counters answer and therefore what the section may
 * claim. [totalBytesPerSecond] is what the chart draws: one line the reader can follow, with the two
 * directions shown as their own rows beside it, because a second line would need a second scale and a second
 * summary under a chart strategic §3.2 gives one summary line.
 */
data class TrafficRate(
    val takenAtMillis: Long,
    val receivedBytesPerSecond: Double,
    val transmittedBytesPerSecond: Double,
) {

    val totalBytesPerSecond: Double get() = receivedBytesPerSecond + transmittedBytesPerSecond
}
