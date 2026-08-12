package com.sza.fastmediasorter.core.playback.resilience

import com.sza.fastmediasorter.core.playback.RadioStreamBufferConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization of the inline mini-player ladder as `StreamInlineAudioManager.canRetry`,
 * `canContinueRetrying` and `scheduleLocalRetry` ran it before S1513 moved it.
 *
 * The site shares the service's delay arithmetic and accepted code range but not its gate: one gate
 * shape serves both the schedule and the fire moment, and a stream that has played once is bounded
 * by nothing at all. Strategic §1 lists that asymmetry as a symptom and §2 forbids fixing it here,
 * so the unbounded case below asserts the current truth, not what a reader would expect.
 */
class StreamInlineRetryPolicyTest {

    @Test
    fun `the connect window, the base delay and the cap survive the move unchanged`() {
        assertEquals(CONNECT_WINDOW_MS, StreamInlineRetryPolicy.CONNECT_WINDOW_MS)
        assertEquals(BASE_RETRY_DELAY_MS, StreamInlineRetryPolicy.BASE_RETRY_DELAY_MS)
        assertEquals(MAX_RETRY_DELAY_MS, StreamInlineRetryPolicy.MAX_RETRY_DELAY_MS)
        // Both audio sites read these off RadioStreamBufferConfig, literally the same constants.
        assertEquals(RadioStreamBufferConfig.DIALOG_TIMEOUT_MS, CONNECT_WINDOW_MS)
        assertEquals(RadioStreamBufferConfig.BASE_RETRY_DELAY_MS, BASE_RETRY_DELAY_MS)
        assertEquals(RadioStreamBufferConfig.MAX_RETRY_DELAY_MS, MAX_RETRY_DELAY_MS)
    }

    /** The delay is read off the counter before the counter is raised, as at the service site. */
    @Test
    fun `a fresh connection retries on the doubling ladder and plateaus at 8000`() {
        val policy = startedPolicy()

        assertEquals(retryAfter(DELAY_1_MS), policy.onLocalFailure(youngConnectionMs()))
        assertEquals(retryAfter(DELAY_2_MS), policy.onLocalFailure(youngConnectionMs()))
        assertEquals(retryAfter(DELAY_3_MS), policy.onLocalFailure(youngConnectionMs()))
        assertEquals(retryAfter(DELAY_3_MS), policy.onLocalFailure(youngConnectionMs()))
    }

    /** A closed gate is an answer, not a silence: the manager stops and the mini-control comes down. */
    @Test
    fun `a stream that never played gives up once the connect window has closed`() {
        val policy = startedPolicy()

        assertEquals(GIVE_UP, policy.onLocalFailure(CONNECT_WINDOW_CLOSES_AT_MS))
        assertFalse(policy.mayReconnect(hasEverPlayed = false, nowMs = CONNECT_WINDOW_CLOSES_AT_MS))
    }

    /**
     * The asymmetry strategic §1 names: the service gives up on the same stream five minutes after
     * its first retry, this site never gives up at all. Thirty days of failures still answer with a
     * retry, and the only bound in sight is the delay plateau.
     */
    @Test
    fun `a stream that already played retries with no upper bound whatsoever`() {
        val policy = startedPolicy()

        repeat(UNBOUNDED_PROBE_ATTEMPTS) {
            policy.onFailure(ioFailure(), hasEverPlayed = true, usingService = false, nowMs = FAR_FUTURE_MS)
        }

        assertEquals(
            retryAfter(DELAY_3_MS),
            policy.onFailure(ioFailure(), hasEverPlayed = true, usingService = false, nowMs = FAR_FUTURE_MS),
        )
        assertTrue(policy.mayReconnect(hasEverPlayed = true, nowMs = FAR_FUTURE_MS))
    }

    /**
     * S0577: with background playback on, the service owns this very stream and runs its own ladder,
     * so the manager schedules nothing. The routing answer must not spend an attempt either - the
     * counter belongs to the in-app player, which is not the one that failed.
     */
    @Test
    fun `a failure on a stream the service owns is not this site's to retry`() {
        val policy = startedPolicy()

        assertEquals(
            OWNED_BY_SERVICE,
            policy.onFailure(ioFailure(), hasEverPlayed = false, usingService = true, nowMs = youngConnectionMs()),
        )
        assertEquals(retryAfter(DELAY_1_MS), policy.onLocalFailure(youngConnectionMs()))
    }

    /** A closed gate answers give-up before the routing flag is even consulted, as it did live. */
    @Test
    fun `a service-owned failure past the connect window still gives up`() {
        val policy = startedPolicy()

        assertEquals(
            GIVE_UP,
            policy.onFailure(
                ioFailure(),
                hasEverPlayed = false,
                usingService = true,
                nowMs = CONNECT_WINDOW_CLOSES_AT_MS,
            ),
        )
    }

