package com.sza.fastmediasorter.core.power

import com.sza.fastmediasorter.core.util.PowerPolicyLevel
import com.sza.fastmediasorter.domain.model.PowerSavingTrigger
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2536: the level verdict is entirely new behaviour - the module consulted no battery signal at all
 * before this ticket - so there is no prior behaviour to fall back on if an arm is wrong.
 *
 * The unreadable-charge arm in particular cannot be staged on a device on demand, which is why the
 * decision was extracted as a pure function rather than left inside the receiver.
 */
class PowerStateObserverTest {

    private fun levelFor(
        trigger: PowerSavingTrigger,
        chargePercent: Int? = 100,
        osPowerSaveMode: Boolean = false,
        animationsDisabled: Boolean = false
    ): PowerPolicyLevel = resolvePowerPolicyLevel(
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
                "$trigger should be SAVING below $threshold",
                PowerPolicyLevel.SAVING,
                levelFor(trigger, chargePercent = threshold - 1)
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
        assertEquals(PowerPolicyLevel.SAVING, levelFor(PowerSavingTrigger.ALWAYS, chargePercent = 1))
        assertEquals(PowerPolicyLevel.NORMAL, levelFor(PowerSavingTrigger.OFF, chargePercent = 1))
        assertEquals(PowerPolicyLevel.NORMAL, levelFor(PowerSavingTrigger.OFF, chargePercent = 0))
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
        assertEquals(
            PowerPolicyLevel.REDUCED,
            levelFor(PowerSavingTrigger.BELOW_20, chargePercent = 100, animationsDisabled = true)
        )
    }

    @Test
    fun `saving outranks the animation switch when both apply`() {
        assertEquals(
            PowerPolicyLevel.SAVING,
            levelFor(PowerSavingTrigger.BELOW_20, chargePercent = 5, animationsDisabled = true)
        )
    }

    @Test
    fun `an unreadable charge leaves a threshold trigger where it would be without the reading`() {
        // The dangerous default is the other one: assuming a flat battery would freeze the app on any
        // device that simply does not report a charge.
        assertEquals(
            PowerPolicyLevel.NORMAL,
            levelFor(PowerSavingTrigger.BELOW_30, chargePercent = null)
        )
        assertEquals(
            PowerPolicyLevel.REDUCED,
            levelFor(PowerSavingTrigger.BELOW_30, chargePercent = null, animationsDisabled = true)
        )
        // The two arms that do not consult the charge keep working without it.
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
    fun `an unknown stored name resolves to the default rather than throwing`() {
        assertEquals(PowerSavingTrigger.BELOW_20, PowerSavingTrigger.fromNameOrDefault(null))
        assertEquals(PowerSavingTrigger.BELOW_20, PowerSavingTrigger.fromNameOrDefault("BELOW_42"))
        assertEquals(PowerSavingTrigger.ALWAYS, PowerSavingTrigger.fromNameOrDefault("ALWAYS"))
    }
}
