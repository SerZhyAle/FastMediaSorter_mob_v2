package com.sza.fastmediasorter.data.network.exceptions

import kotlinx.coroutines.delay
import timber.log.Timber
import kotlin.math.min
import kotlin.math.pow

/**
 * Configurable retry policy with exponential back-off.
 *
 * @param maxAttempts Total attempts including the first one (≥ 1).
 * @param initialDelayMs Delay before the first retry (milliseconds).
 * @param maxDelayMs Upper bound for any single delay.
 * @param backoffMultiplier Exponential factor applied to each successive delay.
 * @param retryOn Predicate that decides whether a particular throwable should trigger a retry.
 *                Defaults to [NetworkErrorClassifier.isTransient].
 */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 1_000,
    val maxDelayMs: Long = 15_000,
    val backoffMultiplier: Double = 2.0,
    val retryOn: (Throwable) -> Boolean = { NetworkErrorClassifier.isTransient(it) }
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1" }
    }

    companion object {
        /** No retries — execute once. */
        val NONE = RetryPolicy(maxAttempts = 1)

        /** Quick retry for interactive UI (2 extra attempts, short delays). */
        val INTERACTIVE = RetryPolicy(maxAttempts = 3, initialDelayMs = 500, maxDelayMs = 3_000)

        /** Background retry for file transfers (5 attempts, longer delays). */
        val FILE_TRANSFER = RetryPolicy(maxAttempts = 5, initialDelayMs = 2_000, maxDelayMs = 30_000)
    }
}

/**
 * Execute [block] with automatic retry according to the given [RetryPolicy].
 *
 * The function classifies every caught exception via [NetworkErrorClassifier]
 * before checking the retry predicate. If retries are exhausted, the *classified*
 * exception is rethrown so callers always receive a typed [NetworkException].
 *
 * ```
 * val result = withRetry(RetryPolicy.INTERACTIVE) {
 *     smbClient.listFiles(path)
 * }
 * ```
 */
suspend fun <T> withRetry(
    policy: RetryPolicy = RetryPolicy(),
    tag: String = "withRetry",
    block: suspend (attempt: Int) -> T
): T {
    var lastException: Throwable? = null

    for (attempt in 1..policy.maxAttempts) {
        try {
            return block(attempt)
        } catch (e: Throwable) {
            val classified = NetworkErrorClassifier.classify(e)
            lastException = classified

            if (attempt >= policy.maxAttempts || !policy.retryOn(classified)) {
                Timber.w(classified, "$tag: Failed permanently on attempt $attempt/${policy.maxAttempts}")
                throw classified
            }

            val delayMs = min(
                policy.maxDelayMs,
                (policy.initialDelayMs * policy.backoffMultiplier.pow((attempt - 1).toDouble())).toLong()
            )
            Timber.d("$tag: Attempt $attempt failed (${classified.javaClass.simpleName}), retrying in ${delayMs}ms")
            delay(delayMs)
        }
    }

    // Should never reach here, but Kotlin needs exhaustive return
    throw lastException ?: IllegalStateException("$tag: No attempts executed")
}
