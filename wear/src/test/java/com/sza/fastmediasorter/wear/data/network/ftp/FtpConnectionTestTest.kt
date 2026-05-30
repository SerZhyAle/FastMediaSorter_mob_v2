package com.sza.fastmediasorter.wear.data.network.ftp

import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FtpConnectionTestTest {

    @Test
    fun `testFtp returns unsupported failure on Wear`() = runTest {
        val result = FtpConnectionTest().testFtp(makeSource())

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is UnsupportedOperationException)
        assertEquals("FTP connection test is not available on Wear OS", exception?.message)
    }

    private fun makeSource() = NetworkSource(
        type = NetworkSourceType.FTP,
        name = "FTP",
        server = "example.com",
        username = "user",
        password = "password"
    )
}