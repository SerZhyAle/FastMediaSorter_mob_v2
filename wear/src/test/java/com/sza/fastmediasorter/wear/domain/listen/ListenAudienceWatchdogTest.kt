package com.sza.fastmediasorter.wear.domain.listen

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2939: the watchdog is what stands between one lost STOP and a watch drained overnight, so its
 * verdict is pinned on virtual time - silence past the limit ends the session once, a steady audience
 * never does, and the limit counts from the last byte taken rather than from the start.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListenAudienceWatchdogTest {

    private companion object {
        const val LIMIT_MS = LISTEN_ABANDON_AFTER_MS
        const val CHECK_MS = LISTEN_AUDIENCE_CHECK_INTERVAL_MS
    }

    private var lastProgressAt = 0L
    private var fired = 0

    private fun TestScope.watchdog() = ListenAudienceWatchdog(
        scope = this,
        abandonAfterMs = LIMIT_MS,
        checkIntervalMs = CHECK_MS,
        now = { testScheduler.currentTime },
        lastAudienceProgressAt = { lastProgressAt },
        onAbandoned = { fired++ }
    )

    @Test
    fun `nobody taking audio for the limit ends the session once`() = runTest {
        watchdog().start()

        advanceTimeBy(LIMIT_MS + CHECK_MS)
        runCurrent()
        advanceTimeBy(LIMIT_MS * 2)
        runCurrent()

        assertEquals(1, fired)
    }

    @Test
    fun `a steady audience never ends the session`() = runTest {
        val watchdog = watchdog()
        watchdog.start()

        repeat(times = 30) {
            advanceTimeBy(CHECK_MS)
            lastProgressAt = testScheduler.currentTime
            runCurrent()
        }

        assertEquals(0, fired)
        watchdog.cancel()
    }

    @Test
    fun `the limit counts from the last byte taken`() = runTest {
        val watchdog = watchdog()
        watchdog.start()
        advanceTimeBy(LIMIT_MS / 2)
        lastProgressAt = testScheduler.currentTime

        advanceTimeBy(LIMIT_MS - CHECK_MS)
        runCurrent()
        assertEquals(0, fired)

        advanceTimeBy(CHECK_MS * 2)
        runCurrent()
        assertEquals(1, fired)
    }

    @Test
    fun `a cancelled watchdog never fires`() = runTest {
        val watchdog = watchdog()
        watchdog.start()
        advanceTimeBy(LIMIT_MS / 2)

        watchdog.cancel()
        advanceTimeBy(LIMIT_MS * 2)
        runCurrent()

        assertEquals(0, fired)
    }
}
