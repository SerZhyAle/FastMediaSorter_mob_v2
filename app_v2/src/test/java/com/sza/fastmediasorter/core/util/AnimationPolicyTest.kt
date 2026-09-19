package com.sza.fastmediasorter.core.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2536 / S3276: tests for AnimationPolicy level, reason, and listener behaviour.
 */
class AnimationPolicyTest {

    @After
    fun reset() {
        AnimationPolicy.update(PowerPolicyDecision(PowerPolicyLevel.NORMAL, PowerPolicyReason.NONE))
    }

    @Test
    fun `cold start allows everything`() {
        assertEquals(PowerPolicyLevel.NORMAL, AnimationPolicy.level)
        assertEquals(PowerPolicyReason.NONE, AnimationPolicy.reason)
        assertTrue(AnimationPolicy.mayAnimate(AnimationIntent.DECORATIVE))
        assertTrue(AnimationPolicy.mayAnimate(AnimationIntent.AMBIENT))
        assertTrue(AnimationPolicy.mayAnimate(AnimationIntent.FUNCTIONAL))
    }

    @Test
    fun `reduced stops ornament and keeps the rest`() {
        AnimationPolicy.update(PowerPolicyDecision(PowerPolicyLevel.REDUCED, PowerPolicyReason.ANIMATION_SWITCH))

        assertFalse(AnimationPolicy.mayAnimate(AnimationIntent.DECORATIVE))
        assertTrue(AnimationPolicy.mayAnimate(AnimationIntent.AMBIENT))
        assertTrue(AnimationPolicy.mayAnimate(AnimationIntent.FUNCTIONAL))
    }

    @Test
    fun `saving leaves only bounded state feedback`() {
        AnimationPolicy.update(PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.LOW_BATTERY))

        assertFalse(AnimationPolicy.mayAnimate(AnimationIntent.DECORATIVE))
        assertFalse(AnimationPolicy.mayAnimate(AnimationIntent.AMBIENT))
        assertTrue(AnimationPolicy.mayAnimate(AnimationIntent.FUNCTIONAL))
    }

    @Test
    fun `a registered listener hears every level change`() {
        var fired = 0
        val listener: () -> Unit = { fired++ }
        AnimationPolicy.addLevelListener(listener)

        try {
            AnimationPolicy.update(PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.LOW_BATTERY))
            AnimationPolicy.update(PowerPolicyDecision(PowerPolicyLevel.REDUCED, PowerPolicyReason.ANIMATION_SWITCH))
        } finally {
            AnimationPolicy.removeLevelListener(listener)
        }

        assertEquals(2, fired)
    }

    @Test
    fun `a registered listener fires on a reason-only change when level stays the same`() {
        AnimationPolicy.update(PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.LOW_BATTERY))
        var fired = 0
        val listener: () -> Unit = { fired++ }
        AnimationPolicy.addLevelListener(listener)

        try {
            AnimationPolicy.update(PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.SYSTEM_SAVER))
        } finally {
            AnimationPolicy.removeLevelListener(listener)
        }

        assertEquals(1, fired)
        assertEquals(PowerPolicyReason.SYSTEM_SAVER, AnimationPolicy.reason)
    }

    @Test
    fun `mayAnimate is unaffected by the reason`() {
        val lowBattery = PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.LOW_BATTERY)
        val systemSaver = PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.SYSTEM_SAVER)

        AnimationPolicy.update(lowBattery)
        val lowBatteryDecorative = AnimationPolicy.mayAnimate(AnimationIntent.DECORATIVE)
        val lowBatteryAmbient = AnimationPolicy.mayAnimate(AnimationIntent.AMBIENT)
        val lowBatteryFunctional = AnimationPolicy.mayAnimate(AnimationIntent.FUNCTIONAL)

        AnimationPolicy.update(systemSaver)
        assertEquals(lowBatteryDecorative, AnimationPolicy.mayAnimate(AnimationIntent.DECORATIVE))
        assertEquals(lowBatteryAmbient, AnimationPolicy.mayAnimate(AnimationIntent.AMBIENT))
        assertEquals(lowBatteryFunctional, AnimationPolicy.mayAnimate(AnimationIntent.FUNCTIONAL))
    }

    @Test
    fun `a removed listener hears nothing`() {
        var fired = 0
        val listener: () -> Unit = { fired++ }
        AnimationPolicy.addLevelListener(listener)
        AnimationPolicy.removeLevelListener(listener)

        AnimationPolicy.update(PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.LOW_BATTERY))

        assertEquals(0, fired)
    }

    @Test
    fun `re-stating the decision in force fires nothing`() {
        val decision = PowerPolicyDecision(PowerPolicyLevel.REDUCED, PowerPolicyReason.ANIMATION_SWITCH)
        AnimationPolicy.update(decision)
        var fired = 0
        val listener: () -> Unit = { fired++ }
        AnimationPolicy.addLevelListener(listener)

        try {
            AnimationPolicy.update(decision)
        } finally {
            AnimationPolicy.removeLevelListener(listener)
        }

        assertEquals(0, fired)
    }

    @Test
    fun `the legacy entry point stays the decorative question at every level`() {
        val decisions = listOf(
            PowerPolicyDecision(PowerPolicyLevel.NORMAL, PowerPolicyReason.NONE),
            PowerPolicyDecision(PowerPolicyLevel.REDUCED, PowerPolicyReason.ANIMATION_SWITCH),
            PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.LOW_BATTERY)
        )
        for (decision in decisions) {
            AnimationPolicy.update(decision)

            assertEquals(
                "isAnimationAllowed diverged from mayAnimate(DECORATIVE) at ${decision.level}",
                AnimationPolicy.mayAnimate(AnimationIntent.DECORATIVE),
                AnimationPolicy.isAnimationAllowed
            )
        }
    }
}
