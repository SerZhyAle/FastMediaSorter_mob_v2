package com.sza.fastmediasorter.ui.launcher.tray

import com.sza.fastmediasorter.core.panel.OsShortcutCatalog
import com.sza.fastmediasorter.domain.model.devicestatus.NetworkTransport
import com.sza.fastmediasorter.ui.networkmonitor.NetworkMonitorSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherTraySectionRoutingTest {

    @Test
    fun bluetoothIndicatorRoutesToBluetoothSectionAndOsShortcut() {
        val (sectionKey, osKey) = LauncherTraySectionRouting.routeFor(LauncherTrayIndicator.BLUETOOTH)
        assertEquals(NetworkMonitorSection.Bluetooth.key, sectionKey)
        assertEquals(OsShortcutCatalog.KEY_BLUETOOTH, osKey)
    }

    @Test
    fun sim1IndicatorRoutesToMobileSectionAndWirelessOsShortcut() {
        val (sectionKey, osKey) = LauncherTraySectionRouting.routeFor(LauncherTrayIndicator.SIM1)
        assertEquals(NetworkMonitorSection.Mobile.key, sectionKey)
        assertEquals(OsShortcutCatalog.KEY_WIRELESS, osKey)
    }

    @Test
    fun sim2IndicatorRoutesToMobileSectionAndWirelessOsShortcut() {
        val (sectionKey, osKey) = LauncherTraySectionRouting.routeFor(LauncherTrayIndicator.SIM2)
        assertEquals(NetworkMonitorSection.Mobile.key, sectionKey)
        assertEquals(OsShortcutCatalog.KEY_WIRELESS, osKey)
    }

    @Test
    fun networkIndicatorOnWifiTransportRoutesToWifiSectionAndWifiOsShortcut() {
        val (sectionKey, osKey) = LauncherTraySectionRouting.routeFor(
            LauncherTrayIndicator.NETWORK,
            NetworkTransport.WIFI,
        )
        assertEquals(NetworkMonitorSection.Wifi.key, sectionKey)
        assertEquals(OsShortcutCatalog.KEY_WIFI, osKey)
    }

    @Test
    fun networkIndicatorOnNonWifiTransportRoutesToMobileSectionAndWirelessOsShortcut() {
        val (sectionKey, osKey) = LauncherTraySectionRouting.routeFor(
            LauncherTrayIndicator.NETWORK,
            NetworkTransport.CELLULAR,
        )
        assertEquals(NetworkMonitorSection.Mobile.key, sectionKey)
        assertEquals(OsShortcutCatalog.KEY_WIRELESS, osKey)
    }

    @Test
    fun tetheringIndicatorRoutesToWifiSectionAndTetheringOsShortcut() {
        val (sectionKey, osKey) = LauncherTraySectionRouting.routeFor(LauncherTrayIndicator.TETHERING)
        assertEquals(NetworkMonitorSection.Wifi.key, sectionKey)
        assertEquals(OsShortcutCatalog.KEY_TETHERING, osKey)
    }

    @Test
    fun tetheringIndicatorOpensTheSystemScreenDirectly() {
        assertTrue(LauncherTraySectionRouting.opensSystemScreenDirectly(LauncherTrayIndicator.TETHERING))
    }

    @Test
    fun everyOtherIndicatorKeepsTheInAppMonitorRoute() {
        val inward = LauncherTrayIndicator.entries.filter { it != LauncherTrayIndicator.TETHERING }
        inward.forEach { indicator ->
            assertFalse(
                "S2027 widened the system-screen exception to $indicator",
                LauncherTraySectionRouting.opensSystemScreenDirectly(indicator)
            )
        }
    }
}
