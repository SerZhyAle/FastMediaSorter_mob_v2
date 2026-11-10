package com.sza.fastmediasorter.data.network.exceptions

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException

class BrowseFriendlyErrorResolverTest {

    @Test
    fun `a connect with no route to the host is connectivity`() {
        val failure = ConnectException(
            "failed to connect to /192.168.1.112 (port 11924) isConnected failed: EHOSTUNREACH (No route to host)",
        )

        assertTrue(BrowseFriendlyErrorResolver.isConnectivityFailure(failure))
    }

    @Test
    fun `a refused connect is connectivity even with an empty text`() {
        assertTrue(BrowseFriendlyErrorResolver.isConnectivityFailure(ConnectException()))
    }

    @Test
    fun `a failure with no connection cause is not connectivity`() {
        assertFalse(BrowseFriendlyErrorResolver.isConnectivityFailure(IllegalStateException("bad credentials")))
    }

    @Test
    fun `a no-such-file status means the server answered`() {
        val failure = SftpException(ChannelSftp.SSH_FX_NO_SUCH_FILE, "no such file")

        assertTrue(BrowseFriendlyErrorResolver.isServerNotFound(failure))
    }

    @Test
    fun `the status is found through a wrapper`() {
        val failure = IOException("listing failed", SftpException(ChannelSftp.SSH_FX_NO_SUCH_FILE, "no such file"))

        assertTrue(BrowseFriendlyErrorResolver.isServerNotFound(failure))
    }

    @Test
    fun `another status is not a missing path`() {
        val failure = SftpException(ChannelSftp.SSH_FX_PERMISSION_DENIED, "no such file")

        assertFalse(BrowseFriendlyErrorResolver.isServerNotFound(failure))
    }

    @Test
    fun `a failure with no status is not judged by its text`() {
        assertFalse(BrowseFriendlyErrorResolver.isServerNotFound(IOException("no such file")))
    }
}
