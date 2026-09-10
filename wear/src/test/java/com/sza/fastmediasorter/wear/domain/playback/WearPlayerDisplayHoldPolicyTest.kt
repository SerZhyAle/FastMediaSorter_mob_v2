package com.sza.fastmediasorter.wear.domain.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2849: all four combinations, because the defect was the one case the old condition got wrong -
 * dimmed with nothing left to protect - and it is indistinguishable from the working case unless the
 * second input is actually consulted.
 */
class WearPlayerDisplayHoldPolicyTest {

    @Test
    fun `dimmed over a session that wants to play holds the display`() {
        assertTrue(
            WearPlayerDisplayHoldPolicy.holdsDisplay(
                isDimmed = true,
                isPlaybackRequested = true
            )
        )
    }

    @Test
    fun `dimmed over a paused session releases the display`() {
        assertFalse(
            WearPlayerDisplayHoldPolicy.holdsDisplay(
                isDimmed = true,
                isPlaybackRequested = false
            )
        )
    }

    @Test
    fun `a lit screen never holds the display on playback alone`() {
        assertFalse(
            WearPlayerDisplayHoldPolicy.holdsDisplay(
                isDimmed = false,
                isPlaybackRequested = true
            )
        )
    }

    @Test
    fun `neither dimmed nor playing releases the display`() {
        assertFalse(
            WearPlayerDisplayHoldPolicy.holdsDisplay(
                isDimmed = false,
                isPlaybackRequested = false
            )
        )
    }
}
