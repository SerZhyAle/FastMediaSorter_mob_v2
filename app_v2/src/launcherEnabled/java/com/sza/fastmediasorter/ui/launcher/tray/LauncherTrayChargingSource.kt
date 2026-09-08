package com.sza.fastmediasorter.ui.launcher.tray

import android.os.BatteryManager
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R

/**
 * S2738: how the device is being charged, as the tray paints it. The plug type comes from
 * [BatteryManager.EXTRA_PLUGGED] on the battery broadcast the tray already receives, so no new
 * subscription, permission or poll appears.
 *
 * [UNKNOWN] exists because a device may report a plug value outside the three documented constants
 * (strategic §7): a charging device then keeps a charging colour instead of falling back to the
 * discharged look, which would state the opposite of what is happening.
 */
enum class LauncherTrayChargingSource(
    @param:ColorRes val colorRes: Int?,
    @param:StringRes val descriptionRes: Int,
) {
    NONE(null, R.string.launcher_tray_battery_level),
    AC(R.color.launcher_tray_battery_ac, R.string.launcher_tray_battery_charging_ac),
    USB(R.color.launcher_tray_battery_usb, R.string.launcher_tray_battery_charging_usb),
    WIRELESS(R.color.launcher_tray_battery_wireless, R.string.launcher_tray_battery_charging_wireless),
    UNKNOWN(R.color.launcher_tray_battery_charging, R.string.launcher_tray_battery_charging),
    ;

    val isCharging: Boolean get() = this != NONE

    companion object {
        fun from(plugged: Int, charging: Boolean): LauncherTrayChargingSource {
            if (!charging) return NONE
            return when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> AC
                BatteryManager.BATTERY_PLUGGED_USB -> USB
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> WIRELESS
                else -> UNKNOWN
            }
        }
    }
}
