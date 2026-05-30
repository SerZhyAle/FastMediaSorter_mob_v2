package com.sza.fastmediasorter.data.network

import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.sza.fastmediasorter.data.network.model.ConnectionKey
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SmbConnectionPool] map ownership / matching logic.
 *
 * The SMBJ tri-layer (Connection/Session/DiskShare) is replaced with relaxed mocks - no socket is
 * opened. The async cascade-close path (closeConnectionAsync / closeAll / closeAllExceptWorker)
 * launches onto an internal IO scope and is not asserted on directly; tests verify the synchronous
 * map-mutation outcome (entry removed, count returned) which is the testable decision logic.
 */
class SmbConnectionPoolTest {

    private fun key(server: String, share: String = "media") = ConnectionKey(
        server = server, port = 445, shareName = share, username = "u", domain = ""
    )

    private fun pooled(consumer: ConnectionConsumer = ConnectionConsumer.SCANNER) = PooledConnection(
        connection = mockk<Connection>(relaxed = true),
        session = mockk<Session>(relaxed = true),
        share = mockk<DiskShare>(relaxed = true),
        consumer = consumer
    )

    @Test
    fun `put then get returns the same pooled connection`() {
        val pool = SmbConnectionPool()
        val k = key("nas")
        val p = pooled()
        pool.put(k, p)
        assertSame(p, pool.get(k))
    }

    @Test
    fun `get returns null for absent key`() {
        assertNull(SmbConnectionPool().get(key("nas")))
    }

    @Test
    fun `remove drops entry and returns it without closing`() {
        val pool = SmbConnectionPool()
        val k = key("nas")
        val p = pooled()
        pool.put(k, p)
        assertSame(p, pool.remove(k))
        assertNull(pool.get(k))
    }

    @Test
    fun `snapshot reflects current entries`() {
        val pool = SmbConnectionPool()
        pool.put(key("a"), pooled())
        pool.put(key("b"), pooled())
        assertEquals(2, pool.snapshot().size)
    }

    @Test
    fun `hasActiveConnectionForServer matches host and port`() {
        val pool = SmbConnectionPool()
        pool.put(key("nas"), pooled())
        assertTrue(pool.hasActiveConnectionForServer("nas", 445))
        assertFalse(pool.hasActiveConnectionForServer("nas", 139))
        assertFalse(pool.hasActiveConnectionForServer("other", 445))
    }

    @Test
    fun `removeMatchingAndCloseAsync removes only matching keys and returns count`() {
        val pool = SmbConnectionPool()
        pool.put(key("nas", "media"), pooled())
        pool.put(key("nas", "photos"), pooled())
        pool.put(key("other", "media"), pooled())
        val removed = pool.removeMatchingAndCloseAsync { it.server == "nas" }
        assertEquals(2, removed)
        assertFalse(pool.hasActiveConnectionForServer("nas", 445))
        assertTrue(pool.hasActiveConnectionForServer("other", 445))
    }

    @Test
    fun `removeAndCloseSync removes the entry`() {
        val pool = SmbConnectionPool()
        val k = key("nas")
        pool.put(k, pooled())
        assertTrue(pool.removeAndCloseSync(k) != null)
        assertNull(pool.get(k))
    }

    @Test
    fun `removeAndCloseSync on absent key returns null`() {
        assertNull(SmbConnectionPool().removeAndCloseSync(key("ghost")))
    }
}
