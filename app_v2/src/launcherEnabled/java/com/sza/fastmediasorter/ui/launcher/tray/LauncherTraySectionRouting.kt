package com.sza.fastmediasorter.ui.launcher.tray

import com.sza.fastmediasorter.core.panel.OsShortcutCatalog
import com.sza.fastmediasorter.domain.model.devicestatus.NetworkTransport
import com.sza.fastmediasorter.ui.networkmonitor.NetworkMonitorSection

/**
 * S2025: pure indicator-to-section routing for the launcher tray.
 */
object LauncherTraySectionRouting {

    /**
     * Maps [indicator] and the rendered [currentTransport] to a pair of
     * (NetworkMonitorSection key, OsShortcutCatalog key).
     */
    fun routeFor(
        indicator: LauncherTrayIndicator,
        currentTransport: NetworkTransport = NetworkTransport.NONE,
    ): Pair<String, String> = when (indicator) {
        LauncherTrayIndicator.BLUETOOTH ->
            NetworkMonitorSection.Bluetooth.key to OsShortcutCatalog.KEY_BLUETOOTH

        LauncherTrayIndicator.TETHERING ->
            NetworkMonitorSection.Wifi.key to OsShortcutCatalog.KEY_TETHERING

        LauncherTrayIndicator.SIM1, LauncherTrayIndicator.SIM2,
        LauncherTrayIndicator.SPEED_RX, LauncherTrayIndicator.SPEED_TX ->
            NetworkMonitorSection.Mobile.key to OsShortcutCatalog.KEY_WIRELESS

        LauncherTrayIndicator.NETWORK -> if (currentTransport == NetworkTransport.WIFI) {
            NetworkMonitorSection.Wifi.key to OsShortcutCatalog.KEY_WIFI
        } else {
            NetworkMonitorSection.Mobile.key to OsShortcutCatalog.KEY_WIRELESS
        }

        else -> "" to ""
    }

    /**
     * S2027: whether [indicator]'s tap skips the in-app Monitor and opens the system screen from
     * [routeFor]'s second element directly.
     *
     * True only for tethering, and for the reason the Monitor exists at all: every other tray indicator
     * reports something the Monitor itself explains, while the app cannot switch tethering on any API
     * level it ships to (strategic ADR-2), so routing it inward costs the user a second tap to reach the
     * only screen that can act on it.
     */
    fun opensSystemScreenDirectly(indicator: LauncherTrayIndicator): Boolean =
        indicator == LauncherTrayIndicator.TETHERING
}
