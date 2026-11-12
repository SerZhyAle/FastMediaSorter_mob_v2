package com.sza.fastmediasorter.core.network

import com.sza.fastmediasorter.data.network.exceptions.LocalNetworkPermissionDeniedException
import com.sza.fastmediasorter.data.network.exceptions.NetworkConnectionLostException
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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

    @Test
    fun `localNetworkRefusal names the missing grant for a LAN host`() {
        every { analyzer.hasLocalNetworkPermission() } returns false
        every { analyzer.isLocalNetworkHost("192.168.1.112") } returns true
        assertTrue(gate.localNetworkRefusal("TEST", "192.168.1.112") is LocalNetworkPermissionDeniedException)
    }

    @Test
    fun `localNetworkRefusal is null when the grant is held`() {
        every { analyzer.hasLocalNetworkPermission() } returns true
        assertNull(gate.localNetworkRefusal("TEST", "192.168.1.112"))
    }

    @Test
    fun `localNetworkRefusal is null for a host that is not a LAN literal`() {
        every { analyzer.hasLocalNetworkPermission() } returns false
        every { analyzer.isLocalNetworkHost("files.example.com") } returns false
        assertNull(gate.localNetworkRefusal("TEST", "files.example.com"))
    }

    @Test
    fun `requireLocalNetworkPermission throws the refusal`() {
        every { analyzer.hasLocalNetworkPermission() } returns false
        every { analyzer.isLocalNetworkHost("192.168.1.112") } returns true
        assertThrows(LocalNetworkPermissionDeniedException::class.java) {
            gate.requireLocalNetworkPermission("TEST", "192.168.1.112")
        }
    }
}
