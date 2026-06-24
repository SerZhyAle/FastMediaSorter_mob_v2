package com.sza.fastmediasorter.data.remote.sftp

import com.sza.fastmediasorter.core.network.NetworkReachabilityGate
import com.sza.fastmediasorter.core.network.NetworkStateMonitor
import com.sza.fastmediasorter.data.network.IdleDisconnectPolicy
import com.sza.fastmediasorter.data.network.exceptions.NetworkConnectionLostException
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Test

class SftpClientTest {

    @Test
    fun `getConnectionForExoPlayer throws synchronously when reachability gate denies`() {
        val gate = mockk<NetworkReachabilityGate>()
        every { gate.requireAnyNetwork("SFTP") } throws NetworkConnectionLostException()
        val client = SftpClient(
            gate,
            dagger.Lazy { mockk(relaxed = true) },
            mockk<IdleDisconnectPolicy>(relaxed = true),
            mockk<NetworkStateMonitor>(relaxed = true),
        )

        val info = SftpClient.SftpConnectionInfo(host = "anyhost", port = 22, username = "u", password = "p")

        val thrown = try {
            client.getConnectionForExoPlayer(info)
            null
        } catch (e: NetworkConnectionLostException) {
            e
        }

        assertNotNull(thrown)
    }
}
