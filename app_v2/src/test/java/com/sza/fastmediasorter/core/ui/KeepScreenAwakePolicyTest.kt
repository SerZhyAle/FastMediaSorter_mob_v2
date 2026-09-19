package com.sza.fastmediasorter.core.ui

import com.sza.fastmediasorter.core.util.PowerPolicyLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S3285: the prevent-sleep decision every screen of the app now shares.
 *
 * The rule the tests below pin: the user setting decides, and the saving level overrules it (S2536).
 */
class KeepScreenAwakePolicyTest {

    @Test
    fun `the setting holds the screen at every level but saving`() {
        assertTrue(KeepScreenAwakePolicy.shouldKeepScreenAwake(true, PowerPolicyLevel.NORMAL))
        assertTrue(KeepScreenAwakePolicy.shouldKeepScreenAwake(true, PowerPolicyLevel.REDUCED))
    }

    @Test
    fun `the saving level stands the hold down even with the setting on`() {
        assertFalse(KeepScreenAwakePolicy.shouldKeepScreenAwake(true, PowerPolicyLevel.SAVING))
    }

    @Test
    fun `the setting off never holds the screen, whatever the level`() {
        for (level in PowerPolicyLevel.entries) {
            assertFalse(
                "preventSleep is off, so $level must not hold the screen",
                KeepScreenAwakePolicy.shouldKeepScreenAwake(false, level),
            )
        }
    }
}
