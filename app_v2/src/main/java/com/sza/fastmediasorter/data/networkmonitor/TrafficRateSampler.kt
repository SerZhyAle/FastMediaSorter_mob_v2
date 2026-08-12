package com.sza.fastmediasorter.data.networkmonitor

import android.net.TrafficStats
import android.os.SystemClock
import com.sza.fastmediasorter.domain.model.networkmonitor.MonitorSection
import com.sza.fastmediasorter.domain.model.networkmonitor.SectionAvailability
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** S1433: one reading of how fast the whole device is moving bytes, in bytes per second. */
data class TrafficRateReading(
    val takenAtMillis: Long,
    val rxBytesPerSecond: Double,
    val txBytesPerSecond: Double,
)

/**
 * S1433: turns the device's cumulative traffic counters into a rate, for one chart.
 *
 * The counters are totals since boot, so a rate needs two reads and the first tick produces none - the
 * chart starts one interval late rather than opening with a fabricated zero.
 *
 * The interval is measured with [SystemClock.elapsedRealtime] while the sample is stamped with the wall
 * clock: a rate divided by a wall-clock delta would spike or go negative the moment the clock is corrected,
 * and the chart's own axis needs a time the user's device agrees with.
 *
 * Cold, like every Monitor sampler: the tick starts on collection and is cancelled when it stops.
 */
@Singleton
class TrafficRateSampler @Inject constructor() {

    /** Emits one rate per tick for as long as it is collected. */
    fun observe(): Flow<MonitorSection<TrafficRateReading>> = callbackFlow {
        val ticker = launch {
            val first = counters()
            if (first == null) {
                trySend(MonitorSection.absent(SectionAvailability.NoHardware))
            } else {
                var previous: TrafficCounters = first
                while (isActive) {
                    delay(SAMPLE_INTERVAL_MS)
                    val current = counters() ?: break
                    trySend(MonitorSection.available(rate(previous, current)))
                    previous = current
                }
            }
        }

        awaitClose { ticker.cancel() }
    }

    /**
     * The counters, or null on a device that does not keep them.
     *
     * `TrafficStats` answers [TrafficStats.UNSUPPORTED] rather than throwing, and a section that cannot ever
     * be filled is [SectionAvailability.NoHardware] rather than an empty chart.
     */
    private fun counters(): TrafficCounters? {
        val unsupported = TrafficStats.UNSUPPORTED.toLong()
        val received = TrafficStats.getTotalRxBytes()
        val transmitted = TrafficStats.getTotalTxBytes()
        if (received == unsupported || transmitted == unsupported) {
            return null
        }
        return TrafficCounters(
            rxBytes = received,
            txBytes = transmitted,
            elapsedRealtimeMs = SystemClock.elapsedRealtime(),
        )
    }

    /**
     * Bytes per second between two reads.
     *
     * A counter that went backwards means it was reset under us, and the honest rate for an interval whose
     * start no longer exists is zero rather than a negative or a huge positive spike.
     */
    private fun rate(previous: TrafficCounters, current: TrafficCounters): TrafficRateReading {
        val elapsedMs = current.elapsedRealtimeMs - previous.elapsedRealtimeMs
        return TrafficRateReading(
            takenAtMillis = System.currentTimeMillis(),
            rxBytesPerSecond = perSecond(current.rxBytes - previous.rxBytes, elapsedMs),
            txBytesPerSecond = perSecond(current.txBytes - previous.txBytes, elapsedMs),
        )
    }

    private fun perSecond(deltaBytes: Long, elapsedMs: Long): Double =
        if (deltaBytes > 0L && elapsedMs > 0L) deltaBytes * MILLIS_PER_SECOND / elapsedMs else NO_RATE

    /** Both counters plus the monotonic instant they were read at, so the interval cannot come from elsewhere. */
    private data class TrafficCounters(
        val rxBytes: Long,
        val txBytes: Long,
        val elapsedRealtimeMs: Long,
    )

    private companion object {

        /** One reading per second, matching the cap the composed Monitor snapshot already applies. */
        const val SAMPLE_INTERVAL_MS = 1000L

        const val MILLIS_PER_SECOND = 1000.0

        /** What a counter reset or a zero-length interval reports, so neither draws a spike. */
        const val NO_RATE = 0.0
    }
}
