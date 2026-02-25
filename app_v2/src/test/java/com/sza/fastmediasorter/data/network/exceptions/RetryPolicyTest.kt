package com.sza.fastmediasorter.data.network.exceptions

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetryPolicyTest {

    // ── Success on first attempt ─────────────────────────────────────────────

    @Test
    fun `withRetry returns result immediately on success`() = runTest {
        var callCount = 0
        val result = withRetry(RetryPolicy(maxAttempts = 3)) {
            callCount++
            "success"
        }
        assertEquals("success", result)
        assertEquals(1, callCount)
    }

    // ── Retry on transient error ─────────────────────────────────────────────

    @Test
    fun `withRetry retries on transient error and succeeds`() = runTest {
        var callCount = 0
        val result = withRetry(RetryPolicy(maxAttempts = 3, initialDelayMs = 0)) {
            callCount++
            if (callCount < 3) throw NetworkTimeoutException("timeout")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, callCount)
    }

    // ── Non-retryable error throws immediately ────────────────────────────────

    @Test
    fun `withRetry does not retry on non-retryable error`() = runTest {
        var callCount = 0
        try {
            withRetry(RetryPolicy(maxAttempts = 3, initialDelayMs = 0)) {
                callCount++
                throw NetworkAccessDeniedException("forbidden")
            }
            fail("expected exception")
        } catch (e: NetworkAccessDeniedException) {
            assertEquals(1, callCount)
        }
    }

    // ── Exhausted retries throw last classified exception ─────────────────────

    @Test
    fun `withRetry throws NetworkException after exhausting attempts`() = runTest {
        var callCount = 0
        try {
            withRetry(RetryPolicy(maxAttempts = 2, initialDelayMs = 0)) {
                callCount++
                throw NetworkTimeoutException("timeout")
            }
            fail("expected exception")
        } catch (e: NetworkTimeoutException) {
            assertEquals(2, callCount)
        }
    }

    // ── Raw exception is classified before rethrowing ────────────────────────

    @Test
    fun `withRetry classifies raw exception before rethrowing`() = runTest {
        try {
            withRetry(RetryPolicy(maxAttempts = 1, initialDelayMs = 0)) {
                throw java.net.SocketTimeoutException("raw timeout")
            }
            fail("expected exception")
        } catch (e: NetworkTimeoutException) {
            // SocketTimeoutException was classified → NetworkTimeoutException
        }
    }

    // ── NONE preset executes once ─────────────────────────────────────────────

    @Test
    fun `RetryPolicy NONE executes exactly once`() = runTest {
        var callCount = 0
        try {
            withRetry(RetryPolicy.NONE) {
                callCount++
                throw NetworkConnectionLostException()
            }
        } catch (e: NetworkException) {
            // expected
        }
        assertEquals(1, callCount)
    }

    // ── Rate limit retried (transient) ───────────────────────────────────────

    @Test
    fun `withRetry retries NetworkRateLimitException as transient`() = runTest {
        var callCount = 0
        val result = withRetry(RetryPolicy(maxAttempts = 3, initialDelayMs = 0)) {
            callCount++
            if (callCount < 2) throw NetworkRateLimitException(retryAfterSeconds = 0)
            "done"
        }
        assertEquals("done", result)
        assertEquals(2, callCount)
    }

    // ── Custom retryOn predicate ──────────────────────────────────────────────

    @Test
    fun `withRetry uses custom retryOn predicate`() = runTest {
        val policy = RetryPolicy(
            maxAttempts = 3,
            initialDelayMs = 0,
            retryOn = { it is NetworkAccessDeniedException } // normally non-retryable
        )
        var callCount = 0
        val result = withRetry(policy) {
            callCount++
            if (callCount < 3) throw NetworkAccessDeniedException()
            "custom"
        }
        assertEquals("custom", result)
        assertEquals(3, callCount)
    }

    // ── init validation ──────────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `RetryPolicy maxAttempts less than 1 throws IllegalArgumentException`() {
        RetryPolicy(maxAttempts = 0)
    }

    // ── Server error retried ──────────────────────────────────────────────────

    @Test
    fun `withRetry retries NetworkServerErrorException as transient`() = runTest {
        var callCount = 0
        val result = withRetry(RetryPolicy(maxAttempts = 3, initialDelayMs = 0)) {
            callCount++
            if (callCount < 2) throw NetworkServerErrorException(500)
            "recovered"
        }
        assertEquals("recovered", result)
        assertEquals(2, callCount)
    }
}
