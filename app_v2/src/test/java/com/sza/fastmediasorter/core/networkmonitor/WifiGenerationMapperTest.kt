package com.sza.fastmediasorter.core.networkmonitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WifiGenerationMapperTest {

    @Test
    fun generationOf_maps_wifi4() {
        assertEquals(4, WifiGenerationMapper.generationOf(WifiGenerationMapper.WIFI_STANDARD_11N))
    }

    @Test
    fun generationOf_maps_wifi5() {
        assertEquals(5, WifiGenerationMapper.generationOf(WifiGenerationMapper.WIFI_STANDARD_11AC))
    }

    @Test
    fun generationOf_maps_wifi6() {
        assertEquals(6, WifiGenerationMapper.generationOf(WifiGenerationMapper.WIFI_STANDARD_11AX))
    }

    @Test
    fun generationOf_maps_wifi7() {
        assertEquals(7, WifiGenerationMapper.generationOf(WifiGenerationMapper.WIFI_STANDARD_11BE))
    }

    @Test
    fun generationOf_returns_null_for_legacy_and_unknown() {
        assertNull(WifiGenerationMapper.generationOf(WifiGenerationMapper.WIFI_STANDARD_LEGACY))
        assertNull(WifiGenerationMapper.generationOf(0))
        assertNull(WifiGenerationMapper.generationOf(99))
    }

    @Test
    fun displayName_and_generationOf_agree_on_nullability() {
        val testValues = listOf(
            WifiGenerationMapper.WIFI_STANDARD_LEGACY,
            WifiGenerationMapper.WIFI_STANDARD_11N,
            WifiGenerationMapper.WIFI_STANDARD_11AC,
            WifiGenerationMapper.WIFI_STANDARD_11AX,
            WifiGenerationMapper.WIFI_STANDARD_11BE,
            0,
            99
        )
        for (value in testValues) {
            val name = WifiGenerationMapper.displayName(value)
            val gen = WifiGenerationMapper.generationOf(value)
            if (value == WifiGenerationMapper.WIFI_STANDARD_LEGACY) {
                assertEquals("802.11 a/b/g", name)
                assertNull(gen)
            } else if (gen != null) {
                assertEquals(name != null, true)
            } else {
                assertEquals(name == null || value == WifiGenerationMapper.WIFI_STANDARD_LEGACY, true)
            }
        }
    }
}
