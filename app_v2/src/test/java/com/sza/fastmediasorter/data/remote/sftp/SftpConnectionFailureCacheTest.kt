package com.sza.fastmediasorter.data.remote.sftp

import com.jcraft.jsch.JSchException
import com.jcraft.jsch.SftpException
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SftpConnectionFailureCacheTest {

    @Test
    fun `returns every qualifying socket cause during cooldown`() {
        val causes = listOf(
            SocketTimeoutException("timeout"),
            ConnectException("refused"),
            NoRouteToHostException("no route"),
            UnknownHostException("unknown"),
        )

        causes.forEach { cause ->
            val cache = SftpConnectionFailureCache()
            val failure = JSchException("timeout: socket is not established", cause)

            cache.record(info(), failure)

            assertSame(cause.toString(), failure, cache.recentFailure(info()))
        }
    }

    @Test
    fun `expires the record once the cooldown elapses`() {
        var now = 0L
        val cache = SftpConnectionFailureCache { now }
        val failure = JSchException("timeout", SocketTimeoutException())

        cache.record(info(), failure)
        now = COOLDOWN_MS - 1
        assertSame(failure, cache.recentFailure(info()))

        now = COOLDOWN_MS
        assertNull(cache.recentFailure(info()))
    }

    @Test
    fun `does not cache auth host key or protocol failures`() {
        val cache = SftpConnectionFailureCache()

        cache.record(info(), JSchException("Auth fail"))
        cache.record(info(), HostKeyMismatchException(expected = "sha256:aaa", actual = "sha256:bbb"))
        cache.record(info(), SftpException(NO_SUCH_FILE, "failure"))

        assertNull(cache.recentFailure(info()))
    }

    @Test
    fun `does not expose a failure across host port user or fingerprint`() {
        val cache = SftpConnectionFailureCache()
        val failure = SocketTimeoutException()
        val recorded = info()

        cache.record(recorded, failure)

        assertSame(failure, cache.recentFailure(recorded))
        assertNull(cache.recentFailure(info(host = "other-host")))
        assertNull(cache.recentFailure(info(port = 2222)))
        assertNull(cache.recentFailure(info(username = "other-user")))
        assertNull(cache.recentFailure(info(fingerprint = "sha256:other")))
    }

    @Test
    fun `clear removes only the matching endpoint`() {
        val cache = SftpConnectionFailureCache()
        val first = info(host = "one")
        val second = info(host = "two")
        val failure = SocketTimeoutException()

        cache.record(first, failure)
        cache.record(second, failure)
        cache.clear(second)

        assertSame(failure, cache.recentFailure(first))
        assertNull(cache.recentFailure(second))
    }

    @Test
    fun `clearAll removes every cached failure`() {
        val cache = SftpConnectionFailureCache()

        cache.record(info(host = "one"), SocketTimeoutException())
        cache.record(info(host = "two"), ConnectException())
        cache.clearAll()

        assertNull(cache.recentFailure(info(host = "one")))
        assertNull(cache.recentFailure(info(host = "two")))
    }

    @Test
    fun `secrets never reach the endpoint key`() {
        val cache = SftpConnectionFailureCache()
        val failure = SocketTimeoutException()

        cache.record(info(password = "first-secret"), failure)

        // Password and key material are credentials, not endpoint identity: a corrected password
        // must not be able to hide behind a still-valid unreachable-host record.
        assertSame(failure, cache.recentFailure(info(password = "second-secret")))
    }

    private fun info(
        host: String = "host",
        port: Int = 22,
        username: String = "user",
        password: String = "",
        fingerprint: String? = null,
    ) = SftpClient.SftpConnectionInfo(
        host = host,
        port = port,
        username = username,
        password = password,
        expectedFingerprint = fingerprint,
    )

    private companion object {
        private const val COOLDOWN_MS = 15_000L
        private const val NO_SUCH_FILE = 2
    }
}
