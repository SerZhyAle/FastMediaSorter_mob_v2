package com.sza.fastmediasorter.wear.ui.common

import com.sza.fastmediasorter.wear.data.power.resolveWearPowerPolicyLevel
import com.sza.fastmediasorter.wear.domain.model.PowerSavingTrigger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2536: the watch module carries no Compose UI instrumentation at all, so anything not asserted as
 * pure logic here can only be checked by hand on a real watch.
 *
 * Covers both halves: the policy matrix the draw sites read, and the verdict that feeds it.
 */
class WearPowerPolicyTest {

    @After
    fun reset() {
        WearPowerPolicy.update(PowerPolicyLevel.NORMAL)
    }

    @Test
    fun `cold start allows everything`() {
        assertEquals(PowerPolicyLevel.NORMAL, WearPowerPolicy.level)
        assertTrue(WearPowerPolicy.mayAnimate(AnimationIntent.DECORATIVE))
        assertTrue(WearPowerPolicy.mayAnimate(AnimationIntent.AMBIENT))
        assertTrue(WearPowerPolicy.mayAnimate(AnimationIntent.FUNCTIONAL))
    }

    @Test
    fun `reduced stops ornament and keeps the rest`() {
        WearPowerPolicy.update(PowerPolicyLevel.REDUCED)

        assertFalse(WearPowerPolicy.mayAnimate(AnimationIntent.DECORATIVE))
        assertTrue(WearPowerPolicy.mayAnimate(AnimationIntent.AMBIENT))
        assertTrue(WearPowerPolicy.mayAnimate(AnimationIntent.FUNCTIONAL))
    }

    @Test
    fun `saving leaves only bounded state feedback`() {
        WearPowerPolicy.update(PowerPolicyLevel.SAVING)

        assertFalse(WearPowerPolicy.mayAnimate(AnimationIntent.DECORATIVE))
        assertFalse(WearPowerPolicy.mayAnimate(AnimationIntent.AMBIENT))
        assertTrue(WearPowerPolicy.mayAnimate(AnimationIntent.FUNCTIONAL))
    }

    private fun levelFor(
        trigger: PowerSavingTrigger,
        chargePercent: Int? = 100,
        osPowerSaveMode: Boolean = false,
        animationsDisabled: Boolean = false
    ): PowerPolicyLevel = resolveWearPowerPolicyLevel(
        trigger = trigger,
        chargePercent = chargePercent,
        osPowerSaveMode = osPowerSaveMode,
        animationsDisabled = animationsDisabled
    )

    @Test
    fun `every threshold trigger fires at its own percentage and not one above it`() {
        val thresholdTriggers = PowerSavingTrigger.entries.filter { it.thresholdPercent != null }

        assertEquals(4, thresholdTriggers.size)
        for (trigger in thresholdTriggers) {
            val threshold = requireNotNull(trigger.thresholdPercent)

            assertEquals(
                "$trigger should be SAVING at its own threshold of $threshold",
                PowerPolicyLevel.SAVING,
                levelFor(trigger, chargePercent = threshold)
            )
            assertEquals(
                "$trigger should be NORMAL one percent above $threshold",
                PowerPolicyLevel.NORMAL,
                levelFor(trigger, chargePercent = threshold + 1)
            )
        }
    }

    @Test
    fun `ALWAYS saves at any charge and OFF never saves on charge alone`() {
        assertEquals(PowerPolicyLevel.SAVING, levelFor(PowerSavingTrigger.ALWAYS, chargePercent = 100))
        assertEquals(PowerPolicyLevel.NORMAL, levelFor(PowerSavingTrigger.OFF, chargePercent = 1))
    }

    @Test
    fun `the OS saver raises SAVING whatever the trigger and the charge`() {
        for (trigger in PowerSavingTrigger.entries) {
            assertEquals(
                "the OS power saver should win over $trigger at a full charge",
                PowerPolicyLevel.SAVING,
                levelFor(trigger, chargePercent = 100, osPowerSaveMode = true)
            )
        }
    }

    @Test
    fun `the animation switch produces REDUCED and never SAVING`() {
        assertEquals(
            PowerPolicyLevel.REDUCED,
            levelFor(PowerSavingTrigger.OFF, chargePercent = 100, animationsDisabled = true)
        )
    }

    @Test
    fun `an unreadable charge leaves a threshold trigger where it would be without the reading`() {
        // A watch that does not report its charge must not be treated as flat - guessing that way
        // would freeze the app permanently on exactly the devices that report the least.
        assertEquals(
            PowerPolicyLevel.NORMAL,
            levelFor(PowerSavingTrigger.BELOW_30, chargePercent = null)
        )
        assertEquals(
            PowerPolicyLevel.SAVING,
            levelFor(PowerSavingTrigger.ALWAYS, chargePercent = null)
        )
        assertEquals(
            PowerPolicyLevel.SAVING,
            levelFor(PowerSavingTrigger.BELOW_30, chargePercent = null, osPowerSaveMode = true)
        )
    }

    @Test
    fun `the watch trigger mirrors the phone's entries exactly`() {
        // The two enums are maintained by hand in two modules, as the wear settings registry already
        // is. A divergence here would desync the settings channel silently, since only the NAME is
        // sent and an unknown name resolves to the default rather than failing.
        assertEquals(
            listOf("OFF", "ALWAYS", "BELOW_10", "BELOW_15", "BELOW_20", "BELOW_30"),
            PowerSavingTrigger.entries.map { it.name }
        )
        assertEquals(PowerSavingTrigger.BELOW_20, PowerSavingTrigger.DEFAULT)
        assertEquals(PowerSavingTrigger.BELOW_20, PowerSavingTrigger.fromNameOrDefault("nonsense"))
    }
}
