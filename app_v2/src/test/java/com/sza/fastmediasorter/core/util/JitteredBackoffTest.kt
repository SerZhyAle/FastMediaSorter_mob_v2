package com.sza.fastmediasorter.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class JitteredBackoffTest {

    @Test
    fun `delays double from one second and stay within the jitter band`() {
        val backoff = JitteredBackoff(random = Random(SEED))
        var base = JitteredBackoff.DEFAULT_INITIAL_MS
        repeat(STEPS) {
            val delay = backoff.nextDelayMs()
            assertTrue("$delay in [${base / 2}, $base]", delay in base / 2..base)
            base = (base * 2).coerceAtMost(JitteredBackoff.DEFAULT_MAX_MS)
        }
    }

    @Test
    fun `delays never exceed five minutes`() {
        val backoff = JitteredBackoff(random = Random(SEED))
        val delays = List(STEPS) { backoff.nextDelayMs() }
        assertTrue(delays.all { it <= JitteredBackoff.DEFAULT_MAX_MS })
        assertTrue(delays.last() >= JitteredBackoff.DEFAULT_MAX_MS / 2)
    }

    @Test
    fun `reset returns to the initial delay`() {
        val backoff = JitteredBackoff(initialMs = INITIAL, maxMs = INITIAL * 8, random = Random(SEED))
        repeat(STEPS) { backoff.nextDelayMs() }

        backoff.reset()

        assertTrue(backoff.nextDelayMs() <= INITIAL)
    }

    @Test
    fun `a zero jitter source yields the lower band edge`() {
        val backoff = JitteredBackoff(initialMs = INITIAL, maxMs = INITIAL * 8, random = ZeroRandom)
        assertEquals(INITIAL / 2, backoff.nextDelayMs())
        assertEquals(INITIAL, backoff.nextDelayMs())
    }

    private object ZeroRandom : Random() {
        override fun nextBits(bitCount: Int): Int = 0

        override fun nextLong(until: Long): Long = 0L
    }

    private companion object {
        const val SEED = 42
        const val STEPS = 20
        const val INITIAL = 1_000L
    }
}
