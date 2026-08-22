package com.sza.fastmediasorter.wear.domain.netmonitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The section keys are the phone's keys: a saved external address must point at the same section on
 * both devices, so a rename on one side is a defect the test names rather than a free choice.
 */
class WearNetworkSectionTest {

    @Test
    fun `a watch with every radio gets all seven sections in declaration order`() {
        val sections = sectionsFor(
            WearNetworkCapabilities(
                hasWifi = true,
                hasMobile = true,
                hasBluetooth = true,
                hasLocation = true
            )
        )

        assertEquals(WearNetworkSection.entries, sections)
    }

    @Test
    fun `a wifi only watch gets summary wifi internet and history`() {
        val sections = sectionsFor(
            WearNetworkCapabilities(
                hasWifi = true,
                hasMobile = false,
                hasBluetooth = false,
                hasLocation = false
            )
        )

        assertEquals(
            listOf(
                WearNetworkSection.Summary,
                WearNetworkSection.Wifi,
                WearNetworkSection.Internet,
                WearNetworkSection.History
            ),
            sections
        )
    }

    @Test
    fun `no capability combination produces an empty list`() {
        allCapabilityCombinations().forEach { capabilities ->
            val sections = sectionsFor(capabilities)
            assertTrue("empty for $capabilities", sections.isNotEmpty())
            assertTrue("no summary for $capabilities", sections.contains(WearNetworkSection.Summary))
        }
    }

    @Test
    fun `every section key equals the phone key for the same section`() {
        val expected = mapOf(
            WearNetworkSection.Summary to "summary",
            WearNetworkSection.Wifi to "wifi",
            WearNetworkSection.Mobile to "mobile",
            WearNetworkSection.Bluetooth to "bluetooth",
            WearNetworkSection.Gnss to "gnss",
            WearNetworkSection.Internet to "internet",
            WearNetworkSection.History to "history"
        )

        WearNetworkSection.entries.forEach { section ->
            assertEquals(expected[section], section.key)
        }
    }

    private fun allCapabilityCombinations(): List<WearNetworkCapabilities> {
        val flags = listOf(false, true)
        return flags.flatMap { wifi ->
            flags.flatMap { mobile ->
                flags.flatMap { bluetooth ->
                    flags.map { location ->
                        WearNetworkCapabilities(wifi, mobile, bluetooth, location)
                    }
                }
            }
        }
    }
}
