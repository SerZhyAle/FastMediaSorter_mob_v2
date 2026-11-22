package com.sza.fastmediasorter.data.remote.exchange

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S4815: the keepalive follows real time, which keeps running while the CPU sleeps and the coroutine clock stands. */
class ExchangeKeepalivePacerTest {

    private var clock = 0L
    private val pacer = ExchangeKeepalivePacer(INTERVAL_MS) { clock }

    @Test
    fun `a keepalive is not owed before a whole interval has passed`() {
        clock = INTERVAL_MS - 1
        assertFalse(pacer.due())
    }

    @Test
    fun `a keepalive is owed once the interval has passed`() {
        clock = INTERVAL_MS
        assertTrue(pacer.due())
    }

    @Test
    fun `the window is missed only past three silent intervals`() {
        clock = INTERVAL_MS * 3
        assertFalse(pacer.windowMissed())
        clock += 1
        assertTrue(pacer.windowMissed())
    }

    @Test
    fun `a frame from the server resets the silence`() {
        clock = INTERVAL_MS * 3
        pacer.heard()
        clock += INTERVAL_MS
        assertEquals(INTERVAL_MS, pacer.silentForMs())
        assertFalse(pacer.windowMissed())
    }

    @Test
    fun `a sleep of several intervals owes a keepalive at once without missing the window`() {
        clock = INTERVAL_MS * 2 + 5_000
        assertTrue(pacer.due())
        assertFalse(pacer.windowMissed())
    }

    @Test
    fun `a sleep past the window is reported missed so the dead stream is dropped`() {
        clock = INTERVAL_MS * 10
        assertTrue(pacer.due())
        assertTrue(pacer.windowMissed())
    }

    @Test
    fun `pace sends one keepalive per interval while the server answers`() = runTest {
        var sent = 0
        val live = ExchangeKeepalivePacer(INTERVAL_MS) { currentTime }
        val job = launch {
            live.pace {
                sent++
                live.heard()
                true
            }
        }
        advanceTimeBy(INTERVAL_MS * 3 + 1)
        runCurrent()
        assertEquals(3, sent)
        assertFalse(live.ended)
        job.cancel()
    }

    @Test
    fun `pace sends at the first tick after a sleep that spent the interval`() = runTest {
        var sent = 0
        var slept = 0L
        val live = ExchangeKeepalivePacer(INTERVAL_MS) { currentTime + slept }
        val job = launch {
            live.pace {
                sent++
                live.heard()
                true
            }
        }
        advanceTimeBy(ExchangeKeepalivePacer.TICK_MS + 1)
        assertEquals(0, sent)
        slept = INTERVAL_MS * 2
        advanceTimeBy(ExchangeKeepalivePacer.TICK_MS)
        assertEquals(1, sent)
        job.cancel()
    }

    @Test
    fun `pace ends when the server stops answering for three intervals`() = runTest {
        var sent = 0
        val live = ExchangeKeepalivePacer(INTERVAL_MS) { currentTime }
        launch {
            live.pace {
                sent++
                true
            }
        }
        advanceUntilIdle()
        assertTrue(live.ended)
        assertEquals(3, sent)
    }

    @Test
    fun `pace ends when the write fails`() = runTest {
        var sent = 0
        val live = ExchangeKeepalivePacer(INTERVAL_MS) { currentTime }
        launch {
            live.pace {
                sent++
                false
            }
        }
        advanceUntilIdle()
        assertTrue(live.ended)
        assertEquals(1, sent)
    }

    private companion object {
        const val INTERVAL_MS = 10_000L
    }
}
