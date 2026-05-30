package com.sza.fastmediasorter.data.remote.ftp

import org.apache.commons.net.ftp.FTPClient
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the [applyUtf8Encoding] control-encoding helper.
 *
 * Operates on a real (unconnected) [FTPClient] - setting controlEncoding is a pure field
 * assignment that performs no network I/O. [enableUtf8Mode] is not covered: it issues an
 * OPTS command over a live control channel and has no observable return value.
 */
class FtpEncodingSupportTest {

    @Test
    fun `applyUtf8Encoding sets control encoding to UTF-8`() {
        val client = FTPClient()
        client.applyUtf8Encoding()
        assertEquals("UTF-8", client.controlEncoding)
    }
}
