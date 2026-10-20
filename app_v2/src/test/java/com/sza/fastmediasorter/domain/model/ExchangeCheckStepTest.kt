package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeCheckStepTest {

    @Test
    fun `the ladder has the fifteen steps of item CY in the contract order`() {
        assertEquals(
            listOf(
                "pc-asleep", "address-lan", "guest-network", "vpn", "windows-public-network",
                "ip-family", "server-internet", "port-forward", "device-clock", "certificate",
                "credentials", "sign-in-rate", "invitation", "device-removed", "operator",
            ),
            ExchangeCheckStep.entries.map { it.id },
        )
    }

    @Test
    fun `only the network and internet steps are restricted to a route`() {
        val network = ExchangeCheckStep.entries.filter { it.applies == ExchangeCheckStep.Applies.NETWORK }
        val internet = ExchangeCheckStep.entries.filter { it.applies == ExchangeCheckStep.Applies.INTERNET }
        assertEquals(
            listOf(
                ExchangeCheckStep.ADDRESS_LAN,
                ExchangeCheckStep.GUEST_NETWORK,
                ExchangeCheckStep.WINDOWS_PUBLIC_NETWORK,
            ),
            network,
        )
        assertEquals(listOf(ExchangeCheckStep.SERVER_INTERNET, ExchangeCheckStep.PORT_FORWARD), internet)
    }

    @Test
    fun `the windows step is the only one tied to a windows target`() {
        assertEquals(
            listOf(ExchangeCheckStep.WINDOWS_PUBLIC_NETWORK),
            ExchangeCheckStep.entries.filter { it.windowsOnly },
        )
    }

    @Test
    fun `of round-trips every id and rejects an unknown one`() {
        ExchangeCheckStep.entries.forEach { assertEquals(it, ExchangeCheckStep.of(it.id)) }
        assertNull(ExchangeCheckStep.of("no-such-step"))
        assertTrue(ExchangeCheckStep.entries.map { it.id }.toSet().size == ExchangeCheckStep.entries.size)
    }
}
