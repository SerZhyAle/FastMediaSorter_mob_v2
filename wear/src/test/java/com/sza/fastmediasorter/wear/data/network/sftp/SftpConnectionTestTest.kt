package com.sza.fastmediasorter.wear.data.network.sftp

import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SftpConnectionTestTest {

    @Test
    fun `testSftp returns unsupported failure on Wear`() = runTest {
        val result = SftpConnectionTest().testSftp(makeSource())

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is UnsupportedOperationException)
        assertEquals("SFTP connection test is not available on Wear OS", exception?.message)
    }

    private fun makeSource() = NetworkSource(
        type = NetworkSourceType.SFTP,
        name = "SFTP",
        server = "example.com",
        username = "user",
        password = "password"
    )
}