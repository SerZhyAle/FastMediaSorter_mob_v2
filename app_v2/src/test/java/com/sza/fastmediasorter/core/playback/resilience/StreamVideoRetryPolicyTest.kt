package com.sza.fastmediasorter.core.playback.resilience

import com.sza.fastmediasorter.core.playback.RadioStreamBufferConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Characterization of the video error ladder as `StreamPlaybackHelper.onPlayerError` ran it before
 * S1513 moved it. Every budget, step and cap below was read out of that file, so a behaviour change
 * during the move fails here rather than on a live channel.
 *
 * Strategic §11.3 makes this test the evidence that nothing shifted, which is why it asserts the
 * delay of every attempt in the ladder rather than only the first one and the plateau.
 */
class StreamVideoRetryPolicyTest {

    @Test
    fun `the two budgets, the two steps and the two caps survive the move unchanged`() {
        assertEquals(MAX_BEHIND_LIVE_RECOVERIES, StreamVideoRetryPolicy.MAX_BEHIND_LIVE_RECOVERIES)
        assertEquals(MAX_TRANSIENT_RETRIES, StreamVideoRetryPolicy.MAX_TRANSIENT_RETRIES)
        assertEquals(BEHIND_LIVE_STEP_DELAY_MS, StreamVideoRetryPolicy.BEHIND_LIVE_STEP_DELAY_MS)
        assertEquals(BEHIND_LIVE_MAX_DELAY_MS, StreamVideoRetryPolicy.BEHIND_LIVE_MAX_DELAY_MS)
        assertEquals(TRANSIENT_BASE_DELAY_MS, StreamVideoRetryPolicy.TRANSIENT_BASE_DELAY_MS)
        assertEquals(TRANSIENT_MAX_DELAY_MS, StreamVideoRetryPolicy.TRANSIENT_MAX_DELAY_MS)
        // The transient cap was written at the call site as the shared retry ceiling (S1512), so the
        // two must not be allowed to drift apart now that the policy names its own.
        assertEquals(RadioStreamBufferConfig.MAX_RETRY_DELAY_MS, TRANSIENT_MAX_DELAY_MS)
    }

    @Test
    fun `a behind live window failure re-anchors three times on a linear ladder`() {
        val policy = StreamVideoRetryPolicy()

        assertEquals(reAnchor(LINEAR_DELAY_1_MS, FIRST), policy.onFailure(behindLiveWindow()))
        assertEquals(reAnchor(LINEAR_DELAY_2_MS, SECOND), policy.onFailure(behindLiveWindow()))
        assertEquals(reAnchor(LINEAR_DELAY_3_MS, THIRD), policy.onFailure(behindLiveWindow()))
        assertEquals(SURFACE, policy.onFailure(behindLiveWindow()))
    }

    /**
     * RTSP carries no sliding window to re-anchor to, and the ladder never offered it the transient
     * branch either: the helper's recoverable-error check answered false for the behind-live-window
     * code, so an RTSP session surfaced the failure on the first one.
     */
    @Test
    fun `RTSP content takes no behind live window branch`() {
        val policy = StreamVideoRetryPolicy()

        assertEquals(SURFACE, policy.onFailure(behindLiveWindow(isRtsp = true)))
        assertEquals(SURFACE, policy.onFailure(behindLiveWindow(isRtsp = true)))
    }

    @Test
    fun `a transient failure retries four times on a doubling ladder capped at 8000`() {
        val policy = StreamVideoRetryPolicy()

        assertEquals(retry(DOUBLING_DELAY_1_MS, FIRST), policy.onFailure(transient()))
        assertEquals(retry(DOUBLING_DELAY_2_MS, SECOND), policy.onFailure(transient()))
        assertEquals(retry(DOUBLING_DELAY_3_MS, THIRD), policy.onFailure(transient()))
        assertEquals(retry(TRANSIENT_MAX_DELAY_MS, FOURTH), policy.onFailure(transient()))
    }

    @Test
    fun `the fifth transient failure surfaces the error instead of retrying`() {
        val policy = StreamVideoRetryPolicy()

        repeat(MAX_TRANSIENT_RETRIES) { policy.onFailure(transient()) }

        assertEquals(SURFACE, policy.onFailure(transient()))
    }

    /**
     * The counters were reset only on a confirmed `STATE_READY` and never on `STATE_BUFFERING`: a
     * stream flapping buffering-error must not refill its own quota and spin forever. Buffering has
     * no entry point on this policy at all, which is how that rule survives the move.
     */
    @Test
    fun `only a confirmed ready state refills both budgets`() {
        val policy = StreamVideoRetryPolicy()
        repeat(MAX_TRANSIENT_RETRIES) { policy.onFailure(transient()) }
        repeat(MAX_BEHIND_LIVE_RECOVERIES) { policy.onFailure(behindLiveWindow()) }

        assertEquals(SURFACE, policy.onFailure(transient()))
        assertEquals(SURFACE, policy.onFailure(behindLiveWindow()))
        policy.onReady()

        assertEquals(retry(DOUBLING_DELAY_1_MS, FIRST), policy.onFailure(transient()))
        assertEquals(reAnchor(LINEAR_DELAY_1_MS, FIRST), policy.onFailure(behindLiveWindow()))
    }

    @Test
    fun `the two budgets are independent of each other`() {
        val policy = StreamVideoRetryPolicy()

        repeat(MAX_BEHIND_LIVE_RECOVERIES) { policy.onFailure(behindLiveWindow()) }

        assertEquals(SURFACE, policy.onFailure(behindLiveWindow()))
        assertEquals(retry(DOUBLING_DELAY_1_MS, FIRST), policy.onFailure(transient()))
    }

