package com.sza.fastmediasorter.core.power

import com.sza.fastmediasorter.core.util.PowerPolicyDecision
import com.sza.fastmediasorter.core.util.PowerPolicyLevel
import com.sza.fastmediasorter.core.util.PowerPolicyReason
import com.sza.fastmediasorter.domain.model.PowerSavingTrigger
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2536 / S3276: the level and reason verdict test.
 */
class PowerStateObserverTest {

    private fun decisionFor(
        trigger: PowerSavingTrigger,
        chargePercent: Int? = 100,
        osPowerSaveMode: Boolean = false,
        animationsDisabled: Boolean = false,
        charging: Boolean = false
    ): PowerPolicyDecision = resolvePowerPolicyDecision(
        trigger = trigger,
        chargePercent = chargePercent,
        osPowerSaveMode = osPowerSaveMode,
        animationsDisabled = animationsDisabled,
        charging = charging
    )

    @Test
    fun `every threshold trigger fires at its own percentage and not one above it`() {
        val thresholdTriggers = PowerSavingTrigger.entries.filter { it.thresholdPercent != null }

        assertEquals(4, thresholdTriggers.size)
        for (trigger in thresholdTriggers) {
            val threshold = requireNotNull(trigger.thresholdPercent)

            assertEquals(
                "$trigger should be SAVING at its own threshold of $threshold",
                PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.LOW_BATTERY),
                decisionFor(trigger, chargePercent = threshold)
            )
            assertEquals(
                "$trigger should be SAVING below $threshold",
                PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.LOW_BATTERY),
                decisionFor(trigger, chargePercent = threshold - 1)
            )
            assertEquals(
                "$trigger should be NORMAL one percent above $threshold",
                PowerPolicyDecision(PowerPolicyLevel.NORMAL, PowerPolicyReason.NONE),
                decisionFor(trigger, chargePercent = threshold + 1)
            )
        }
    }

    @Test
    fun `ALWAYS saves at any charge and OFF never saves on charge alone`() {
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.USER_ALWAYS),
            decisionFor(PowerSavingTrigger.ALWAYS, chargePercent = 100)
        )
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.USER_ALWAYS),
            decisionFor(PowerSavingTrigger.ALWAYS, chargePercent = 1)
        )
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.NORMAL, PowerPolicyReason.NONE),
            decisionFor(PowerSavingTrigger.OFF, chargePercent = 1)
        )
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.NORMAL, PowerPolicyReason.NONE),
            decisionFor(PowerSavingTrigger.OFF, chargePercent = 0)
        )
    }

    @Test
    fun `the OS saver raises SAVING whatever the trigger and the charge`() {
        for (trigger in PowerSavingTrigger.entries) {
            val expectedReason = if (trigger == PowerSavingTrigger.ALWAYS) {
                PowerPolicyReason.USER_ALWAYS
            } else {
                PowerPolicyReason.SYSTEM_SAVER
            }
            assertEquals(
                "the OS power saver should win over $trigger at a full charge",
                PowerPolicyDecision(PowerPolicyLevel.SAVING, expectedReason),
                decisionFor(trigger, chargePercent = 100, osPowerSaveMode = true)
            )
        }
    }

    @Test
    fun `the animation switch produces REDUCED and never SAVING`() {
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.REDUCED, PowerPolicyReason.ANIMATION_SWITCH),
            decisionFor(PowerSavingTrigger.OFF, chargePercent = 100, animationsDisabled = true)
        )
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.REDUCED, PowerPolicyReason.ANIMATION_SWITCH),
            decisionFor(PowerSavingTrigger.BELOW_20, chargePercent = 100, animationsDisabled = true)
        )
    }

    @Test
    fun `saving outranks the animation switch when both apply`() {
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.LOW_BATTERY),
            decisionFor(PowerSavingTrigger.BELOW_20, chargePercent = 5, animationsDisabled = true)
        )
    }

    @Test
    fun `an unreadable charge leaves a threshold trigger where it would be without the reading`() {
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.NORMAL, PowerPolicyReason.NONE),
            decisionFor(PowerSavingTrigger.BELOW_30, chargePercent = null)
        )
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.REDUCED, PowerPolicyReason.ANIMATION_SWITCH),
            decisionFor(PowerSavingTrigger.BELOW_30, chargePercent = null, animationsDisabled = true)
        )
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.USER_ALWAYS),
            decisionFor(PowerSavingTrigger.ALWAYS, chargePercent = null)
        )
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.SYSTEM_SAVER),
            decisionFor(PowerSavingTrigger.BELOW_30, chargePercent = null, osPowerSaveMode = true)
        )
    }

    @Test
    fun `charging suppresses low battery threshold arm but not system or user always`() {
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.NORMAL, PowerPolicyReason.NONE),
            decisionFor(PowerSavingTrigger.BELOW_20, chargePercent = 5, charging = true)
        )
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.REDUCED, PowerPolicyReason.ANIMATION_SWITCH),
            decisionFor(PowerSavingTrigger.BELOW_20, chargePercent = 5, animationsDisabled = true, charging = true)
        )
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.SYSTEM_SAVER),
            decisionFor(PowerSavingTrigger.BELOW_20, chargePercent = 5, osPowerSaveMode = true, charging = true)
        )
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.USER_ALWAYS),
            decisionFor(PowerSavingTrigger.ALWAYS, chargePercent = 100, charging = true)
        )
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.NORMAL, PowerPolicyReason.NONE),
            decisionFor(PowerSavingTrigger.BELOW_30, chargePercent = null, charging = true)
        )
    }

    @Test
    fun `no battery level is claimed only after the platform has answered`() {
        assertEquals(false, resolveBatteryLevelUnavailable(batteryIntentSeen = false, chargePercent = null))
        assertEquals(true, resolveBatteryLevelUnavailable(batteryIntentSeen = true, chargePercent = null))
        assertEquals(false, resolveBatteryLevelUnavailable(batteryIntentSeen = true, chargePercent = 42))
    }

    @Test
    fun `an unknown stored name resolves to the default rather than throwing`() {
        assertEquals(PowerSavingTrigger.BELOW_20, PowerSavingTrigger.fromNameOrDefault(null))
        assertEquals(PowerSavingTrigger.BELOW_20, PowerSavingTrigger.fromNameOrDefault("BELOW_42"))
        assertEquals(PowerSavingTrigger.ALWAYS, PowerSavingTrigger.fromNameOrDefault("ALWAYS"))
    }
}
