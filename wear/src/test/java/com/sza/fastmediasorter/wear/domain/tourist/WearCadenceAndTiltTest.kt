package com.sza.fastmediasorter.wear.domain.tourist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearCadenceAndTiltTest {

    @Test
    fun `a steady stream reads one hundred twenty steps a minute`() {
        val meter = WearCadenceMeter()
        var cadence: Int? = null
        for (second in 0..30) {
            cadence = meter.onSteps(second * 2L, T0 + second * SECOND)
        }

        assertEquals(120, cadence)
    }

    @Test
    fun `a short window has no cadence`() {
        val meter = WearCadenceMeter()
        meter.onSteps(0L, T0)

        assertNull(meter.onSteps(10L, T0 + 5 * SECOND))
    }

    @Test
    fun `a steps reset starts the window over`() {
        val meter = WearCadenceMeter()
        meter.onSteps(100L, T0)
        meter.onSteps(140L, T0 + 20 * SECOND)

        assertNull(meter.onSteps(0L, T0 + 21 * SECOND))
    }

    @Test
    fun `a flat watch is level`() {
        val tilt = WearTiltCalculator.tilt(0f, 0f, GRAVITY)

        assertEquals(0f, tilt.pitchDegrees, ANGLE_DELTA)
        assertEquals(0f, tilt.rollDegrees, ANGLE_DELTA)
    }

    @Test
    fun `a watch on its edge reads ninety degrees`() {
        assertEquals(90f, WearTiltCalculator.tilt(0f, GRAVITY, 0f).pitchDegrees, ANGLE_DELTA)
        assertEquals(-90f, WearTiltCalculator.tilt(GRAVITY, 0f, 0f).rollDegrees, ANGLE_DELTA)
    }

    private companion object {
        const val T0 = 1_000_000L
        const val SECOND = 1_000L
        const val GRAVITY = 9.81f
        const val ANGLE_DELTA = 0.01f
    }
}
