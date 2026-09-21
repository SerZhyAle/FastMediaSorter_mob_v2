package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2129: the shrink loop in [WearCaptionText] rests entirely on [WearCaptionScale.nextSmaller]
 * terminating and never answering below the floor. A step function that failed either property
 * would recompose forever on a caption that cannot fit, so both are asserted rather than assumed.
 */
class WearCaptionScaleTest {

    @Test
    fun `steps down by one from the ceiling`() {
        val next = WearCaptionScale.nextSmaller(WearCaptionScale.Ceiling)
        assertEquals(15f, next?.value)
    }

    /**
     * S3362: Wear OS review item WO-V14 sets 12sp as the smallest size essential text may take, and
     * this floor is what every list and cell caption reaches on a long label or a large font scale.
     * Pinned as a number because the review judges the number, not the mechanism around it.
     */
    @Test
    fun `the floor is the smallest size the Wear review allows for essential text`() {
        assertEquals(WO_V14_MIN_ESSENTIAL_SP, WearCaptionScale.Floor.value, EXACT)
    }

    @Test
    fun `answers null at the floor`() {
        assertNull(WearCaptionScale.nextSmaller(WearCaptionScale.Floor))
    }

    @Test
    fun `never answers below the floor from any starting size`() {
        var current = WearCaptionScale.Ceiling
        while (true) {
            val next = WearCaptionScale.nextSmaller(current) ?: break
            assertTrue(
                "stepped below the floor: ${next.value}",
                next.value >= WearCaptionScale.Floor.value
            )
            current = next
        }
    }

    @Test
    fun `answers null for a size already below the floor`() {
        assertNull(WearCaptionScale.nextSmaller(1f.sp))
    }

    @Test
    fun `terminates in a finite number of steps from the ceiling`() {
        var current = WearCaptionScale.nextSmaller(WearCaptionScale.Ceiling)
        var steps = 0
        while (current != null) {
            current = WearCaptionScale.nextSmaller(current)
            steps++
            assertTrue("step function did not terminate", steps <= MAX_EXPECTED_STEPS)
        }
        assertTrue("expected at least one step below the ceiling", steps > 0)
    }

    private companion object {
        // Ceiling to floor at one sp per step, with headroom - a bound that fails loudly rather
        // than letting a non-terminating step run the suite out of time.
        const val MAX_EXPECTED_STEPS = 100

        /** WO-V14: essential text may not be drawn below 12sp. */
        const val WO_V14_MIN_ESSENTIAL_SP = 12f

        /** The floor is a declared constant, so the comparison is exact rather than approximate. */
        const val EXACT = 0f
    }
}
