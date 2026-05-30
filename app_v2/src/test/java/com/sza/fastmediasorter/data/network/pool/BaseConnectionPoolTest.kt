package com.sza.fastmediasorter.data.network.pool

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for the abstract [BaseConnectionPool] generic pooling logic, exercised through a
 * minimal in-memory test subclass. No real sockets - the "connection" is a plain counter object.
 */
class BaseConnectionPoolTest {

    /** A fake connection whose validity is toggleable, tracking close() calls. */
    private class FakeConn(val id: Int) {
        var valid = true
        var closed = false
    }

    private class TestPool(
        maxConnections: Int = 4,
        idleTimeoutMs: Long = 10_000,
    ) : BaseConnectionPool<String, FakeConn>(maxConnections, idleTimeoutMs, "TEST") {
        val createCount = AtomicInteger(0)
        val closed = mutableListOf<Int>()
        var validOverride: ((FakeConn) -> Boolean)? = null

        override suspend fun createConnection(key: String): FakeConn =
            FakeConn(createCount.incrementAndGet())

        override fun isConnectionValid(connection: FakeConn): Boolean =
            validOverride?.invoke(connection) ?: connection.valid

        override fun closeConnection(connection: FakeConn) {
            connection.closed = true
            closed.add(connection.id)
        }

        suspend fun <T> use(key: String, block: suspend (FakeConn) -> T): T = withConnection(key, block)
    }

    @Test
    fun `first use creates a connection and pool size becomes one`() = runBlocking {
        val pool = TestPool()
        val id = pool.use("k") { it.id }
        assertEquals(1, id)
        assertEquals(1, pool.createCount.get())
        assertEquals(1, pool.getPoolSize())
    }

    @Test
    fun `valid pooled connection is reused on second use`() = runBlocking {
        val pool = TestPool()
        pool.use("k") { it.id }
        pool.use("k") { it.id }
        assertEquals(1, pool.createCount.get()) // not recreated
        assertEquals(1, pool.getPoolSize())
    }

    @Test
    fun `stale connection is closed and recreated`() = runBlocking {
        val pool = TestPool()
        pool.use("k") { it.id }
        // Mark everything invalid so the next acquire treats the pooled entry as stale.
        pool.validOverride = { false }
        pool.use("k") { it.id }
        assertEquals(2, pool.createCount.get())
        assertTrue(pool.closed.contains(1)) // the stale connection was closed
    }

    @Test
    fun `distinct keys get distinct pooled connections`() = runBlocking {
        val pool = TestPool()
        pool.use("a") { it.id }
        pool.use("b") { it.id }
        assertEquals(2, pool.createCount.get())
        assertEquals(2, pool.getPoolSize())
    }

    @Test
    fun `cleanupIdleConnections closes connections past idle timeout`() = runBlocking {
        val pool = TestPool(idleTimeoutMs = -1) // every connection is immediately "idle"
        pool.use("k") { it.id }
        pool.cleanupIdleConnections()
        assertEquals(0, pool.getPoolSize())
        assertTrue(pool.closed.contains(1))
    }

    @Test
    fun `cleanupIdleConnections keeps fresh connections`() = runBlocking {
        val pool = TestPool(idleTimeoutMs = 10_000)
        pool.use("k") { it.id }
        pool.cleanupIdleConnections()
        assertEquals(1, pool.getPoolSize())
    }

    @Test
    fun `clearAllConnections empties the pool and closes everything`() = runBlocking {
        val pool = TestPool()
        pool.use("a") { it.id }
        pool.use("b") { it.id }
        pool.clearAllConnections()
        assertEquals(0, pool.getPoolSize())
        assertTrue(pool.closed.containsAll(listOf(1, 2)))
    }
}
