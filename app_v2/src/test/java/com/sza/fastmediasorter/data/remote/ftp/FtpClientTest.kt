package com.sza.fastmediasorter.data.remote.ftp

import com.sza.fastmediasorter.core.network.NetworkReachabilityGate
import com.sza.fastmediasorter.data.network.IdleDisconnectPolicy
import com.sza.fastmediasorter.data.network.exceptions.NetworkConnectionLostException
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test

class FtpClientTest {

    @Test
    fun `connect propagates NetworkConnectionLostException when reachability gate denies`() = runBlocking {
        val gate = mockk<NetworkReachabilityGate>()
        every { gate.requireAnyNetwork("FTP") } throws NetworkConnectionLostException()
        val client = FtpClient(gate, dagger.Lazy { mockk(relaxed = true) }, mockk<IdleDisconnectPolicy>(relaxed = true))

        val result = client.connect(host = "anyhost", port = 21, username = "u", password = "p")

        assertNotNull(result.exceptionOrNull())
        assert(result.exceptionOrNull() is NetworkConnectionLostException)
    }

    @Test
    fun `getConnectionForExoPlayer throws synchronously when reachability gate denies`() {
        val gate = mockk<NetworkReachabilityGate>()
        every { gate.requireAnyNetwork("FTP") } throws NetworkConnectionLostException()
        val client = FtpClient(gate, dagger.Lazy { mockk(relaxed = true) }, mockk<IdleDisconnectPolicy>(relaxed = true))

        val info = FtpExoPlayerPool.FtpConnectionInfo(host = "anyhost", port = 21, username = "u", password = "p")

        val thrown = try {
            client.getConnectionForExoPlayer(info)
            null
        } catch (e: NetworkConnectionLostException) {
            e
        }

        assertNotNull(thrown)
    }
}
