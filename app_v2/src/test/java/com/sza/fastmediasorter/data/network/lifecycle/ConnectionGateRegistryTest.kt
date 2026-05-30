package com.sza.fastmediasorter.data.network.lifecycle

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [ConnectionGateRegistry] protocol-to-gate registration. */
class ConnectionGateRegistryTest {

    private fun gate(p: NetworkProtocol): NetworkConnectionGate<*> =
        mockk<NetworkConnectionGate<*>>().also { every { it.protocol } returns p }

    @Test
    fun `gateFor returns null when nothing registered`() {
        assertNull(ConnectionGateRegistry().gateFor<Any>(NetworkProtocol.SMB))
    }

    @Test
    fun `register then gateFor returns the same gate`() {
        val registry = ConnectionGateRegistry()
        val smb = gate(NetworkProtocol.SMB)
        registry.register(smb)
        assertSame(smb, registry.gateFor<Any>(NetworkProtocol.SMB))
    }

    @Test
    fun `re-register same protocol overwrites previous gate`() {
        val registry = ConnectionGateRegistry()
        registry.register(gate(NetworkProtocol.FTP))
        val replacement = gate(NetworkProtocol.FTP)
        registry.register(replacement)
        assertSame(replacement, registry.gateFor<Any>(NetworkProtocol.FTP))
        assertEquals(1, registry.all().size)
    }

    @Test
    fun `all returns every distinct protocol gate`() {
        val registry = ConnectionGateRegistry()
        registry.register(gate(NetworkProtocol.SMB))
        registry.register(gate(NetworkProtocol.SFTP))
        registry.register(gate(NetworkProtocol.CLOUD))
        assertEquals(3, registry.all().size)
    }

    @Test
    fun `gateFor for unregistered protocol returns null`() {
        val registry = ConnectionGateRegistry()
        registry.register(gate(NetworkProtocol.SMB))
        assertTrue(registry.gateFor<Any>(NetworkProtocol.CLOUD) == null)
    }
}
