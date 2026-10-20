package com.sza.fastmediasorter.domain.streams.facets

import com.sza.fastmediasorter.domain.streams.facets.StreamBankFetchRetry.Attempt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The publish-window retry (STREAM-BANK 2.4 SB-A13). The same class runs in the watch module against the
 * watch's own compiled copy of the shared source.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StreamBankFetchRetryTest {

    @Test
    fun `a 404 followed by success returns the fetched value`() = runTest {
        val answers = ArrayDeque(listOf<Attempt<String>>(Attempt.HttpStatus(NOT_FOUND), Attempt.Done(BANK)))
        var calls = 0

        val outcome = StreamBankFetchRetry.fetch {
            calls++
            answers.removeFirst()
        }

        assertEquals(Attempt.Done(BANK), outcome)
        assertEquals(2, calls)
    }

    @Test
    fun `a 404 on every attempt gives up after the bound`() = runTest {
        var calls = 0

        val outcome = StreamBankFetchRetry.fetch<String> {
            calls++
            Attempt.HttpStatus(NOT_FOUND)
        }

        assertEquals(Attempt.HttpStatus(NOT_FOUND), outcome)
        assertEquals(StreamBankFetchRetry.MAX_ATTEMPTS, calls)
    }

    @Test
    fun `a status other than 404 is not retried`() = runTest {
        var calls = 0

        val outcome = StreamBankFetchRetry.fetch<String> {
            calls++
            Attempt.HttpStatus(SERVER_ERROR)
        }

        assertEquals(Attempt.HttpStatus(SERVER_ERROR), outcome)
        assertEquals(1, calls)
    }

    @Test
    fun `cancellation during the wait stops further attempts`() = runTest {
        var calls = 0
        val fetch = async {
            StreamBankFetchRetry.fetch<String> {
                calls++
                Attempt.HttpStatus(NOT_FOUND)
            }
        }
        runCurrent()
        assertEquals(1, calls)

        fetch.cancel()
        advanceTimeBy(StreamBankFetchRetry.RETRY_DELAY_MILLIS * StreamBankFetchRetry.MAX_ATTEMPTS)
        runCurrent()

        assertEquals(1, calls)
        assertTrue(fetch.isCancelled)
        val thrown = runCatching { fetch.await() }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
    }

    private companion object {
        const val NOT_FOUND = 404
        const val SERVER_ERROR = 500
        const val BANK = "bank"
    }
}
