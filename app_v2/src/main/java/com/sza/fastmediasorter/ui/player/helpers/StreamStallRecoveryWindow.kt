package com.sza.fastmediasorter.ui.player.helpers

/**
 * Bounds retries during one unstable period without permanently exhausting a healthy long session.
 *
 * The caller supplies monotonic time so the window remains correct across wall-clock changes and
 * the policy stays JVM-testable. A returned value is the ordinal used in the recovery diagnostic;
 * null means the next recovery must surface the normal playback error instead.
 */
internal class StreamStallRecoveryWindow(
    private val maxAttempts: Int = STREAM_MAX_WATCHDOG_RECOVERIES,
    private val windowMs: Long = RECOVERY_WINDOW_MS,
) {

    private val attemptTimes = ArrayDeque<Long>()

    fun tryAcquire(nowMs: Long): Int? {
        while (attemptTimes.firstOrNull()?.let { nowMs - it > windowMs } == true) {
            attemptTimes.removeFirst()
        }
        if (attemptTimes.size >= maxAttempts) return null
        attemptTimes.addLast(nowMs)
        return attemptTimes.size
    }

    fun clear() {
        attemptTimes.clear()
    }

    private companion object {
        const val RECOVERY_WINDOW_MS = 120_000L
    }
}
