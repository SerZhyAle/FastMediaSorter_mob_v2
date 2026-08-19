package com.sza.fastmediasorter.widget.networkmonitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1440: the indicator key is persisted per widget instance and per desktop cell, so a duplicated or
 * silently renamed key retargets a tile the user already placed - these pin the catalogue's shape.
 */
class NetworkMonitorIndicatorTest {

    @Test
    fun `the catalogue holds exactly the eight indicators the spec names`() {
        assertEquals(EXPECTED_COUNT, NetworkMonitorIndicator.entries.size)
    }

    @Test
    fun `every key is unique`() {
        val keys = NetworkMonitorIndicator.entries.map { it.key }

        assertEquals("a duplicated key retargets an already placed tile", keys.size, keys.toSet().size)
    }

    @Test
    fun `every key is an ascii lowercase identifier`() {
        val shape = Regex("^[a-z][a-z0-9_]*$")

        NetworkMonitorIndicator.entries.forEach { indicator ->
            assertTrue("key '${indicator.key}' is not a persisted-safe identifier", shape.matches(indicator.key))
        }
    }

    @Test
    fun `fromKey round-trips every constant and falls back for anything else`() {
        NetworkMonitorIndicator.entries.forEach { indicator ->
            assertEquals(indicator, NetworkMonitorIndicator.fromKey(indicator.key))
        }

        assertEquals(NetworkMonitorIndicator.LOCAL_ADDRESS, NetworkMonitorIndicator.fromKey("nonexistent"))
        assertEquals(NetworkMonitorIndicator.LOCAL_ADDRESS, NetworkMonitorIndicator.fromKey(null))
    }

    private companion object {
        const val EXPECTED_COUNT = 8
    }
}
