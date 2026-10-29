package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /**
     * S4468: Compose tears a word wider than its cell by characters and counts that against `maxLines`,
     * so only the break position tells a tear from a wrap. These are the four captions the owner saw
     * torn on the watch, with the break offsets read off the screenshots.
     */
    @Test
    fun `a break through the middle of a word is a tear`() {
        assertTrue(breaksMidWord("tone_test", listOf("tone_te".length)))
        assertTrue(breaksMidWord("Тестовый звук ёж", listOf("Тестов".length, "Тестовый".length)))
        assertTrue(breaksMidWord("193.178.50.43", listOf("193.178".length)))
        assertTrue(breaksMidWord("SM-L310", listOf("SM-L31".length)))
    }

    @Test
    fun `a break on the whitespace that wrapped the line is clean`() {
        assertFalse(breaksMidWord("Тестовый звук ёж", listOf("Тестовый ".length, "Тестовый звук ".length)))
    }

    @Test
    fun `a break behind a hyphen or a slash is clean`() {
        assertFalse(breaksMidWord("my-file name", listOf("my-".length)))
        assertFalse(breaksMidWord("music/rock", listOf("music/".length)))
    }

    @Test
    fun `a caption on one line has no break to judge`() {
        assertFalse(breaksMidWord("tone_test", emptyList()))
    }

    @Test
    fun `an offset outside the text is not a tear`() {
        assertFalse(breaksMidWord("abc", listOf(0, 3, 9)))
    }

    /**
     * S4469: the grid's shared size is only right if a late or stale report cannot lift what another
     * cell already lowered, so the property is asserted without a composition.
     */
    @Test
    fun `a caption group starts at the ceiling`() {
        assertEquals(WearCaptionScale.Ceiling.value, WearCaptionGroup().size.value, EXACT)
    }

    @Test
    fun `a smaller report lowers the group size`() {
        val group = WearCaptionGroup()
        group.lowerTo(14f.sp)
        assertEquals(14f, group.size.value, EXACT)
    }

    @Test
    fun `a larger report leaves the group size alone`() {
        val group = WearCaptionGroup()
        group.lowerTo(13f.sp)
        group.lowerTo(15f.sp)
        assertEquals(13f, group.size.value, EXACT)
    }

    @Test
    fun `two reports settle on the smaller one in either order`() {
        val first = WearCaptionGroup().apply {
            lowerTo(15f.sp)
            lowerTo(13f.sp)
        }
        val second = WearCaptionGroup().apply {
            lowerTo(13f.sp)
            lowerTo(15f.sp)
        }
        assertEquals(13f, first.size.value, EXACT)
        assertEquals(13f, second.size.value, EXACT)
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
