package com.sza.fastmediasorter.core.playback.resilience

import com.sza.fastmediasorter.core.playback.RadioStreamBufferConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Characterization of the foreground-service audio ladder as `AudioPlaybackService.canRetryStream`,
 * `scheduleStreamRetry` and `giveUpStreamRetry` ran it before S1513 moved it. Every window, delay and
 * accepted code below was read out of that file, so a behaviour change during the move fails here
 * rather than on a live station at night.
 *
 * The connection is stamped at a non-zero instant throughout: both windows are differences against a
 * stamp the policy took itself, and a test starting at zero would pass just as well against a policy
 * that read the raw clock.
 */
class StreamServiceRetryPolicyTest {

    @Test
    fun `the two windows, the base delay and the cap survive the move unchanged`() {
        assertEquals(CONNECT_WINDOW_MS, StreamServiceRetryPolicy.CONNECT_WINDOW_MS)
        assertEquals(RECONNECT_WINDOW_MS, StreamServiceRetryPolicy.RECONNECT_WINDOW_MS)
        assertEquals(BASE_RETRY_DELAY_MS, StreamServiceRetryPolicy.BASE_RETRY_DELAY_MS)
        assertEquals(MAX_RETRY_DELAY_MS, StreamServiceRetryPolicy.MAX_RETRY_DELAY_MS)
        // The service read all three of these off RadioStreamBufferConfig, so the policy naming its
        // own must not let the two copies drift apart later.
        assertEquals(RadioStreamBufferConfig.DIALOG_TIMEOUT_MS, CONNECT_WINDOW_MS)
        assertEquals(RadioStreamBufferConfig.BASE_RETRY_DELAY_MS, BASE_RETRY_DELAY_MS)
        assertEquals(RadioStreamBufferConfig.MAX_RETRY_DELAY_MS, MAX_RETRY_DELAY_MS)
    }

    /**
     * The delay is read off the attempt counter and the counter is raised afterwards, so the first
     * failure of a connection waits the base delay and the third one already sits on the plateau -
     * the shift is clamped at 2, not the result.
     */
    @Test
    fun `a fresh connection retries on the doubling ladder and plateaus at 8000`() {
        val policy = startedPolicy()

        assertEquals(retryAfter(DELAY_1_MS), policy.onFailure(ioFailure(), youngConnectionMs()))
        assertEquals(retryAfter(DELAY_2_MS), policy.onFailure(ioFailure(), youngConnectionMs()))
        assertEquals(retryAfter(DELAY_3_MS), policy.onFailure(ioFailure(), youngConnectionMs()))
        assertEquals(retryAfter(DELAY_3_MS), policy.onFailure(ioFailure(), youngConnectionMs()))
    }

    @Test
    fun `a stream that never played is offered no retry once the connect window has closed`() {
        val policy = startedPolicy()

        assertEquals(IGNORE, policy.onFailure(ioFailure(), CONNECT_WINDOW_CLOSES_AT_MS))
    }

    @Test
    fun `a stream that already played is still offered a retry long after that window closed`() {
        val policy = startedPolicy(playedBefore = true)

        assertEquals(retryAfter(DELAY_1_MS), policy.onFailure(ioFailure(), FAR_FUTURE_MS))
    }

    /**
     * The classifier answers `TRANSIENT_NETWORK` for the timeout code because the video site retries
     * it, but this site only ever accepted the flat `IO_UNSPECIFIED..IO_READ_POSITION_OUT_OF_RANGE`
     * range. Accepting the class alone would silently widen the set S1513 §2 freezes.
     */
    @Test
    fun `the timeout code outside the flat IO range is still ignored`() {
        val policy = startedPolicy()

        assertEquals(IGNORE, policy.onFailure(failureOf(StreamFailureClass.ERROR_CODE_TIMEOUT), youngConnectionMs()))
        assertEquals(
            IGNORE,
            policy.onFailure(failureOf(StreamFailureClass.ERROR_CODE_BEHIND_LIVE_WINDOW), youngConnectionMs()),
        )
    }

    /**
     * The trap Phase 01 surfaced. `ERROR_CODE_IO_BAD_HTTP_STATUS` sits inside the range this site
     * accepts, and the site never walked the cause chain for a response code, so it retried the code
     * unconditionally. The shared classifier sends the same failure to its HTTP branch and, with no
     * status to judge, answers `NOT_RETRYABLE` - mapping that to "give up" would stop retrying a
     * failure this site retries today.
     */
    @Test
    fun `a bad HTTP status with no response code observed is retried exactly as before`() {
        val policy = startedPolicy()
        val failure = badHttpStatus(httpStatus = null)

        assertEquals(StreamFailureClass.NOT_RETRYABLE, failure.failureClass)
        assertEquals(retryAfter(DELAY_1_MS), policy.onFailure(failure, youngConnectionMs()))
    }

    /** Same code, an observed status the classifier rejects: this site never looked, so it retries. */
    @Test
    fun `a bad HTTP status the classifier rejects is retried too`() {
        val policy = startedPolicy()
        val failure = badHttpStatus(httpStatus = HTTP_NOT_FOUND)

        assertEquals(StreamFailureClass.NOT_RETRYABLE, failure.failureClass)
        assertEquals(retryAfter(DELAY_1_MS), policy.onFailure(failure, youngConnectionMs()))
    }

    @Test
    fun `a retryable HTTP status takes the same ladder`() {
        val policy = startedPolicy()
        val failure = badHttpStatus(httpStatus = HTTP_SERVICE_UNAVAILABLE)

        assertEquals(StreamFailureClass.RETRYABLE_HTTP_STATUS, failure.failureClass)
        assertEquals(retryAfter(DELAY_1_MS), policy.onFailure(failure, youngConnectionMs()))
    }

