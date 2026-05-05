package com.sza.fastmediasorter.data.network

import timber.log.Timber

/**
 * S0061 Phase 05: bounded ring-buffer of reconnect timestamps used to detect a flaky
 * LAN/Wi-Fi environment vs. a one-off server restart.
 *
 * Extracted from [SmbConnectionManager] in S0061 Phase 01 to keep the manager below
 * the project 1000-LOC limit. The class owns no SMB state — it is a pure counter.
 */
class SmbReconnectMetric(
    private val countThreshold: Int = 3,
    private val windowMs: Long = 5 * 60 * 1000L,
    private val notifyCooldownMs: Long = 10 * 60 * 1000L,
    private val capacity: Int = 8
) {
    private val timestamps = ArrayDeque<Long>(capacity)

    @Volatile
    private var lastNotifyAt = 0L

    /**
     * Record a reconnect event at the current wall-clock instant. Emits one Timber.w
     * line per [notifyCooldownMs] when the rolling-window count crosses [countThreshold].
     */
    fun record(now: Long = System.currentTimeMillis()) {
        synchronized(timestamps) {
            timestamps.addLast(now)
            while (timestamps.size > capacity ||
                (timestamps.isNotEmpty() && now - timestamps.first() > windowMs)
            ) {
                timestamps.removeFirst()
            }
            if (timestamps.size >= countThreshold) {
                maybeNotify(now, timestamps.size)
            }
        }
    }

    private fun maybeNotify(now: Long, count: Int) {
        if (now - lastNotifyAt >= notifyCooldownMs) {
            lastNotifyAt = now
            Timber.w(
                "SMB network unstable: $count reconnects in the last " +
                    "${windowMs / 1000}s — check Wi-Fi / switch stability"
            )
        }
    }
}
