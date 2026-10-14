package com.sza.fastmediasorter.wear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** S4228: the vectors follow the phone's `SftpPairingPayload.encode` byte for byte - URL-encoded values, fixed keys. */
class WearSftpPairingCodeTest {

    @Test
    fun `a v1 code reads every field`() {
        val code = WearSftpPairingCode.decode(
            "FMSSFTP1:h=192.168.1.20,fe80%3A%3A1&p=2222&u=fms&pw=s%26cret&fp=SHA256%3Aabc%2B%2F"
        )

        assertEquals(listOf("192.168.1.20", "fe80::1"), code?.hosts)
        assertEquals(2222, code?.port)
        assertEquals("fms", code?.username)
        assertEquals("s&cret", code?.password)
        assertEquals("SHA256:abc+/", code?.hostKeyFingerprint)
    }

    @Test
    fun `a v2 code reads its direct fields and ignores the anywhere keys`() {
        val code = WearSftpPairingCode.decode(
            "FMSSFTP2:h=10.0.0.5&p=2022&u=owner&fp=SHA256%3Axyz&x=ex.example%3A7443&sid=AbCdEfGhIjKlMnOp&drv=1"
        )

        assertEquals(listOf("10.0.0.5"), code?.hosts)
        assertEquals(2022, code?.port)
        assertNull(code?.password)
        assertEquals("SHA256:xyz", code?.hostKeyFingerprint)
    }

    @Test
    fun `a code without a pinned host key is refused`() {
        assertNull(WearSftpPairingCode.decode("FMSSFTP1:h=192.168.1.23&p=2022&u=fms"))
    }

    @Test
    fun `a damaged percent sequence is refused`() {
        assertNull(WearSftpPairingCode.decode("FMSSFTP1:h=192.168.1.23&p=2022&u=fms&pw=%zz&fp=SHA256%3Aabc"))
    }
}
