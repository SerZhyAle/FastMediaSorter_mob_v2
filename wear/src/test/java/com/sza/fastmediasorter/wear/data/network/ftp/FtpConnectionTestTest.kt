package com.sza.fastmediasorter.wear.data.network.ftp

import com.sza.fastmediasorter.wear.data.network.FakeWearNetworkChannelMonitor
import com.sza.fastmediasorter.wear.data.network.WearEndpointResolver
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FtpConnectionTestTest {

    /**
     * S1554: the previous version of this test pinned the stub as the contract - it asserted that
     * every call fails with UnsupportedOperationException. That is exactly what a real
     * implementation must stop doing, so the assertion is inverted rather than deleted: a refused
     * connection must report the refusal, not the absence of the feature.
     */
    @Test
    fun `testFtp reports the connection failure rather than declaring FTP unsupported`() = runTest {
        val resolver = WearEndpointResolver(FakeWearNetworkChannelMonitor())
        val result = FtpConnectionTest(resolver).testFtp(unreachableSource())

        assertTrue(result.isFailure)
        assertFalse(
            "FTP testing is implemented on Wear; a failure must describe the connection",
            result.exceptionOrNull() is UnsupportedOperationException
        )
    }

    /** Loopback with a port nothing listens on: refused immediately, with no DNS and no network. */
    private fun unreachableSource() = NetworkSource(
        type = NetworkSourceType.FTP,
        name = "FTP",
        server = "127.0.0.1",
        port = 1,
        username = "user",
        password = "password"
    )
}
