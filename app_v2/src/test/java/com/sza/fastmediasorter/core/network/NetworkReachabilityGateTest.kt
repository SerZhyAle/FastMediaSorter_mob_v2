package com.sza.fastmediasorter.core.network

import com.sza.fastmediasorter.data.network.exceptions.NetworkConnectionLostException
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertThrows
import org.junit.Test

class NetworkReachabilityGateTest {

    private val analyzer = mockk<NetworkContextAnalyzer>()
    private val gate = NetworkReachabilityGate(analyzer)

    @Test
    fun `requireAnyNetwork passes when any transport is active`() {
        every { analyzer.hasAnyNetwork() } returns true
        gate.requireAnyNetwork("TEST")
    }

    @Test
    fun `requireAnyNetwork throws when no transport is active`() {
        every { analyzer.hasAnyNetwork() } returns false
        assertThrows(NetworkConnectionLostException::class.java) {
            gate.requireAnyNetwork("TEST")
        }
    }

    @Test
    fun `requireWifi passes when Wi-Fi transport is active`() {
        every { analyzer.hasAnyNetwork() } returns true
        every { analyzer.hasWifi() } returns true
        gate.requireWifi("TEST")
    }

    @Test
    fun `requireWifi throws when only cellular transport is active`() {
        every { analyzer.hasAnyNetwork() } returns true
        every { analyzer.hasWifi() } returns false
        assertThrows(NetworkConnectionLostException::class.java) {
            gate.requireWifi("TEST")
        }
    }

    @Test
    fun `requireWifi throws when no transport is active - delegates to requireAnyNetwork`() {
        every { analyzer.hasAnyNetwork() } returns false
        assertThrows(NetworkConnectionLostException::class.java) {
            gate.requireWifi("TEST")
        }
    }
}
