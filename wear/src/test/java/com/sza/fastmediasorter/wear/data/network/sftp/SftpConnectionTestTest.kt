package com.sza.fastmediasorter.wear.data.network.sftp

import com.sza.fastmediasorter.wear.data.network.FakeWearNetworkChannelMonitor
import com.sza.fastmediasorter.wear.data.network.WearEndpointResolver
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SftpConnectionTestTest {

    /**
     * Loopback with nothing listening is refused immediately, so this stays offline and fast while
     * still exercising the real connect path. The negative assertion is the point: the previous
     * implementation answered every call with UnsupportedOperationException, and the ViewModel reads
     * that exact type to show "test not supported" instead of the server's reason.
     */
    @Test
    fun `testSftp reports the real connect failure rather than an unsupported stub`() = runTest {
        val resolver = WearEndpointResolver(FakeWearNetworkChannelMonitor())
        val result = SftpConnectionTest(resolver).testSftp(makeSource(port = UNUSED_LOOPBACK_PORT))

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertFalse(exception is UnsupportedOperationException)
    }

    private fun makeSource(
        server: String = "127.0.0.1",
        port: Int = UNUSED_LOOPBACK_PORT
    ) = NetworkSource(
        type = NetworkSourceType.SFTP,
        name = "SFTP",
        server = server,
        port = port,
        username = "user",
        password = "password"
    )

    private companion object {
        const val UNUSED_LOOPBACK_PORT = 1
    }
}
