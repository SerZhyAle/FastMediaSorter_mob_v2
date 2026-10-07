package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SftpExchangeConfigTest {

    private val usable = SftpExchangeConfig(
        enabled = true,
        host = "relay.example.net",
        port = 44022,
        password = "Passw0rdTest",
        shareId = "q3Vb7YtK0xP2mN9sLfR4wA",
        pinnedCertificate = null,
    )

    @Test
    fun `the password policy is the owner floor`() {
        assertTrue(SftpExchangePasswordPolicy.accepts("Passw0rdTest"))
        assertFalse(SftpExchangePasswordPolicy.accepts(null))
        assertFalse(SftpExchangePasswordPolicy.accepts("Passw0rd"))
        assertFalse(SftpExchangePasswordPolicy.accepts("PasswordTest"))
        assertFalse(SftpExchangePasswordPolicy.accepts("passw0rdtest"))
        assertFalse(SftpExchangePasswordPolicy.accepts("PASSW0RDTEST"))
        // Cyrillic letters do not count as the Latin letters the policy asks for.
        assertFalse(SftpExchangePasswordPolicy.accepts("Пароль1234а"))
    }

    @Test
    fun `a config is usable only when every part is present`() {
        assertTrue(usable.isUsable)
        assertFalse(usable.copy(enabled = false).isUsable)
        assertFalse(usable.copy(host = " ").isUsable)
        assertFalse(usable.copy(port = 0).isUsable)
        assertFalse(usable.copy(password = "short").isUsable)
        assertFalse(usable.copy(shareId = "bad").isUsable)
    }

    @Test
    fun `the endpoint brackets an IPv6 literal`() {
        assertEquals("relay.example.net:44022", usable.endpoint())
        assertEquals("[2001:db8::7]:44022", usable.copy(host = "2001:db8::7").endpoint())
        assertTrue(SftpPairingPayload.isHostPort(usable.copy(host = "2001:db8::7").endpoint()))
    }
}
