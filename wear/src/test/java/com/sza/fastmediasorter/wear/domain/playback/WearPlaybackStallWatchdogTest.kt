package com.sza.fastmediasorter.wear.domain.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2848: the timer is the whole fix, so its three behaviours are pinned on virtual time - it fires
 * after the timeout, a recovery cancels it, and a stall that keeps reporting itself does not push its
 * own deadline back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearPlaybackStallWatchdogTest {

    private companion object {
        const val TIMEOUT_MS = WEAR_PLAYBACK_STALL_TIMEOUT_MS
    }

    @Test
    fun `a stall that lasts the timeout fires once`() = runTest {
        var fired = 0
        val watchdog = WearPlaybackStallWatchdog(this, TIMEOUT_MS) { fired++ }

        watchdog.onActivityChanged(BackgroundPlaybackActivity.Stalled)
        advanceTimeBy(TIMEOUT_MS + 1)
        runCurrent()

        assertEquals(1, fired)
    }

    @Test
    fun `a stall shorter than the timeout does not fire`() = runTest {
        var fired = 0
        val watchdog = WearPlaybackStallWatchdog(this, TIMEOUT_MS) { fired++ }

        watchdog.onActivityChanged(BackgroundPlaybackActivity.Stalled)
        advanceTimeBy(TIMEOUT_MS - 1)
        runCurrent()

        assertEquals(0, fired)
        watchdog.cancel()
    }

    @Test
    fun `a stream that recovers cancels the timer`() = runTest {
        var fired = 0
        val watchdog = WearPlaybackStallWatchdog(this, TIMEOUT_MS) { fired++ }

        watchdog.onActivityChanged(BackgroundPlaybackActivity.Stalled)
        advanceTimeBy(TIMEOUT_MS / 2)
        watchdog.onActivityChanged(BackgroundPlaybackActivity.Playing)
        advanceTimeBy(TIMEOUT_MS)
        runCurrent()

        assertEquals(0, fired)
    }

    @Test
    fun `a pause cancels the timer`() = runTest {
        var fired = 0
        val watchdog = WearPlaybackStallWatchdog(this, TIMEOUT_MS) { fired++ }

        watchdog.onActivityChanged(BackgroundPlaybackActivity.Stalled)
        watchdog.onActivityChanged(BackgroundPlaybackActivity.Settled)
        advanceTimeBy(TIMEOUT_MS + 1)
        runCurrent()

        assertEquals(0, fired)
    }

    @Test
    fun `repeated stall reports do not postpone the deadline`() = runTest {
        var fired = 0
        val watchdog = WearPlaybackStallWatchdog(this, TIMEOUT_MS) { fired++ }

        watchdog.onActivityChanged(BackgroundPlaybackActivity.Stalled)
        repeat(10) {
            advanceTimeBy(TIMEOUT_MS / 10)
            watchdog.onActivityChanged(BackgroundPlaybackActivity.Stalled)
        }
        advanceTimeBy(1)
        runCurrent()

        assertEquals(1, fired)
    }

    @Test
    fun `a stall after a recovery arms a fresh timer`() = runTest {
        var fired = 0
        val watchdog = WearPlaybackStallWatchdog(this, TIMEOUT_MS) { fired++ }

        watchdog.onActivityChanged(BackgroundPlaybackActivity.Stalled)
        watchdog.onActivityChanged(BackgroundPlaybackActivity.Playing)
        watchdog.onActivityChanged(BackgroundPlaybackActivity.Stalled)
        advanceTimeBy(TIMEOUT_MS + 1)
        runCurrent()

        assertEquals(1, fired)
    }
}
