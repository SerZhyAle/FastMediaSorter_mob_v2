package com.sza.fastmediasorter.ui.launcher.tray

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S2738: the plug-type mapping the tray colours depend on. */
class LauncherTrayChargingSourceTest {

    @Test
    fun `not charging maps to NONE regardless of plug value`() {
        val source = LauncherTrayChargingSource.from(BatteryManager.BATTERY_PLUGGED_AC, charging = false)

        assertEquals(LauncherTrayChargingSource.NONE, source)
        assertFalse(source.isCharging)
        assertNull(source.colorRes)
    }

    @Test
    fun `documented plug values map to their own source`() {
        assertEquals(
            LauncherTrayChargingSource.AC,
            LauncherTrayChargingSource.from(BatteryManager.BATTERY_PLUGGED_AC, charging = true),
        )
        assertEquals(
            LauncherTrayChargingSource.USB,
            LauncherTrayChargingSource.from(BatteryManager.BATTERY_PLUGGED_USB, charging = true),
        )
        assertEquals(
            LauncherTrayChargingSource.WIRELESS,
            LauncherTrayChargingSource.from(BatteryManager.BATTERY_PLUGGED_WIRELESS, charging = true),
        )
    }

    @Test
    fun `charging under an unreported plug value keeps a charging colour`() {
        val source = LauncherTrayChargingSource.from(plugged = 0, charging = true)

        assertEquals(LauncherTrayChargingSource.UNKNOWN, source)
        assertTrue(source.isCharging)
    }
}
