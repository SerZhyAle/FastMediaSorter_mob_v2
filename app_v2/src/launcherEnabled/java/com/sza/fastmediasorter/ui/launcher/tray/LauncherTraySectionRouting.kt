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

        LauncherTrayIndicator.SIM1, LauncherTrayIndicator.SIM2 ->
            NetworkMonitorSection.Mobile.key to OsShortcutCatalog.KEY_WIRELESS

        LauncherTrayIndicator.NETWORK -> if (currentTransport == NetworkTransport.WIFI) {
            NetworkMonitorSection.Wifi.key to OsShortcutCatalog.KEY_WIFI
        } else {
            NetworkMonitorSection.Mobile.key to OsShortcutCatalog.KEY_WIRELESS
        }

        else -> "" to ""
    }
}
