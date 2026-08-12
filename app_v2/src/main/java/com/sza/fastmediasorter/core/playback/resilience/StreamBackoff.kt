package com.sza.fastmediasorter.core.playback.resilience

/**
 * Retry-delay arithmetic shared by the stream retry ladders (S1513).
 *
 * Neither shape is new. The audio service and the inline mini-player already computed the identical
 * `base shl attempt` capped value from the same `RadioStreamBufferConfig` constants, and the video
 * behind-live-window branch the linear one; owning the arithmetic here is what stops three copies
 * of the same formula from drifting apart unnoticed, and what makes the ladders assertable on the
 * JVM without a player.
 *
 * Deliberately free of Android and Media3 types - the caller keeps the scheduling.
 */
internal object StreamBackoff {

    /**
     * Shift ceiling the audio sites use. They clamp the stored attempt index rather than the
     * result, which is what keeps a long outage from shifting a Long past its width.
     */
    const val DEFAULT_MAX_SHIFT = 2

    /**
     * Doubling ladder `baseMs shl attempt`, flattened at [capMs].
     *
     * [attempt] is clamped rather than trusted because a call site holding a raw failure counter
     * would otherwise hand over an unbounded index: a negative shift throws and an oversized one
     * wraps, so a runaway counter would turn the ladder into a crash or into no wait at all.
     */
    fun exponentialDelayMs(
        attempt: Int,
        baseMs: Long,
        capMs: Long,
        maxShift: Int = DEFAULT_MAX_SHIFT,
    ): Long {
        val shift = attempt.coerceIn(0, maxShift.coerceAtLeast(0))
        return (baseMs shl shift).coerceAtMost(capMs)
    }

    /**
     * Linear ladder `attempt * stepMs`, flattened at [capMs].
     *
     * Used where the failure is a routine live-edge desync rather than a dead endpoint, so a short
     * predictable wait recovers faster than a doubling one.
     */
    fun linearDelayMs(attempt: Int, stepMs: Long, capMs: Long): Long {
        val steps = attempt.coerceAtLeast(0)
        return (steps * stepMs).coerceAtMost(capMs)
    }
}
