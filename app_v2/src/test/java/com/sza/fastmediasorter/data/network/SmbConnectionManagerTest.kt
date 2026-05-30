package com.sza.fastmediasorter.data.network

import com.sza.fastmediasorter.core.network.NetworkReachabilityGate
import com.sza.fastmediasorter.core.network.NetworkStateMonitor
import com.sza.fastmediasorter.data.network.lifecycle.NetworkLifecycleBootstrapper
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import dagger.Lazy
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Unit tests for the JVM-reachable portions of [SmbConnectionManager]: tiered client caching in
 * [SmbConnectionManager.getClient] and the no-op-safe lifecycle helpers. The pooled connect / retry
 * paths require live SMBJ sockets and are out of scope. All collaborators are mocked; the cached
 * `SMBClient` is constructed from an immutable `SmbConfig` (no socket).
 */
class SmbConnectionManagerTest {

    private fun manager(): SmbConnectionManager {
        val monitor = mockk<NetworkStateMonitor>(relaxed = true)
        val tracker = mockk<SmbPlaybackConnectionTracker>(relaxed = true)
        val gate = mockk<NetworkReachabilityGate>(relaxed = true)
        @Suppress("UNCHECKED_CAST")
        val bootstrap = mockk<Lazy<NetworkLifecycleBootstrapper>>(relaxed = true)
        val idle = mockk<IdleDisconnectPolicy>(relaxed = true)
        return SmbConnectionManager(monitor, tracker, gate, bootstrap, idle)
    }

    @Test
    fun `getClient caches one SMBClient instance per server-port tier`() {
        val mgr = manager()
        val first = mgr.getClient("192.168.1.10", 445)
        val second = mgr.getClient("192.168.1.10", 445)
        assertNotNull(first)
        assertSame(first, second)
    }

    @Test
    fun `setResetCallback and clearConnectionPool are safe on empty pool`() {
        val mgr = manager()
        mgr.setResetCallback(object : SmbResetCallback {
            override fun onAutoReset(reason: String) { /* no-op */ }
        })
        mgr.clearConnectionPool()
        mgr.setResetCallback(null)
    }

    @Test
    fun `invalidateExoPlayerConnection is a no-op when pool is empty`() {
        val mgr = manager()
        mgr.invalidateExoPlayerConnection(SmbConnectionInfo(server = "srv", shareName = "share"))
    }

    @Test
    fun `close releases pool without throwing`() {
        val mgr = manager()
        mgr.close()
    }
}
