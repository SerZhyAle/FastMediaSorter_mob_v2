package com.sza.fastmediasorter.wear.ui.apps.waterflashlight

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S3394: the acceptance of the fix is a swim, so the timing rules that separate the owner's exit from
 * a water-born key press or back gesture are proven here instead of on a wrist.
 */
class WaterFlashlightExitGateTest {

    private val gate = WaterFlashlightExitGate()

    @Test
    fun `short press does not leave`() {
        gate.onKeyDown(KEY, 1_000L)

        assertFalse(gate.onKeyUp(KEY, 1_000L + WaterFlashlightExitGate.EXIT_HOLD_MS - 1))
    }

    @Test
    fun `held press leaves`() {
        gate.onKeyDown(KEY, 1_000L)

        assertTrue(gate.onKeyUp(KEY, 1_000L + WaterFlashlightExitGate.EXIT_HOLD_MS))
    }

    @Test
    fun `release of another key is not the press being held`() {
        gate.onKeyDown(KEY, 1_000L)

        assertFalse(gate.onKeyUp(OTHER_KEY, 1_000L + WaterFlashlightExitGate.EXIT_HOLD_MS))
    }

    @Test
    fun `platform long press leaves however short the wall clock says it was`() {
        gate.onKeyDown(KEY, 1_000L)
        gate.onKeyDown(KEY, 1_000L, longPress = true)

        assertTrue(gate.onKeyUp(KEY, 1_000L))
    }

    @Test
    fun `repeated downs of a held key do not restart the hold`() {
        gate.onKeyDown(KEY, 1_000L)
        gate.onKeyDown(KEY, 1_000L + WaterFlashlightExitGate.EXIT_HOLD_MS)

        assertTrue(gate.onKeyUp(KEY, 1_000L + WaterFlashlightExitGate.EXIT_HOLD_MS))
    }

    @Test
    fun `a long press does not carry over to the next press`() {
        gate.onKeyDown(KEY, 1_000L, longPress = true)
        assertTrue(gate.onKeyUp(KEY, 1_000L))

        gate.onKeyDown(KEY, 5_000L)

        assertFalse(gate.onKeyUp(KEY, 5_100L))
    }

    @Test
    fun `single orphan release does not leave`() {
        assertFalse(gate.onKeyUp(KEY, 1_000L))
    }

    @Test
    fun `three orphan releases inside the window leave`() {
        assertFalse(gate.onKeyUp(KEY, 1_000L))
        assertFalse(gate.onKeyUp(KEY, 1_200L))

        assertTrue(gate.onKeyUp(KEY, 1_400L))
    }

    @Test
    fun `orphan releases spread past the window do not leave`() {
        val step = WaterFlashlightExitGate.STREAK_WINDOW_MS + 1

        assertFalse(gate.onKeyUp(KEY, 1_000L))
        assertFalse(gate.onKeyUp(KEY, 1_000L + step))
        assertFalse(gate.onKeyUp(KEY, 1_000L + step * 2))
    }

    @Test
    fun `single back does not leave`() {
        assertFalse(gate.onBack(1_000L))
    }

    @Test
    fun `three backs inside the window leave`() {
        assertFalse(gate.onBack(1_000L))
        assertFalse(gate.onBack(1_300L))

        assertTrue(gate.onBack(1_600L))
    }

    @Test
    fun `backs spread past the window do not leave`() {
        val step = WaterFlashlightExitGate.STREAK_WINDOW_MS + 1

        assertFalse(gate.onBack(1_000L))
        assertFalse(gate.onBack(1_000L + step))
        assertFalse(gate.onBack(1_000L + step * 2))
    }

    @Test
    fun `a back after a completed streak starts a new one`() {
        gate.onBack(1_000L)
        gate.onBack(1_100L)
        assertTrue(gate.onBack(1_200L))

        assertFalse(gate.onBack(1_300L))
    }

    @Test
    fun `a matched short press breaks the back streak`() {
        gate.onBack(1_000L)
        gate.onBack(1_100L)
        gate.onKeyDown(KEY, 1_200L)
        assertFalse(gate.onKeyUp(KEY, 1_250L))

        assertFalse(gate.onBack(1_300L))
    }

    private companion object {
        const val KEY = 4L
        const val OTHER_KEY = 264L
    }
}
