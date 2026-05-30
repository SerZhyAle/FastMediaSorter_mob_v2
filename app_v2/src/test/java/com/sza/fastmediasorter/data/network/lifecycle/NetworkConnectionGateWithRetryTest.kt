package com.sza.fastmediasorter.data.network.lifecycle

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.SocketTimeoutException

/**
 * Unit tests for the default [NetworkConnectionGate.withRetry] implementation - the
 * one-transparent-retry-on-transient-failure logic + connection release accounting.
 *
 * Uses a minimal in-memory fake gate (no real sockets) that records every acquire/release.
 */
class NetworkConnectionGateWithRetryTest {

    /** Records lifecycle events; each acquire returns a fresh incrementing connection id. */
    private class FakeGate : NetworkConnectionGate<Int> {
        override val protocol = NetworkProtocol.SMB
        var nextId = 0
        val acquired = mutableListOf<Int>()
        val released = mutableListOf<Pair<Int, Boolean>>()

        override suspend fun acquire(consumer: ConsumerType, resourceKey: String): Int {
            val id = nextId++
            acquired.add(id)
            return id
        }

        override fun release(connection: Int, success: Boolean) {
            released.add(connection to success)
        }

        override fun closeFor(consumer: ConsumerType) = Unit
        override fun lastRecreateMs(resourceKey: String): Long? = null
    }

    @Test
    fun `success on first attempt releases with success true and no retry`() = runBlocking {
        val gate = FakeGate()
        val result = gate.withRetry(ConsumerType.UI_PLAYER, "smb://nas:445") { conn -> conn * 10 }
        assertEquals(0, result)
        assertEquals(listOf(0), gate.acquired)
        assertEquals(listOf(0 to true), gate.released)
    }

    @Test
    fun `transient failure then success retries on a fresh connection`() = runBlocking {
        val gate = FakeGate()
        var calls = 0
        val result = gate.withRetry(ConsumerType.UI_SCANNER, "smb://nas:445") { conn ->
            calls++
            if (calls == 1) throw SocketTimeoutException("transient")
            conn
        }
        // Second (fresh) connection id = 1.
        assertEquals(1, result)
        assertEquals(listOf(0, 1), gate.acquired)
        // First released as failure, second as success.
        assertEquals(listOf(0 to false, 1 to true), gate.released)
    }

    @Test
    fun `non-transient failure is not retried and releases as failure`() = runBlocking {
        val gate = FakeGate()
        var calls = 0
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                gate.withRetry(ConsumerType.UI_OPERATION, "smb://nas:445") {
                    calls++
                    throw IllegalStateException("permanent")
                }
            }
        }
        assertEquals(1, calls)
        assertEquals(listOf(0), gate.acquired)
        assertEquals(listOf(0 to false), gate.released)
    }

    @Test
    fun `transient failure twice rethrows second error and releases both as failure`() = runBlocking {
        val gate = FakeGate()
        assertThrows(SocketTimeoutException::class.java) {
            runBlocking {
                gate.withRetry(ConsumerType.UI_PLAYER, "smb://nas:445") {
                    throw SocketTimeoutException("still transient")
                }
            }
        }
        assertEquals(listOf(0, 1), gate.acquired)
        assertEquals(listOf(0 to false, 1 to false), gate.released)
    }

    @Test
    fun `cancellation on first attempt releases as failure and is not retried`() = runBlocking {
        val gate = FakeGate()
        assertThrows(CancellationException::class.java) {
            runBlocking {
                gate.withRetry(ConsumerType.UI_PLAYER, "smb://nas:445") {
                    throw CancellationException("cancelled")
                }
            }
        }
        assertEquals(listOf(0), gate.acquired)
        assertEquals(listOf(0 to false), gate.released)
    }
}
