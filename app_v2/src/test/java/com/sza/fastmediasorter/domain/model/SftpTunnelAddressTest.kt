package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SftpTunnelAddressTest {

    @Test
    fun `a tunnel host round-trips and an ordinary host is not one`() {
        val host = SftpTunnelAddress.host(SHARE_ID, "relay.example.net")
        assertEquals(SftpTunnelAddress.Parts(SHARE_ID, "relay.example.net"), SftpTunnelAddress.parse(host))
        assertFalse(SftpTunnelAddress.isTunnel("192.168.1.5"))
        assertFalse(SftpTunnelAddress.isTunnel("user@relay.example.net"))
        assertNull(SftpTunnelAddress.parse("$SHARE_ID@"))
    }

    @Test
    fun `the tunnel of a v2 code is its exchange endpoint under the share id`() {
        val pairing = SftpPairingPayload(
            hosts = listOf("192.168.1.5"),
            port = 2222,
            username = "fms",
            password = "pw",
            hostKeyFingerprint = "SHA256:x",
            exchangeEndpoint = "[2001:db8::7]:44022",
            shareId = SHARE_ID,
        )
        val tunnel = checkNotNull(SftpTunnelAddress.fromPairing(pairing))
        assertEquals(HostPort("$SHARE_ID@2001:db8::7", 44022), tunnel)
        assertTrue(SftpTunnelAddress.isTunnel(tunnel.host))
        assertNull(SftpTunnelAddress.fromPairing(pairing.copy(exchangeEndpoint = null, shareId = null)))
    }

    @Test
    fun `a log line never carries the share id`() {
        val host = SftpTunnelAddress.host(SHARE_ID, "relay.example.net")
        assertEquals("***@relay.example.net", SftpTunnelAddress.forLog(host))
        assertEquals("192.168.1.5", SftpTunnelAddress.forLog("192.168.1.5"))
    }

    private companion object {
        const val SHARE_ID = "q3Vb7YtK0xP2mN9sLfR4wA"
    }
}