    @Test
    fun `a retryable HTTP status shares the transient ladder and its budget`() {
        val policy = StreamVideoRetryPolicy()

        assertEquals(retry(DOUBLING_DELAY_1_MS, FIRST), policy.onFailure(retryableHttpStatus()))
        assertEquals(retry(DOUBLING_DELAY_2_MS, SECOND), policy.onFailure(transient()))
    }

    /**
     * A bad HTTP status whose `cause` chain carried no response code: the helper walked that chain,
     * found no `InvalidResponseCodeException` and answered "not recoverable", so the failure went
     * straight to the user. The classifier reaches the same answer through `NOT_RETRYABLE`.
     */
    @Test
    fun `a bad HTTP status with no response code in the chain surfaces at once`() {
        val policy = StreamVideoRetryPolicy()

        val decision = policy.onFailure(
            failure(StreamFailureClass.NOT_RETRYABLE, StreamFailureClass.ERROR_CODE_IO_BAD_HTTP_STATUS),
        )

        assertEquals(SURFACE, decision)
        assertEquals(retry(DOUBLING_DELAY_1_MS, FIRST), policy.onFailure(transient()))
    }

    /**
     * The IO codes this site never retried. The shared classifier answers `TRANSIENT_NETWORK` for
     * the whole Media3 IO block because the two audio sites accept it, but the video ladder listed
     * four codes and no more - §11.3 forbids widening that set while relocating it.
     */
    @Test
    fun `an IO code outside this site's four still surfaces`() {
        val policy = StreamVideoRetryPolicy()

        assertEquals(SURFACE, policy.onFailure(transient(FILE_NOT_FOUND_ERROR_CODE)))
        assertEquals(SURFACE, policy.onFailure(transient(NO_PERMISSION_ERROR_CODE)))
        assertEquals(SURFACE, policy.onFailure(transient(READ_POSITION_ERROR_CODE)))
    }

    @Test
    fun `all four unconditionally recoverable codes take the transient ladder`() {
        UNCONDITIONALLY_RECOVERABLE_CODES.forEach { errorCode ->
            val policy = StreamVideoRetryPolicy()

            assertEquals(retry(DOUBLING_DELAY_1_MS, FIRST), policy.onFailure(transient(errorCode)))
        }
    }

    private fun behindLiveWindow(isRtsp: Boolean = false): StreamVideoFailure = failure(
        failureClass = StreamFailureClass.BEHIND_LIVE_WINDOW,
        errorCode = StreamFailureClass.ERROR_CODE_BEHIND_LIVE_WINDOW,
        isRtsp = isRtsp,
    )

    private fun transient(errorCode: Int = StreamFailureClass.ERROR_CODE_TIMEOUT): StreamVideoFailure =
        failure(StreamFailureClass.TRANSIENT_NETWORK, errorCode)

    private fun retryableHttpStatus(): StreamVideoFailure = failure(
        failureClass = StreamFailureClass.RETRYABLE_HTTP_STATUS,
        errorCode = StreamFailureClass.ERROR_CODE_IO_BAD_HTTP_STATUS,
    )

    private fun failure(
        failureClass: StreamFailureClass,
        errorCode: Int,
        isRtsp: Boolean = false,
    ): StreamVideoFailure = StreamVideoFailure(failureClass, errorCode, isRtsp)

    private fun reAnchor(delayMs: Long, attempt: Int): StreamVideoRetryDecision =
        StreamVideoRetryDecision.ReAnchor(delayMs, attempt)

    private fun retry(delayMs: Long, attempt: Int): StreamVideoRetryDecision =
        StreamVideoRetryDecision.Retry(delayMs, attempt)

    private companion object {
        val SURFACE = StreamVideoRetryDecision.Surface

        val UNCONDITIONALLY_RECOVERABLE_CODES = listOf(
            StreamFailureClass.ERROR_CODE_TIMEOUT,
            StreamFailureClass.ERROR_CODE_IO_UNSPECIFIED,
            StreamFailureClass.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            StreamFailureClass.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        )

        const val MAX_BEHIND_LIVE_RECOVERIES = 3
        const val MAX_TRANSIENT_RETRIES = 4

        const val BEHIND_LIVE_STEP_DELAY_MS = 1_000L
        const val BEHIND_LIVE_MAX_DELAY_MS = 5_000L
        const val LINEAR_DELAY_1_MS = 1_000L
        const val LINEAR_DELAY_2_MS = 2_000L
        const val LINEAR_DELAY_3_MS = 3_000L

        const val TRANSIENT_BASE_DELAY_MS = 2_000L
        const val TRANSIENT_MAX_DELAY_MS = 8_000L
        const val DOUBLING_DELAY_1_MS = 2_000L
        const val DOUBLING_DELAY_2_MS = 4_000L
        const val DOUBLING_DELAY_3_MS = 8_000L

        const val FIRST = 1
        const val SECOND = 2
        const val THIRD = 3
        const val FOURTH = 4

        /** Media3 `ERROR_CODE_IO_FILE_NOT_FOUND` - inside the IO block, never on this site's list. */
        const val FILE_NOT_FOUND_ERROR_CODE = 2005
        const val NO_PERMISSION_ERROR_CODE = 2006
        const val READ_POSITION_ERROR_CODE = StreamFailureClass.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
    }
}
