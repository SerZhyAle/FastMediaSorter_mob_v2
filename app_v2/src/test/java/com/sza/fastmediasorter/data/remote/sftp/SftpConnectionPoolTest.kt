package com.sza.fastmediasorter.data.remote.sftp

import com.jcraft.jsch.JSchException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class SftpConnectionPoolTest {

    @Test
    fun `periodic sweep can start once and stop cleanly`() {
        val pool = SftpConnectionPool()

        assertFalse(pool.isPeriodicSweepActive())

        pool.startPeriodicSweepForTest()
        pool.startPeriodicSweepForTest()

        assertTrue(pool.isPeriodicSweepActive())

        pool.stopPeriodicSweepForTest()

        assertFalse(pool.isPeriodicSweepActive())
    }

    @Test
    fun `qualifying creation failure short-circuits the next handshake`() {
        val pool = SftpConnectionPool()
        val failure = unreachable()

        pool.applyHandshakeOutcomeForTest(info(), failure)

        assertSame(failure, guardFailure(pool, info()))
    }

    @Test
    fun `guard stays silent for a distinct endpoint`() {
        val pool = SftpConnectionPool()

        pool.applyHandshakeOutcomeForTest(info(), unreachable())

        assertPasses(pool, info(host = "other-host"))
    }

    @Test
    fun `explicit invalidation permits a new handshake`() = runBlocking {
        val pool = SftpConnectionPool()

        pool.applyHandshakeOutcomeForTest(info(), unreachable())
        pool.invalidate(info())

        assertPasses(pool, info())
    }

    @Test
    fun `global reset clears failures for every endpoint`() = runBlocking {
        val pool = SftpConnectionPool()
        val first = info(host = "one")
        val second = info(host = "two")

        pool.applyHandshakeOutcomeForTest(first, unreachable())
        pool.applyHandshakeOutcomeForTest(second, unreachable())
        pool.disconnectAll()

        assertPasses(pool, first)
        assertPasses(pool, second)
    }

    @Test
    fun `pool delegates eligibility to SftpConnectionFailureCache`() {
        val pool = SftpConnectionPool()

        // An auth or host-key refusal proves nothing about reachability and must stay retryable,
        // so the pool arms its guard only for what the cache accepts.
        pool.applyHandshakeOutcomeForTest(info(), JSchException("Auth fail"))

        assertPasses(pool, info())
    }

    @Test
    fun `successful handshake clears the prior failure`() {
        val pool = SftpConnectionPool()

        pool.applyHandshakeOutcomeForTest(info(), unreachable())
        pool.applyHandshakeOutcomeForTest(info(), null)

        assertPasses(pool, info())
    }

    /** Returns the throwable the guard rethrows, failing the test if it lets the handshake through. */
    private fun guardFailure(pool: SftpConnectionPool, info: SftpClient.SftpConnectionInfo): Throwable =
        runCatching { pool.failFastIfRecentlyUnreachableForTest(info) }.exceptionOrNull()
            ?: throw AssertionError("expected the recent-failure guard to short-circuit ${info.host}")

    private fun assertPasses(pool: SftpConnectionPool, info: SftpClient.SftpConnectionInfo) {
        pool.failFastIfRecentlyUnreachableForTest(info)
    }

    private fun unreachable(): Throwable =
        JSchException("timeout: socket is not established", SocketTimeoutException("failed to connect"))

    private fun info(host: String = "host") =
        SftpClient.SftpConnectionInfo(host = host, username = "user")
}
