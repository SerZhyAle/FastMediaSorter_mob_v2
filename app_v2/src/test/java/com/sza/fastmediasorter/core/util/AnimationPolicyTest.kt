package com.sza.fastmediasorter.core.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2536: the policy matrix is the whole contract every animation site in both modules reads, and it
 * is the only part of this ticket that is pure logic - once the sites consult it, the same
 * assertions need a device.
 */
class AnimationPolicyTest {

    @After
    fun reset() {
        AnimationPolicy.update(PowerPolicyLevel.NORMAL)
    }

    @Test
    fun `cold start allows everything`() {
        assertEquals(PowerPolicyLevel.NORMAL, AnimationPolicy.level)
        assertTrue(AnimationPolicy.mayAnimate(AnimationIntent.DECORATIVE))
        assertTrue(AnimationPolicy.mayAnimate(AnimationIntent.AMBIENT))
        assertTrue(AnimationPolicy.mayAnimate(AnimationIntent.FUNCTIONAL))
    }

    @Test
    fun `reduced stops ornament and keeps the rest`() {
        AnimationPolicy.update(PowerPolicyLevel.REDUCED)

        assertFalse(AnimationPolicy.mayAnimate(AnimationIntent.DECORATIVE))
        assertTrue(AnimationPolicy.mayAnimate(AnimationIntent.AMBIENT))
        assertTrue(AnimationPolicy.mayAnimate(AnimationIntent.FUNCTIONAL))
    }

    @Test
    fun `saving leaves only bounded state feedback`() {
        AnimationPolicy.update(PowerPolicyLevel.SAVING)

        assertFalse(AnimationPolicy.mayAnimate(AnimationIntent.DECORATIVE))
        assertFalse(AnimationPolicy.mayAnimate(AnimationIntent.AMBIENT))
        assertTrue(AnimationPolicy.mayAnimate(AnimationIntent.FUNCTIONAL))
    }

    @Test
    fun `the legacy entry point stays the decorative question at every level`() {
        for (level in PowerPolicyLevel.entries) {
            AnimationPolicy.update(level)

            assertEquals(
                "isAnimationAllowed diverged from mayAnimate(DECORATIVE) at $level",
                AnimationPolicy.mayAnimate(AnimationIntent.DECORATIVE),
                AnimationPolicy.isAnimationAllowed
            )
        }
    }
}