    @Test
    fun `the fire-time gate gives up 15000 ms after connect while the stream never played`() {
        val policy = startedPolicy()
        policy.onFailure(ioFailure(), youngConnectionMs())

        assertEquals(IGNORE, policy.onRetryDue(CONNECT_WINDOW_CLOSES_AT_MS - 1))
        assertEquals(GIVE_UP, policy.onRetryDue(CONNECT_WINDOW_CLOSES_AT_MS))
    }

    @Test
    fun `the fire-time gate gives up 300000 ms after the first retry once the stream has played`() {
        val policy = startedPolicy(playedBefore = true)
        policy.onFailure(ioFailure(), FIRST_RETRY_AT_MS)

        assertEquals(IGNORE, policy.onRetryDue(RECONNECT_WINDOW_CLOSES_AT_MS - 1))
        assertEquals(GIVE_UP, policy.onRetryDue(RECONNECT_WINDOW_CLOSES_AT_MS))
    }

    /** The stamp is taken on the first schedule since the last reset and never refreshed. */
    @Test
    fun `the reconnect window is stamped by the first retry, not by the latest one`() {
        val policy = startedPolicy(playedBefore = true)
        policy.onFailure(ioFailure(), FIRST_RETRY_AT_MS)
        policy.onFailure(ioFailure(), FIRST_RETRY_AT_MS + LATER_RETRY_OFFSET_MS)

        assertEquals(GIVE_UP, policy.onRetryDue(RECONNECT_WINDOW_CLOSES_AT_MS))
    }

    /**
     * The two life stages are judged by two different stamps. A stream that already played but has
     * scheduled no retry yet is outside both windows, and collapsing the pair into one clock would
     * have it give up the moment the 15 s connect window elapsed.
     */
    @Test
    fun `a played stream with no retry stamped is never judged by the connect window`() {
        val policy = startedPolicy(playedBefore = true)

        assertEquals(IGNORE, policy.onRetryDue(FAR_FUTURE_MS))
    }

    /** S1291: the user paused, so the pending retry is dropped and its window starts over. */
    @Test
    fun `a dropped retry unstamps the reconnect window`() {
        val policy = startedPolicy(playedBefore = true)
        policy.onFailure(ioFailure(), FIRST_RETRY_AT_MS)
        policy.onRetryDropped()

        assertEquals(IGNORE, policy.onRetryDue(RECONNECT_WINDOW_CLOSES_AT_MS))
    }

    @Test
    fun `a ready state refills the delay ladder and unstamps the reconnect window`() {
        val policy = startedPolicy(playedBefore = true)
        repeat(LADDER_LENGTH) { policy.onFailure(ioFailure(), FIRST_RETRY_AT_MS) }
        policy.onStreamReady()

        assertEquals(IGNORE, policy.onRetryDue(RECONNECT_WINDOW_CLOSES_AT_MS))
        assertEquals(retryAfter(DELAY_1_MS), policy.onFailure(ioFailure(), FIRST_RETRY_AT_MS))
    }

    /** The catalog lookup that seeds the played flag lands after the reset, exactly as it does live. */
    private fun startedPolicy(playedBefore: Boolean = false): StreamServiceRetryPolicy {
        val policy = StreamServiceRetryPolicy()
        policy.onStreamChanged(CONNECTED_AT_MS)
        if (playedBefore) {
            policy.onKnownPlayedBefore(true)
        }
        return policy
    }

    private fun youngConnectionMs(): Long = CONNECTED_AT_MS + YOUNG_CONNECTION_AGE_MS

    private fun ioFailure(): StreamAudioFailure =
        failureOf(StreamFailureClass.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)

    private fun badHttpStatus(httpStatus: Int?): StreamAudioFailure =
        failureOf(StreamFailureClass.ERROR_CODE_IO_BAD_HTTP_STATUS, httpStatus)

    private fun failureOf(errorCode: Int, httpStatus: Int? = null): StreamAudioFailure =
        StreamAudioFailure(StreamFailureClass.classify(errorCode, httpStatus), errorCode)

    private fun retryAfter(delayMs: Long): StreamServiceRetryDecision =
        StreamServiceRetryDecision.RetryAfter(delayMs)

    private companion object {
        val IGNORE = StreamServiceRetryDecision.Ignore
        val GIVE_UP = StreamServiceRetryDecision.GiveUp

        const val CONNECT_WINDOW_MS = 15_000L
        const val RECONNECT_WINDOW_MS = 300_000L
        const val BASE_RETRY_DELAY_MS = 2_000L
        const val MAX_RETRY_DELAY_MS = 8_000L

        const val DELAY_1_MS = 2_000L
        const val DELAY_2_MS = 4_000L
        const val DELAY_3_MS = 8_000L
        const val LADDER_LENGTH = 3

        /** Non-zero so a policy reading a raw clock instead of the stamp could not pass. */
        const val CONNECTED_AT_MS = 10_000L
        const val YOUNG_CONNECTION_AGE_MS = 1_000L
        const val CONNECT_WINDOW_CLOSES_AT_MS = CONNECTED_AT_MS + CONNECT_WINDOW_MS
        const val FIRST_RETRY_AT_MS = CONNECTED_AT_MS + 5_000L
        const val LATER_RETRY_OFFSET_MS = 90_000L
        const val RECONNECT_WINDOW_CLOSES_AT_MS = FIRST_RETRY_AT_MS + RECONNECT_WINDOW_MS
        const val FAR_FUTURE_MS = 30L * 24 * 60 * 60 * 1000

        const val HTTP_NOT_FOUND = 404
        const val HTTP_SERVICE_UNAVAILABLE = 503
    }
}
