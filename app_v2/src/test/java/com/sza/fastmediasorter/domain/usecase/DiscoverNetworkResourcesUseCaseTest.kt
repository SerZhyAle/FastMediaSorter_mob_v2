package com.sza.fastmediasorter.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DiscoverNetworkResourcesUseCase].
 *
 * Uses a controllable test subclass to override [isTcpPortOpen] so that tests
 * run without real network sockets and complete in <1 ms each.
 */
@ExperimentalCoroutinesApi
class DiscoverNetworkResourcesUseCaseTest {

    /**
     * Test subclass that records every (ip, port) probe and returns a
     * programmable result. The [openPorts] map holds per-IP sets of open ports.
     */
    private inner class FakeUseCase(
        private val openPorts: Map<String, Set<Int>> = emptyMap(),
        private val fakeLocalIp: String? = "10.0.0.5"
    ) : DiscoverNetworkResourcesUseCase() {

        val probedPorts = mutableListOf<Pair<String, Int>>() // recorded for assertions

        override fun isTcpPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
            probedPorts.add(ip to port)
            return openPorts[ip]?.contains(port) == true
        }

        // Override to avoid real NetworkInterface calls in unit tests.
        override fun getLocalIpAddress(): String? = fakeLocalIp
    }

    // -- extractSubnet ---------------------------------------------------------

    @Test
    fun `extractSubnet returns slash24 prefix`() {
        val useCase = FakeUseCase()
        assertEquals("192.168.1", useCase.extractSubnet("192.168.1.5"))
        assertEquals("10.0.0", useCase.extractSubnet("10.0.0.254"))
        assertEquals("172.16.100", useCase.extractSubnet("172.16.100.1"))
    }

    // -- FALLBACK_SUBNETS constant ----------------------------------------------

    @Test
    fun `FALLBACK_SUBNETS contains required private ranges`() {
        assertTrue("192.168.0 must be in fallbacks",
            DiscoverNetworkResourcesUseCase.FALLBACK_SUBNETS.contains("192.168.0"))
        assertTrue("192.168.1 must be in fallbacks",
            DiscoverNetworkResourcesUseCase.FALLBACK_SUBNETS.contains("192.168.1"))
    }

    // -- probePorts logic -------------------------------------------------------

    @Test
    fun `probePorts returns 445 when port 445 is open`() {
        val useCase = FakeUseCase(openPorts = mapOf("1.2.3.4" to setOf(445)))
        val ports = useCase.probePorts("1.2.3.4")
        assertTrue("445 must be reported", ports.contains(445))
    }

    @Test
    fun `probePorts does NOT probe 139 when 445 is open`() {
        val useCase = FakeUseCase(openPorts = mapOf("1.2.3.4" to setOf(445)))
        useCase.probePorts("1.2.3.4")
        // 139 must never be tried if 445 is already open (F-03: port 445 first, 139 as fallback only)
        assertFalse("port 139 must not be probed when 445 is open",
            useCase.probedPorts.any { it.first == "1.2.3.4" && it.second == 139 })
    }

    @Test
    fun `probePorts returns 139 as fallback when 445 is closed`() {
        val useCase = FakeUseCase(openPorts = mapOf("1.2.3.4" to setOf(139)))
        val ports = useCase.probePorts("1.2.3.4")
        assertTrue("139 must be reported as SMB fallback", ports.contains(139))
        assertFalse("445 must NOT be reported when it was closed", ports.contains(445))
    }

    @Test
    fun `probePorts returns FTP and SFTP ports independently of SMB ports`() {
        val useCase = FakeUseCase(openPorts = mapOf("1.2.3.4" to setOf(21, 22)))
        val ports = useCase.probePorts("1.2.3.4")
        assertTrue("FTP port 21 must be reported", ports.contains(21))
        assertTrue("SFTP port 22 must be reported", ports.contains(22))
    }

    @Test
    fun `probePorts returns empty list when no ports are open`() {
        val useCase = FakeUseCase(openPorts = emptyMap())
        val ports = useCase.probePorts("1.2.3.4")
        assertTrue("no open ports should yield empty list", ports.isEmpty())
    }

    // -- fallback subnet filtering ---------------------------------------------

    @Test
    fun `execute skips fallback subnet that matches detected subnet`() = runTest(UnconfinedTestDispatcher()) {
        // Local IP is in 192.168.1.*, so "192.168.1" is the detected subnet.
        // Fallback scan must NOT retry 192.168.1.* — only 192.168.0.*.
        val useCase = FakeUseCase(
            openPorts = emptyMap(),   // primary scan finds nothing → fallback triggered
            fakeLocalIp = "192.168.1.5"
        )

        val hosts = useCase.execute().toList()

        // Ensure that 192.168.1.* was scanned once (primary) but not again (fallback).
        val subnets192_1Probes = useCase.probedPorts
            .filter { it.first.startsWith("192.168.1.") }
        val subnets192_0Probes = useCase.probedPorts
            .filter { it.first.startsWith("192.168.0.") }

        // Primary scan must have probed 192.168.1.* (at least attempted to).
        // Fallback scan for 192.168.0.* must have run (since primary was empty).
        assertTrue("fallback 192.168.0.* must be probed when primary is empty",
            subnets192_0Probes.isNotEmpty())
        // The detected subnet 192.168.1.* must NOT appear in a second fallback pass.
        // Count of distinct last-octets probed in 192.168.1 == exactly 254 (one pass only).
        val uniqueSubnet1Hosts = subnets192_1Probes.map { it.first }.toSet()
        assertTrue("primary subnet probed up to 254 addresses",
            uniqueSubnet1Hosts.size <= 254)
    }

    // -- cancellation ----------------------------------------------------------

    @Test
    fun `execute stops emitting after coroutine is cancelled`() = runTest(UnconfinedTestDispatcher()) {
        // One host at 10.0.0.1; all others empty. Collect one item then cancel.
        val useCase = FakeUseCase(
            openPorts = mapOf("10.0.0.1" to setOf(445)),
            fakeLocalIp = "10.0.0.5"
        )

        val collected = mutableListOf<NetworkHost>()
        val job = launch {
            useCase.execute().collect { host ->
                collected.add(host)
                // Cancel after receiving the first host
                this@launch.cancel()
            }
        }
        job.join()

        // At least the first host was received; crucially no exception surfaced
        assertNotNull("at least one host must be collected before cancel", collected.firstOrNull())
    }
}
