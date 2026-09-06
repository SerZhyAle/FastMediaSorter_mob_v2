package com.sza.fastmediasorter.core.network

import com.sza.fastmediasorter.domain.model.network.HotspotState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SoftApInterfaceProbeTest {

    private lateinit var probe: SoftApInterfaceProbe

    @Before
    fun setUp() {
        probe = SoftApInterfaceProbe()
    }

    @Test
    fun `swlan0 present but down returns DISABLED`() {
        val interfaces = listOf(
            NetworkInterfaceDescriptor("swlan0", isUp = false),
            NetworkInterfaceDescriptor("wlan0", isUp = true)
        )
        val result = probe.evaluateInterfaces(interfaces)
        assertEquals(HotspotState.DISABLED, result)
    }

    @Test
    fun `swlan0 present and up returns ENABLED`() {
        val interfaces = listOf(
            NetworkInterfaceDescriptor("swlan0", isUp = true),
            NetworkInterfaceDescriptor("wlan0", isUp = true)
        )
        val result = probe.evaluateInterfaces(interfaces)
        assertEquals(HotspotState.ENABLED, result)
    }

    @Test
    fun `p2p0 up with swlan0 down returns DISABLED`() {
        val interfaces = listOf(
            NetworkInterfaceDescriptor("swlan0", isUp = false),
            NetworkInterfaceDescriptor("p2p0", isUp = true),
            NetworkInterfaceDescriptor("wlan0", isUp = true)
        )
        val result = probe.evaluateInterfaces(interfaces)
        assertEquals(HotspotState.DISABLED, result)
    }

    @Test
    fun `null interface list returns UNKNOWN and empty list returns DISABLED`() {
        val nullResult = probe.evaluateInterfaces(null)
        assertEquals(HotspotState.UNKNOWN, nullResult)

        val emptyResult = probe.evaluateInterfaces(emptyList())
        assertEquals(HotspotState.DISABLED, emptyResult)
    }
}
