package com.sza.fastmediasorter.data.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for the abstract [BaseConnectionPool] (data/network) pooled-with-recovery logic,
 * exercised through a minimal in-memory subclass. No sockets are opened - the "connection" is a
 * plain counter and validity/criticality are controlled by the test.
 */
class BaseConnectionPoolTest {

    private class FakeConn(val id: Int)

    private class TestPool : BaseConnectionPool<String, FakeConn>(maxConnections = 4, idleTimeoutMs = 60_000) {
        val createCount = AtomicInteger(0)
        val closed = mutableListOf<Int>()
        var alive = true
        var critical = false
        var resetCount = 0

        override suspend fun createConnection(key: String): FakeConn = FakeConn(createCount.incrementAndGet())
        override fun isConnectionAlive(connection: FakeConn): Boolean = alive
        override fun closeConnection(connection: FakeConn) { closed.add(connection.id) }
        override fun isCriticalError(exception: Exception): Boolean = critical
        override fun resetClients() { resetCount++ }

        suspend fun <T> use(key: String, block: suspend (FakeConn) -> T): T = withConnection(key, block)
    }

    @Test
    fun `first call creates a fresh connection`() = runBlocking {
        val pool = TestPool()
        val id = pool.use("k") { it.id }
        assertEquals(1, id)
        assertEquals(1, pool.createCount.get())
    }

    @Test
    fun `valid pooled connection is reused`() = runBlocking {
        val pool = TestPool()
        pool.use("k") { it.id }
        pool.use("k") { it.id }
        assertEquals(1, pool.createCount.get())
    }

    @Test
    fun `dead pooled connection forces a fresh create`() = runBlocking {
        val pool = TestPool()
        pool.use("k") { it.id }
        pool.alive = false // pooled entry now considered dead
        val id = pool.use("k") { it.id }
        assertEquals(2, id)
        assertEquals(2, pool.createCount.get())
    }

    @Test
    fun `cancellation propagates and keeps connection`() = runBlocking {
        val pool = TestPool()
        pool.use("k") { it.id } // seed a valid pooled connection
        assertThrows(CancellationException::class.java) {
            runBlocking { pool.use("k") { throw CancellationException("cancel") } }
        }
        // Connection was not removed; next reuse does not recreate.
        pool.use("k") { it.id }
        assertEquals(1, pool.createCount.get())
    }

    @Test
    fun `block failure on fresh connection rethrows and removes entry`() = runBlocking {
        val pool = TestPool()
        assertThrows(IllegalStateException::class.java) {
            runBlocking { pool.use("k") { throw IllegalStateException("op failed") } }
        }
        // Entry removed; a subsequent successful call creates a new connection.
        pool.alive = true
        val id = pool.use("k") { it.id }
        assertEquals(2, id)
    }

    @Test
    fun `critical error on fresh connection triggers reset`() = runBlocking {
        val pool = TestPool()
        pool.critical = true
        assertThrows(IllegalStateException::class.java) {
            runBlocking { pool.use("k") { throw IllegalStateException("critical") } }
        }
        assertEquals(1, pool.resetCount)
    }

    @Test
    fun `clearConnectionPool closes pooled connections`() = runBlocking {
        val pool = TestPool()
        pool.use("a") { it.id }
        pool.use("b") { it.id }
        pool.clearConnectionPool()
        assertEquals(2, pool.closed.size)
    }

    @Test
    fun `forceFullReset closes connections and resets clients`() = runBlocking {
        val pool = TestPool()
        pool.use("a") { it.id }
        pool.forceFullReset()
        assertEquals(1, pool.resetCount)
        assertEquals(1, pool.closed.size)
    }
}
