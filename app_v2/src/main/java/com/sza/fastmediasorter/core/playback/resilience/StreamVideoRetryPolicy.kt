package com.sza.fastmediasorter.core.playback.resilience

/**
 * One playback failure at the full-screen video site, reduced to plain values (S1513).
 *
 * [errorCode] travels beside [failureClass] because the classifier is the union of three call sites:
 * its `TRANSIENT_NETWORK` covers the whole Media3 IO block, which the two audio sites retry, while
 * this site has only ever retried four codes. Strategic §11.3 forbids widening that set during the
 * move, so the code stays readable here instead of being inferred from the class alone.
 */
internal data class StreamVideoFailure(
    val failureClass: StreamFailureClass,
    val errorCode: Int,
    /** RTSP carries no sliding live window, so the re-anchor branch is never offered for it. */
    val isRtsp: Boolean,
)

/**
 * The three answers the video error ladder can give.
 *
 * [ReAnchor] and [Retry] carry their [ReAnchor.attempt] out of the policy rather than let the caller
 * read it back, so the log line keeps naming the attempt number without a second copy of the counter
 * living next to the first one.
 */
internal sealed interface StreamVideoRetryDecision {

    /** Seek to the live edge, then re-prepare, after [delayMs]: a desync, not a dead endpoint. */
    data class ReAnchor(val delayMs: Long, val attempt: Int) : StreamVideoRetryDecision

    /** Re-prepare after [delayMs]; whether the live edge also needs a seek is the caller's read. */
    data class Retry(val delayMs: Long, val attempt: Int) : StreamVideoRetryDecision

    /** Nothing left to spend, or nothing observed worth spending it on - show the user the error. */
    object Surface : StreamVideoRetryDecision
}

/**
 * The two-branch error ladder of `StreamPlaybackHelper.onPlayerError`, lifted out of Media3 (S1513).
 *
 * Behaviour moved verbatim from the helper. A routine live-edge desync re-anchors up to
 * [MAX_BEHIND_LIVE_RECOVERIES] times on the short linear ladder, because a bare re-prepare would
 * land on the same expired position and fail again; a transient failure retries up to
 * [MAX_TRANSIENT_RETRIES] times on the doubling one, flattened at [TRANSIENT_MAX_DELAY_MS] because
 * by the time an error reaches this ladder the ExoPlayer loader has already spent its own retries
 * (S1512). Giving up means surfacing the error - the player stays alive.
 *
 * No `now` parameter, unlike the other rules of this ticket: strategic §4 records that this ladder
 * counts attempts and never seconds, and an unused clock argument would advertise a time window the
 * ladder does not have. The same reasoning already stands in `StreamStallRule`, whose seeding and
 * clearing entry points take no time either.
 *
 * The counters have exactly one reset entry point, [onReady]. That is the whole of the old rule
 * "reset on a confirmed ready state, never on buffering": a buffering event has nothing to call.
 */
internal class StreamVideoRetryPolicy(
    private val maxBehindLiveRecoveries: Int = MAX_BEHIND_LIVE_RECOVERIES,
    private val maxTransientRetries: Int = MAX_TRANSIENT_RETRIES,
) {

    private var behindLiveRecoveries = 0
    private var transientRetries = 0

    /** Refill both budgets - playback is confirmed healthy again. */
    fun onReady() {
        behindLiveRecoveries = 0
        transientRetries = 0
    }

    /**
     * Judge one failure and, when it answers with a retry, spend the matching budget.
     *
     * The two budgets are deliberately separate: a channel whose live window keeps outrunning the
     * playhead must not eat the allowance a genuine network drop will need.
     */
    fun onFailure(failure: StreamVideoFailure): StreamVideoRetryDecision =
        when (failure.failureClass) {
            StreamFailureClass.BEHIND_LIVE_WINDOW -> decideBehindLive(failure.isRtsp)
            // The status the classifier read out of the cause chain is the evidence this branch
            // needs; the code adds nothing, being the bad-HTTP-status one by construction.
            StreamFailureClass.RETRYABLE_HTTP_STATUS -> decideTransient()
            StreamFailureClass.TRANSIENT_NETWORK -> decideTransient(failure.errorCode)
            StreamFailureClass.NOT_RETRYABLE -> StreamVideoRetryDecision.Surface
        }

    private fun decideBehindLive(isRtsp: Boolean): StreamVideoRetryDecision {
        if (isRtsp || behindLiveRecoveries >= maxBehindLiveRecoveries) {
            return StreamVideoRetryDecision.Surface
        }
        behindLiveRecoveries++
        val delayMs = StreamBackoff.linearDelayMs(
            attempt = behindLiveRecoveries,
            stepMs = BEHIND_LIVE_STEP_DELAY_MS,
            capMs = BEHIND_LIVE_MAX_DELAY_MS,
        )
        return StreamVideoRetryDecision.ReAnchor(delayMs, behindLiveRecoveries)
    }

    private fun decideTransient(errorCode: Int): StreamVideoRetryDecision {
        if (errorCode !in UNCONDITIONALLY_RECOVERABLE_CODES) {
            return StreamVideoRetryDecision.Surface
        }
        return decideTransient()
    }

    private fun decideTransient(): StreamVideoRetryDecision {
        if (transientRetries >= maxTransientRetries) {
            return StreamVideoRetryDecision.Surface
        }
        transientRetries++
        // The counter is incremented before the delay is read, so the first failure of a session
        // asks for shift 0 and gets the base delay - the `2000 shl (attempt - 1)` of the helper.
        val delayMs = StreamBackoff.exponentialDelayMs(
            attempt = transientRetries - 1,
            baseMs = TRANSIENT_BASE_DELAY_MS,
            capMs = TRANSIENT_MAX_DELAY_MS,
            maxShift = maxTransientRetries - 1,
        )
        return StreamVideoRetryDecision.Retry(delayMs, transientRetries)
    }

    companion object {

        const val MAX_BEHIND_LIVE_RECOVERIES = 3

        const val MAX_TRANSIENT_RETRIES = 4

        /** One second per attempt: a live-edge desync heals faster on a short predictable wait. */
        const val BEHIND_LIVE_STEP_DELAY_MS = 1_000L

        const val BEHIND_LIVE_MAX_DELAY_MS = 5_000L

        const val TRANSIENT_BASE_DELAY_MS = 2_000L

        /** The value the helper read as `RadioStreamBufferConfig.MAX_RETRY_DELAY_MS` (S1512). */
        const val TRANSIENT_MAX_DELAY_MS = 8_000L

        /**
         * The codes this site retries on the error code alone, taken verbatim from the helper's
         * superseded recoverable-error check minus its bad-HTTP-status case, which the classifier
         * now answers from the status instead.
         *
         * Named rather than derived from the classifier's IO range: that range is the union of all
         * three sites, and folding this site into it would silently start retrying a missing file
         * and a denied permission four times before the user hears about either.
         */
        private val UNCONDITIONALLY_RECOVERABLE_CODES = setOf(
            StreamFailureClass.ERROR_CODE_TIMEOUT,
            StreamFailureClass.ERROR_CODE_IO_UNSPECIFIED,
            StreamFailureClass.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            StreamFailureClass.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        )
    }
}
