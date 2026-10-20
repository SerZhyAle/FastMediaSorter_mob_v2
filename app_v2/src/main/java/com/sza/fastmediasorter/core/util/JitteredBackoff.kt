package com.sza.fastmediasorter.core.util

import kotlin.random.Random

/**
 * S4231: exponential back-off with jitter, the shape contract DEVICE-EXCHANGE item CQ fixes for Drive rate
 * limits and server errors - from 1 second, doubling to at most 5 minutes. The jitter keeps two devices of
 * one account that failed together from retrying in lockstep.
 */
class JitteredBackoff(
    private val initialMs: Long = DEFAULT_INITIAL_MS,
    private val maxMs: Long = DEFAULT_MAX_MS,
    private val random: Random = Random.Default,
) {

    private var nextBaseMs = initialMs

    /** The wait before the next attempt, in `[base / 2, base]`; each call doubles the base up to [maxMs]. */
    fun nextDelayMs(): Long {
        val base = nextBaseMs
        nextBaseMs = (base * 2).coerceAtMost(maxMs)
        val half = base / 2
        return half + random.nextLong(base - half + 1)
    }

    fun reset() {
        nextBaseMs = initialMs
    }

    companion object {
        const val DEFAULT_INITIAL_MS: Long = 1_000L
        const val DEFAULT_MAX_MS: Long = 300_000L
    }
}