    @Test
    fun `the timeout code outside the flat IO range gives up instead of retrying`() {
        val policy = startedPolicy()

        assertEquals(GIVE_UP, policy.onLocalFailure(youngConnectionMs(), StreamFailureClass.ERROR_CODE_TIMEOUT))
    }

    /**
     * The trap Phase 01 surfaced, at this site too: the code sits inside the accepted range and the
     * manager never read a response code, so `NOT_RETRYABLE` must not become a give-up here.
     */
    @Test
    fun `a bad HTTP status with no response code observed is retried exactly as before`() {
        val policy = startedPolicy()
        val failure = badHttpStatus(httpStatus = null)

        assertEquals(StreamFailureClass.NOT_RETRYABLE, failure.failureClass)
        assertEquals(
            retryAfter(DELAY_1_MS),
            policy.onFailure(failure, hasEverPlayed = false, usingService = false, nowMs = youngConnectionMs()),
        )
    }

    @Test
    fun `audible playback refills the delay ladder`() {
        val policy = startedPolicy()
        repeat(LADDER_LENGTH) { policy.onLocalFailure(youngConnectionMs()) }
        policy.onPlaybackHealthy()

        assertEquals(retryAfter(DELAY_1_MS), policy.onLocalFailure(youngConnectionMs()))
    }

    /**
     * `clearRecoveryState` zeroed the connection stamp rather than re-taking it, leaving a stream
     * that never played outside its own window. Verbatim: `play()` re-stamps before the next gate.
     */
    @Test
    fun `clearing the recovery state closes the connect window for a stream that never played`() {
        val policy = startedPolicy()
        policy.onCleared()

        assertFalse(policy.mayReconnect(hasEverPlayed = false, nowMs = youngConnectionMs()))
        assertTrue(policy.mayReconnect(hasEverPlayed = true, nowMs = youngConnectionMs()))
    }

    private fun startedPolicy(): StreamInlineRetryPolicy {
        val policy = StreamInlineRetryPolicy()
        policy.onStreamStarted(CONNECTED_AT_MS)
        return policy
    }

    private fun StreamInlineRetryPolicy.onLocalFailure(
        nowMs: Long,
        errorCode: Int = StreamFailureClass.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    ): StreamInlineRetryDecision = onFailure(
        failure = failureOf(errorCode),
        hasEverPlayed = false,
        usingService = false,
        nowMs = nowMs,
    )

    private fun youngConnectionMs(): Long = CONNECTED_AT_MS + YOUNG_CONNECTION_AGE_MS

    private fun ioFailure(): StreamAudioFailure =
        failureOf(StreamFailureClass.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)

    private fun badHttpStatus(httpStatus: Int?): StreamAudioFailure =
        failureOf(StreamFailureClass.ERROR_CODE_IO_BAD_HTTP_STATUS, httpStatus)

    private fun failureOf(errorCode: Int, httpStatus: Int? = null): StreamAudioFailure =
        StreamAudioFailure(StreamFailureClass.classify(errorCode, httpStatus), errorCode)

    private fun retryAfter(delayMs: Long): StreamInlineRetryDecision =
        StreamInlineRetryDecision.RetryAfter(delayMs)

    private companion object {
        val GIVE_UP = StreamInlineRetryDecision.GiveUp
        val OWNED_BY_SERVICE = StreamInlineRetryDecision.OwnedByService

        const val CONNECT_WINDOW_MS = 15_000L
        const val BASE_RETRY_DELAY_MS = 2_000L
        const val MAX_RETRY_DELAY_MS = 8_000L

        const val DELAY_1_MS = 2_000L
        const val DELAY_2_MS = 4_000L
        const val DELAY_3_MS = 8_000L
        const val LADDER_LENGTH = 3

        /**
         * Non-zero so a policy reading a raw clock instead of the stamp could not pass, and further
         * past zero than the connect window itself: the gate measures `now - stamp`, so a cleared
         * stamp only closes the window when the clock has already run longer than the window. A
         * smaller base would put the test inside a window measured from boot and make a cleared
         * stamp look open, which no device ever sees - `elapsedRealtime()` passes 15 s during boot.
         */
        const val CONNECTED_AT_MS = 120_000L
        const val YOUNG_CONNECTION_AGE_MS = 1_000L
        const val CONNECT_WINDOW_CLOSES_AT_MS = CONNECTED_AT_MS + CONNECT_WINDOW_MS
        const val FAR_FUTURE_MS = 30L * 24 * 60 * 60 * 1000

        /** Far past any budget the other two ladders own - this one has none to exhaust. */
        const val UNBOUNDED_PROBE_ATTEMPTS = 100
    }
}
