package com.sza.fastmediasorter.ui.common.widget.dimclock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DimClockTickerTest {

    @Test
    fun cadenceSelectionMatchesSecondsVisibility() {
        val ticker = DimClockTicker()
        assertEquals(DimClockTicker.CADENCE_SECONDS_MS, ticker.getCadenceMs(secondsVisible = true))
        assertEquals(DimClockTicker.CADENCE_NORMAL_MS, ticker.getCadenceMs(secondsVisible = false))
    }

    @Test
    fun burnInOffsetCyclesThroughPositions() {
        val ticker = DimClockTicker()
        val initial = ticker.currentBurnInOffsetDp()
        assertEquals(Pair(0.0f, 0.0f), initial)

        val next = ticker.nextBurnInOffsetDp()
        assertEquals(Pair(DimClockTicker.BURN_IN_AMPLITUDE_DP, 0.0f), next)

        val positions = mutableSetOf<Pair<Float, Float>>()
        positions.add(initial)
        positions.add(next)
        repeat(10) {
            positions.add(ticker.nextBurnInOffsetDp())
        }
        assertTrue(positions.size > 2)
    }

    @Test
    fun burnInUpdateAdvancesOnlyAfterPeriod() {
        var simulatedTime = 5000L
        val ticker = DimClockTicker(clock = { simulatedTime })

        // Inside the shift period: the offset holds and the cycle does not advance.
        assertEquals(Pair(0.0f, 0.0f), ticker.updateBurnInOffset())

        simulatedTime += DimClockTicker.BURN_IN_PERIOD_MS
        assertEquals(
            Pair(DimClockTicker.BURN_IN_AMPLITUDE_DP, 0.0f),
            ticker.updateBurnInOffset(),
        )

        // Just before the next period elapses: still the same position.
        simulatedTime += DimClockTicker.BURN_IN_PERIOD_MS - 1
        assertEquals(
            Pair(DimClockTicker.BURN_IN_AMPLITUDE_DP, 0.0f),
            ticker.updateBurnInOffset(),
        )
    }

    @Test
    fun autoFadeTransitionRespectsIdleThreshold() {
        var simulatedTime = 1000L
        val ticker = DimClockTicker(clock = { simulatedTime })

        // Below idle threshold
        assertEquals(DimClockTicker.FULL_ALPHA, ticker.computeAutoFadeAlpha(), 0.001f)

        // Advance past threshold
        simulatedTime += DimClockTicker.FADE_DELAY_MS
        assertEquals(DimClockTicker.FADE_FLOOR_ALPHA, ticker.computeAutoFadeAlpha(), 0.001f)

        // User activity resets alpha
        ticker.onUserActivity()
        assertEquals(DimClockTicker.FULL_ALPHA, ticker.computeAutoFadeAlpha(), 0.001f)
    }
}
