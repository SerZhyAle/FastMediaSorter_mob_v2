package com.sza.fastmediasorter.domain.model.tourist

/**
 * S4150: the latest heart rate and body temperature the watch Tourist reported, stamped with the
 * phone's own arrival time so a skewed watch clock cannot make an old value look fresh.
 */
data class WatchHealthReading(
    val heartRateBpm: Int?,
    val bodyTemperatureCelsius: Float?,
    val receivedAtMillis: Long,
)

/** S4150: how long after arrival a watch reading still counts as current on the phone. */
const val WATCH_HEALTH_FRESHNESS_MS = 60_000L

/**
 * S4150: what the dashboard shows at [nowMillis]. A reading past the freshness window - or stamped in
 * the future after a clock change - reads as absent, so a stale pulse is never shown as current.
 */
fun WatchHealthReading?.toStats(offered: Boolean, nowMillis: Long): WatchHealthStats {
    val fresh = this?.takeIf { offered && nowMillis - it.receivedAtMillis in 0..WATCH_HEALTH_FRESHNESS_MS }
    return WatchHealthStats(
        offered = offered,
        heartRateBpm = fresh?.heartRateBpm,
        bodyTemperatureCelsius = fresh?.bodyTemperatureCelsius,
    )
}
