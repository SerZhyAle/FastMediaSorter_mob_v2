package com.sza.fastmediasorter.data.network.exceptions

import com.jcraft.jsch.JSchException
import com.sza.fastmediasorter.data.remote.sftp.HostKeyMismatchException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class NetworkErrorClassifierTest {

    // ── Already-typed exceptions pass through ────────────────────────────────

    @Test
    fun `classify returns same instance when already NetworkException`() {
        val original = NetworkTimeoutException("already typed")
        val result = NetworkErrorClassifier.classify(original)
        assertTrue(result === original)
    }

    // ── Timeout variants ────────────────────────────────────────────────────

    @Test
    fun `classify SocketTimeoutException as NetworkTimeoutException`() {
        val result = NetworkErrorClassifier.classify(SocketTimeoutException("timeout"))
        assertTrue(result is NetworkTimeoutException)
    }

    @Test
    fun `classify UnknownHostException as NetworkTimeoutException`() {
        val result = NetworkErrorClassifier.classify(UnknownHostException("unknown host"))
        assertTrue(result is NetworkTimeoutException)
    }

    @Test
    fun `classify ConnectException as NetworkTimeoutException`() {
        val result = NetworkErrorClassifier.classify(ConnectException("refused"))
        assertTrue(result is NetworkTimeoutException)
    }

    @Test
    fun `classify NoRouteToHostException as NetworkTimeoutException`() {
        val result = NetworkErrorClassifier.classify(NoRouteToHostException("unreachable"))
        assertTrue(result is NetworkTimeoutException)
    }

    // ── SSL ─────────────────────────────────────────────────────────────────

    @Test
    fun `classify SSLException as NetworkAccessDeniedException`() {
        val result = NetworkErrorClassifier.classify(SSLException("ssl error"))
        assertTrue(result is NetworkAccessDeniedException)
    }

    // ── File not found ───────────────────────────────────────────────────────

    @Test
    fun `classify FileNotFoundException as NetworkFileNotFoundException`() {
        val result = NetworkErrorClassifier.classify(FileNotFoundException("missing"))
        assertTrue(result is NetworkFileNotFoundException)
    }

    // ── Message-based: access denied ─────────────────────────────────────────

    @Test
    fun `classify message containing 401 as NetworkAccessDeniedException`() {
        val result = NetworkErrorClassifier.classify(RuntimeException("HTTP 401: Unauthorized"))
        assertTrue(result is NetworkAccessDeniedException)
    }

    @Test
    fun `classify message containing 403 as NetworkAccessDeniedException`() {
        val result = NetworkErrorClassifier.classify(RuntimeException("HTTP 403: Forbidden"))
        assertTrue(result is NetworkAccessDeniedException)
    }

    @Test
    fun `classify message containing access denied as NetworkAccessDeniedException`() {
        val result = NetworkErrorClassifier.classify(RuntimeException("access denied to resource"))
        assertTrue(result is NetworkAccessDeniedException)
    }

    // ── Message-based: not found ─────────────────────────────────────────────

    @Test
    fun `classify message containing 404 as NetworkFileNotFoundException`() {
        val result = NetworkErrorClassifier.classify(RuntimeException("HTTP 404: Not Found"))
        assertTrue(result is NetworkFileNotFoundException)
    }

    @Test
    fun `classify message containing STATUS_OBJECT_NAME_NOT_FOUND as NetworkFileNotFoundException`() {
        val result = NetworkErrorClassifier.classify(RuntimeException("STATUS_OBJECT_NAME_NOT_FOUND"))
        assertTrue(result is NetworkFileNotFoundException)
    }

    @Test
    fun `classify SMB STATUS_BAD_NETWORK_NAME as NetworkFileNotFoundException`() {
        val result = NetworkErrorClassifier.classify(
            RuntimeException("STATUS_BAD_NETWORK_NAME (0xc00000cc): Could not connect to \\\\192.168.1.110\\medi")
        )
        assertTrue(result is NetworkFileNotFoundException)
    }

    // ── Rate limit ───────────────────────────────────────────────────────────

    @Test
    fun `classify message containing 429 as NetworkRateLimitException`() {
        val result = NetworkErrorClassifier.classify(RuntimeException("429 Too Many Requests"))
        assertTrue(result is NetworkRateLimitException)
    }

    @Test
    fun `classify message containing rate limit as NetworkRateLimitException`() {
        val result = NetworkErrorClassifier.classify(RuntimeException("rate limit exceeded"))
        assertTrue(result is NetworkRateLimitException)
    }

    @Test
    fun `classify rate limit with Retry-After hint extracts seconds`() {
        val result = NetworkErrorClassifier.classify(RuntimeException("rate limit Retry-After: 30"))
        assertTrue(result is NetworkRateLimitException)
        assertEquals(30L, (result as NetworkRateLimitException).retryAfterSeconds)
    }

    // ── Server errors ────────────────────────────────────────────────────────

    @Test
    fun `classify message containing 500 as NetworkServerErrorException`() {
        val result = NetworkErrorClassifier.classify(RuntimeException("server error 500"))
        assertTrue(result is NetworkServerErrorException)
    }

    @Test
    fun `classify message containing 503 as NetworkServerErrorException`() {
        val result = NetworkErrorClassifier.classify(RuntimeException("service unavailable 503"))
        assertTrue(result is NetworkServerErrorException)
    }

    @Test
    fun `classify HTTP status 503 embedded in message as NetworkServerErrorException`() {
        val result = NetworkErrorClassifier.classify(RuntimeException("Response code: 503"))
        assertTrue(result is NetworkServerErrorException)
        assertEquals(503, (result as NetworkServerErrorException).statusCode)
    }

    // ── Connection lost ──────────────────────────────────────────────────────

    @Test
    fun `classify message connection reset as NetworkConnectionLostException`() {
        val result = NetworkErrorClassifier.classify(RuntimeException("connection reset by peer"))
        assertTrue(result is NetworkConnectionLostException)
    }

    // ── Unsupported ──────────────────────────────────────────────────────────

    @Test
    fun `classify message unsupported as NetworkUnsupportedOperationException`() {
        val result = NetworkErrorClassifier.classify(RuntimeException("unsupported operation"))
        assertTrue(result is NetworkUnsupportedOperationException)
    }

    // ── Default case ─────────────────────────────────────────────────────────

    @Test
    fun `classify unknown exception as NetworkConnectionLostException`() {
        val result = NetworkErrorClassifier.classify(RuntimeException("something weird"))
        assertTrue(result is NetworkConnectionLostException)
    }

    // ── isTransient ──────────────────────────────────────────────────────────

    @Test
    fun `isTransient true for NetworkTimeoutException`() {
        assertTrue(NetworkErrorClassifier.isTransient(NetworkTimeoutException()))
    }

    @Test
    fun `isTransient true for NetworkConnectionLostException`() {
        assertTrue(NetworkErrorClassifier.isTransient(NetworkConnectionLostException()))
    }

    @Test
    fun `isTransient true for NetworkRateLimitException`() {
        assertTrue(NetworkErrorClassifier.isTransient(NetworkRateLimitException()))
    }

    @Test
    fun `isTransient true for NetworkServerErrorException`() {
        assertTrue(NetworkErrorClassifier.isTransient(NetworkServerErrorException()))
    }

    @Test
    fun `isTransient false for NetworkAccessDeniedException`() {
        assertFalse(NetworkErrorClassifier.isTransient(NetworkAccessDeniedException()))
    }

    @Test
    fun `isTransient false for NetworkFileNotFoundException`() {
        assertFalse(NetworkErrorClassifier.isTransient(NetworkFileNotFoundException()))
    }

    @Test
    fun `classify cause is preserved in result`() {
        val cause = RuntimeException("root cause")
        val result = NetworkErrorClassifier.classify(SocketTimeoutException("timeout").also { it.initCause(cause) })
        assertNotNull(result.cause)
    }

    // ── S1055: SSH host-key change / auth reject on the live path ─────────────

    @Test
    fun `classify JSch reject HostKey as NetworkHostKeyChangedException`() {
        val result = NetworkErrorClassifier.classify(JSchException("reject HostKey: 192.168.1.10"))
        assertTrue(result is NetworkHostKeyChangedException)
        assertFalse(NetworkErrorClassifier.isTransient(result))
    }

    @Test
    fun `classify HostKeyMismatchException message as NetworkHostKeyChangedException`() {
        val result = NetworkErrorClassifier.classify(
            HostKeyMismatchException(expected = "SHA256:aaa", actual = "SHA256:bbb")
        )
        assertTrue(result is NetworkHostKeyChangedException)
    }

    @Test
    fun `classify JSch Auth fail as NetworkAccessDeniedException`() {
        val result = NetworkErrorClassifier.classify(JSchException("Auth fail"))
        assertTrue(result is NetworkAccessDeniedException)
        assertFalse(NetworkErrorClassifier.isTransient(result))
    }

    @Test
    fun `classify JSch USERAUTH fail as NetworkAccessDeniedException`() {
        val result = NetworkErrorClassifier.classify(JSchException("USERAUTH fail"))
        assertTrue(result is NetworkAccessDeniedException)
    }

    @Test
    fun `classify wrapped Auth fail cause as NetworkAccessDeniedException`() {
        val wrapped = IOException("Failed to establish SFTP connection: Auth fail", JSchException("Auth fail"))
        val result = NetworkErrorClassifier.classify(wrapped)
        assertTrue(result is NetworkAccessDeniedException)
    }

    @Test
    fun `classify SFTP permission denied does not become NetworkHostKeyChangedException`() {
        val result = NetworkErrorClassifier.classify(RuntimeException("permission denied"))
        assertFalse(result is NetworkHostKeyChangedException)
    }

    @Test
    fun `isTransient false for NetworkHostKeyChangedException`() {
        assertFalse(NetworkErrorClassifier.isTransient(NetworkHostKeyChangedException()))
    }
}
