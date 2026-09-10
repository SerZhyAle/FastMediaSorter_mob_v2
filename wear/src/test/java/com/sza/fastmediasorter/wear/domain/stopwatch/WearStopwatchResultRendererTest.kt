package com.sza.fastmediasorter.wear.domain.stopwatch

import org.junit.Assert.assertEquals
import org.junit.Test

class WearStopwatchResultRendererTest {

    private val labels = WearStopwatchResultLabels(
        participant = { number -> "P$number" },
        lap = { number -> "L$number" }
    )

    private val start = 1_000L

    @Test
    fun `a single participant without laps is one line`() {
        var state = WearStopwatchEngine.startOrLap(WearStopwatchState.initial(1), 0, start)
        state = WearStopwatchEngine.stopOrReset(state, 0, start + 2_000L)

        assertEquals("P1: 0:02.00", WearStopwatchResultRenderer.render(state, start + 9_000L, labels))
    }

    @Test
    fun `each lap carries its split and its running total`() {
        var state = WearStopwatchEngine.startOrLap(WearStopwatchState.initial(1), 0, start)
        state = WearStopwatchEngine.startOrLap(state, 0, start + 1_000L)
        state = WearStopwatchEngine.startOrLap(state, 0, start + 2_500L)
        state = WearStopwatchEngine.stopOrReset(state, 0, start + 3_000L)

        val expected = listOf(
            "P1: 0:03.00",
            "  L1: 0:01.00 / 0:01.00",
            "  L2: 0:01.50 / 0:02.50"
        ).joinToString("\n")
        assertEquals(expected, WearStopwatchResultRenderer.render(state, start + 9_000L, labels))
    }

    @Test
    fun `every participant appears, including an untouched one`() {
        var state = WearStopwatchEngine.startOrLap(WearStopwatchState.initial(2), 0, start)
        state = WearStopwatchEngine.stopOrReset(state, 0, start + 500L)

        val expected = listOf("P1: 0:00.50", "P2: 0:00.00").joinToString("\n")
        assertEquals(expected, WearStopwatchResultRenderer.render(state, start + 9_000L, labels))
    }
}
