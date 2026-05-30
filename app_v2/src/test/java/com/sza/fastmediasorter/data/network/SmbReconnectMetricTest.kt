package com.sza.fastmediasorter.data.network

import org.junit.Test

/**
 * Unit tests for [SmbReconnectMetric] - bounded ring-buffer + rolling-window thresholding.
 *
 * The class has no observable return value (it only emits a throttled Timber.w line), so these
 * tests assert the absence of crashes across the branch matrix: capacity eviction, window
 * eviction, threshold crossing, and notify-cooldown. A deterministic injected clock keeps the
 * sliding window reproducible; no wall-clock or real waiting is involved.
 */
class SmbReconnectMetricTest {

    @Test
    fun `records below threshold without crossing notify`() {
        val metric = SmbReconnectMetric(countThreshold = 3, windowMs = 1000, notifyCooldownMs = 1000)
        metric.record(now = 0)
        metric.record(now = 100)
        // 2 events < threshold 3 - no notify path exercised.
    }

    @Test
    fun `crosses threshold within window`() {
        val metric = SmbReconnectMetric(countThreshold = 3, windowMs = 1000, notifyCooldownMs = 1000)
        metric.record(now = 0)
        metric.record(now = 100)
        metric.record(now = 200) // 3rd within window -> notify branch
    }

    @Test
    fun `window eviction drops stale timestamps before threshold`() {
        val metric = SmbReconnectMetric(countThreshold = 3, windowMs = 1000, notifyCooldownMs = 1000)
        metric.record(now = 0)
        metric.record(now = 100)
        // Far outside the 1000ms window - earlier entries evicted, count stays at 1.
        metric.record(now = 5000)
    }

    @Test
    fun `capacity eviction keeps buffer bounded`() {
        val metric = SmbReconnectMetric(
            countThreshold = 100, // never notify - we only exercise the capacity path
            windowMs = 1_000_000,
            notifyCooldownMs = 1000,
            capacity = 4
        )
        repeat(20) { i -> metric.record(now = i.toLong()) }
        // Survives well beyond capacity without unbounded growth or exception.
    }

    @Test
    fun `notify cooldown suppresses repeated notifications`() {
        val metric = SmbReconnectMetric(countThreshold = 2, windowMs = 100_000, notifyCooldownMs = 10_000)
        metric.record(now = 0)
        metric.record(now = 1)     // crosses threshold -> notify at t=1
        metric.record(now = 2)     // still within cooldown -> suppressed
        metric.record(now = 20_000) // past cooldown -> notify again
    }

    @Test
    fun `default constructor accepts records`() {
        val metric = SmbReconnectMetric()
        metric.record(now = 1_000)
    }
}
