package com.sza.fastmediasorter.wear.ui.player.common

import org.junit.Assert.assertEquals
import org.junit.Test

class RotaryStepAccumulatorTest {

    private fun collect(accumulator: RotaryStepAccumulator, vararg deltas: Float): List<Int> {
        val steps = mutableListOf<Int>()
        deltas.forEach { delta -> accumulator.add(delta) { step -> steps += step } }
        return steps
    }

    @Test
    fun `small deltas accumulate to one step at the default threshold`() {
        val steps = collect(RotaryStepAccumulator(), 16f, 16f, 15f)
        assertEquals(emptyList<Int>(), steps)

        val crossing = collect(RotaryStepAccumulator(), 16f, 16f, 16f)
        assertEquals(listOf(1), crossing)
    }

    @Test
    fun `one large delta emits every whole step it covers`() {
        assertEquals(listOf(1, 1, 1), collect(RotaryStepAccumulator(), 150f))
    }

    @Test
    fun `residue is kept across calls instead of being rounded away`() {
        assertEquals(listOf(1), collect(RotaryStepAccumulator(), 47f, 47f))
        assertEquals(listOf(1, 1), collect(RotaryStepAccumulator(), 47f, 47f, 2f))
    }

    @Test
    fun `direction reversal drops the pending travel`() {
        assertEquals(emptyList<Int>(), collect(RotaryStepAccumulator(), 40f, -40f))
        assertEquals(listOf(-1), collect(RotaryStepAccumulator(), 40f, -48f))
    }

    @Test
    fun `negative travel emits negative steps`() {
        assertEquals(listOf(-1, -1), collect(RotaryStepAccumulator(), -96f))
    }

    @Test
    fun `a zero delta neither emits nor resets the pending travel`() {
        assertEquals(listOf(1), collect(RotaryStepAccumulator(), 40f, 0f, 8f))
    }

    @Test
    fun `an explicit step size overrides the default`() {
        assertEquals(listOf(1), collect(RotaryStepAccumulator(stepPixels = 120f), 120f))
        assertEquals(emptyList<Int>(), collect(RotaryStepAccumulator(stepPixels = 120f), 48f))
    }
}
