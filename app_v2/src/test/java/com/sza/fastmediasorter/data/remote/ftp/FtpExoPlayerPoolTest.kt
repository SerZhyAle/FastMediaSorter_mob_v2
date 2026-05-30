package com.sza.fastmediasorter.data.remote.ftp

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.commons.net.ftp.FTPClient
import org.junit.Test

/**
 * Unit tests for the JVM-reachable lifecycle helpers of [FtpExoPlayerPool]. The acquire path opens
 * a socket-backed [FTPClient] and is out of scope; release/cleanup operate on mocked clients and an
 * empty pool. No sockets are opened.
 */
class FtpExoPlayerPoolTest {

    @Test
    fun `releaseExoPlayerConnection completes command and disconnects connected client`() {
        val pool = FtpExoPlayerPool()
        val client = mockk<FTPClient>(relaxed = true)
        every { client.isConnected } returns true
        pool.releaseExoPlayerConnection(client)
        verify { client.completePendingCommand() }
        verify { client.logout() }
        verify { client.disconnect() }
    }

    @Test
    fun `releaseExoPlayerConnection skips disconnect for already-disconnected client`() {
        val pool = FtpExoPlayerPool()
        val client = mockk<FTPClient>(relaxed = true)
        every { client.isConnected } returns false
        pool.releaseExoPlayerConnection(client)
        verify { client.completePendingCommand() }
        verify(exactly = 0) { client.disconnect() }
    }

    @Test
    fun `releaseExoPlayerConnection tolerates null client`() {
        val pool = FtpExoPlayerPool()
        pool.releaseExoPlayerConnection(null)
    }

    @Test
    fun `releaseExoPlayerConnection swallows completePendingCommand failure`() {
        val pool = FtpExoPlayerPool()
        val client = mockk<FTPClient>(relaxed = true)
        every { client.completePendingCommand() } throws RuntimeException("broken pipe")
        every { client.isConnected } returns false
        pool.releaseExoPlayerConnection(client)
    }

    @Test
    fun `cleanupIdleFtpConnections is a no-op on empty pool`() {
        val pool = FtpExoPlayerPool()
        pool.cleanupIdleFtpConnections()
    }
}
