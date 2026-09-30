package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The embedded SFTP server's pairing code: what the server phone shows and what the scanning phone reads. */
class SftpPairingPayloadTest {

    private val payload = SftpPairingPayload(
        hosts = listOf("192.168.1.5", "100.64.0.7"),
        port = 2222,
        username = "fms",
        password = "p&ss=w,rd %2F",
        hostKeyFingerprint = "SHA256:abc+/def",
    )

    @Test
    fun `a code round-trips including separators inside the password`() {
        assertEquals(payload, SftpPairingPayload.decode(payload.encode()))
    }

    @Test
    fun `a key-login code carries no password field`() {
        val keyOnly = payload.copy(password = null)
        val code = keyOnly.encode()
        assertFalse(code.contains("pw="))
        assertEquals(keyOnly, SftpPairingPayload.decode(code))
    }

    @Test
    fun `the prefix marks the code`() {
        assertTrue(payload.encode().startsWith(SftpPairingPayload.PREFIX))
        assertFalse(SftpPairingPayload.isPairingPayload("FMSBCAST1:whatever"))
        assertNull(SftpPairingPayload.decode("FMSBCAST1:whatever"))
    }

    @Test
    fun `an incomplete or damaged code is refused`() {
        assertNull(SftpPairingPayload.decode("FMSSFTP1:"))
        assertNull(SftpPairingPayload.decode("FMSSFTP1:h=1.2.3.4&p=2222&u=fms"))
        assertNull(SftpPairingPayload.decode("FMSSFTP1:h=1.2.3.4&p=0&u=fms&fp=SHA256%3Ax"))
        assertNull(SftpPairingPayload.decode("FMSSFTP1:h=1.2.3.4&p=2222&u=fms&fp=MD5%3Ax"))
        assertNull(SftpPairingPayload.decode("FMSSFTP1:h=%ZZ&p=2222&u=fms&fp=SHA256%3Ax"))
    }
}
