package com.sza.fastmediasorter.domain.streams.facets

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/**
 * STREAM-BANK rule 11 / 2.4 SB-A13: publishing replaces the bank asset delete-then-upload, so an HTTP
 * 404 for a short window is expected and is retried rather than surfaced. Only a 404 is retried; any
 * other status is a real answer and returns at once.
 *
 * The retry is cancellable: cancellation is checked before every attempt and the wait is a suspending
 * [delay], so leaving the screen or cancelling the import stops further requests. The attempt must
 * close its own response before returning, otherwise the connection would be held across the wait.
 *
 * Compiled into the watch module from this tree, so it imports nothing from Android, Hilt or the app.
 */
object StreamBankFetchRetry {

    /** One attempt's answer: the fetched value, or the HTTP status that refused it. */
    sealed interface Attempt<out T> {
        data class Done<out T>(val value: T) : Attempt<T>
        data class HttpStatus(val code: Int) : Attempt<Nothing>
    }

    const val HTTP_NOT_FOUND = 404

    // The window lasts as long as one asset upload; four attempts ten seconds apart cover it without
    // holding an import open for longer than a user would wait on a refresh.
    const val MAX_ATTEMPTS = 4
    const val RETRY_DELAY_MILLIS = 10_000L

    /**
     * Runs [attempt] until it returns something other than a 404, or [maxAttempts] attempts were made,
     * and returns the last outcome. [onRetry] receives the number of the attempt that just got a 404.
     */
    suspend fun <T> fetch(
        maxAttempts: Int = MAX_ATTEMPTS,
        delayMillis: Long = RETRY_DELAY_MILLIS,
        onRetry: (attemptNumber: Int) -> Unit = {},
        attempt: suspend () -> Attempt<T>,
    ): Attempt<T> {
        var attemptNumber = 1
        while (true) {
            currentCoroutineContext().ensureActive()
            val outcome = attempt()
            val notFound = outcome is Attempt.HttpStatus && outcome.code == HTTP_NOT_FOUND
            if (!notFound || attemptNumber >= maxAttempts) return outcome
            onRetry(attemptNumber)
            delay(delayMillis)
            attemptNumber++
        }
    }
}
