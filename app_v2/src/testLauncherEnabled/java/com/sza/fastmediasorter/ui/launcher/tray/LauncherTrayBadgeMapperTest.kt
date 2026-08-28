package com.sza.fastmediasorter.ui.launcher.tray

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherTrayBadgeMapperTest {

    @Test
    fun bluetoothBadge_returns_null_for_null() {
        assertNull(LauncherTrayBadgeMapper.bluetoothBadge(null))
    }

    @Test
    fun bluetoothBadge_returns_null_for_zero() {
        assertNull(LauncherTrayBadgeMapper.bluetoothBadge(0))
    }

    @Test
    fun bluetoothBadge_returns_digit_string_for_one() {
        assertEquals("1", LauncherTrayBadgeMapper.bluetoothBadge(1))
    }

    @Test
    fun bluetoothBadge_returns_string_for_two_digit_count() {
        assertEquals("12", LauncherTrayBadgeMapper.bluetoothBadge(12))
    }

    @Test
    fun dataTypeBadge_returns_null_for_null_or_unknown() {
        assertNull(LauncherTrayBadgeMapper.dataTypeBadge(null))
        assertNull(LauncherTrayBadgeMapper.dataTypeBadge(0))
        assertNull(LauncherTrayBadgeMapper.dataTypeBadge(99))
    }

    @Test
    fun dataTypeBadge_maps_gprs_and_gsm_to_G() {
        assertEquals("G", LauncherTrayBadgeMapper.dataTypeBadge(LauncherTrayBadgeMapper.NETWORK_TYPE_GPRS))
        assertEquals("G", LauncherTrayBadgeMapper.dataTypeBadge(LauncherTrayBadgeMapper.NETWORK_TYPE_GSM))
    }

    @Test
    fun dataTypeBadge_maps_edge_to_E() {
        assertEquals("E", LauncherTrayBadgeMapper.dataTypeBadge(LauncherTrayBadgeMapper.NETWORK_TYPE_EDGE))
    }

    @Test
    fun dataTypeBadge_maps_3g_types_to_3G() {
        assertEquals("3G", LauncherTrayBadgeMapper.dataTypeBadge(LauncherTrayBadgeMapper.NETWORK_TYPE_UMTS))
        assertEquals("3G", LauncherTrayBadgeMapper.dataTypeBadge(LauncherTrayBadgeMapper.NETWORK_TYPE_CDMA))
        assertEquals("3G", LauncherTrayBadgeMapper.dataTypeBadge(LauncherTrayBadgeMapper.NETWORK_TYPE_EVDO_0))
    }

    @Test
    fun dataTypeBadge_maps_hsdpa_to_H() {
        assertEquals("H", LauncherTrayBadgeMapper.dataTypeBadge(LauncherTrayBadgeMapper.NETWORK_TYPE_HSDPA))
        assertEquals("H", LauncherTrayBadgeMapper.dataTypeBadge(LauncherTrayBadgeMapper.NETWORK_TYPE_HSUPA))
        assertEquals("H", LauncherTrayBadgeMapper.dataTypeBadge(LauncherTrayBadgeMapper.NETWORK_TYPE_HSPA))
    }

    @Test
    fun dataTypeBadge_maps_hspap_to_HPlus() {
        assertEquals("H+", LauncherTrayBadgeMapper.dataTypeBadge(LauncherTrayBadgeMapper.NETWORK_TYPE_HSPAP))
    }

    @Test
    fun dataTypeBadge_maps_lte_to_4G() {
        assertEquals("4G", LauncherTrayBadgeMapper.dataTypeBadge(LauncherTrayBadgeMapper.NETWORK_TYPE_LTE))
    }

    @Test
    fun dataTypeBadge_maps_nr_to_5G() {
        assertEquals("5G", LauncherTrayBadgeMapper.dataTypeBadge(LauncherTrayBadgeMapper.NETWORK_TYPE_NR))
    }

    @Test
    fun dataTypeBadge_nrAdvanced_overrides_type_to_5G() {
        assertEquals("5G", LauncherTrayBadgeMapper.dataTypeBadge(LauncherTrayBadgeMapper.NETWORK_TYPE_LTE, nrAdvanced = true))
        assertEquals("5G", LauncherTrayBadgeMapper.dataTypeBadge(null, nrAdvanced = true))
    }
}
