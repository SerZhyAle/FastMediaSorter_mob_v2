package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SftpExchangeConfigTest {

    // DIAGNOSTIC-REPORT 0.13 section 10 and rule 3: the share id never prints.
    @Test
    fun `toString hides the share id and keeps absence visible`() {
        val text = SftpExchangeConfig(enabled = true, shareId = "q3Vb7YtK0xP2mN9sLfR4wA").toString()

        assertFalse(text.contains("q3Vb7YtK0xP2mN9sLfR4wA"))
        assertTrue(text.contains("enabled=true"))
        assertTrue(SftpExchangeConfig(enabled = false, shareId = "").toString().contains("shareId=)"))
    }
}
