package com.sza.fastmediasorter.core.playback.resilience

/**
 * One playback failure at either audio site, reduced to plain values (S1513).
 *
 * Shared by both audio ladders because they observe the identical pair: the error code and nothing
 * else - neither has ever walked the `cause` chain for a response code.
 */
internal data class StreamAudioFailure(
    val failureClass: StreamFailureClass,
    val errorCode: Int,
)

/** The flat code range both audio sites accept; the classifier's own IO range is private to it. */
private val AUDIO_IO_ERROR_CODES =
    StreamFailureClass.ERROR_CODE_IO_UNSPECIFIED..StreamFailureClass.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE

/**
 * Whether either audio ladder would spend a retry on this failure, moved verbatim from the two
 * `errorCode in IO_UNSPECIFIED..IO_READ_POSITION_OUT_OF_RANGE` checks.
 *
 * The bad-HTTP-status code is the trap this phase exists to avoid: it lies inside that range, so both
 * sites retry it today without ever looking at a status, while the classifier routes it to its HTTP
 * branch and - with no status observed - answers `NOT_RETRYABLE`. Reading that as "give up" would stop
 * retrying a failure both sites retry, which strategic §2 forbids, so here the code decides and the
 * class does not. `TRANSIENT_NETWORK` is narrowed for the mirror-image reason - it also covers the
 * timeout code, outside the range and never accepted here.
 */
internal fun StreamAudioFailure.isRetryableAtAudioSite(): Boolean = when (failureClass) {
    StreamFailureClass.TRANSIENT_NETWORK -> errorCode in AUDIO_IO_ERROR_CODES
    StreamFailureClass.RETRYABLE_HTTP_STATUS -> true
    StreamFailureClass.NOT_RETRYABLE -> errorCode == StreamFailureClass.ERROR_CODE_IO_BAD_HTTP_STATUS
    StreamFailureClass.BEHIND_LIVE_WINDOW -> false
}

/**
 * The three answers the foreground-service audio ladder can give.
 *
 * [Ignore] is the policy declining to intervene, so the caller's own default stands: at failure time
 * the error falls through to the skip/fatal path, at fire time the pending reconnect proceeds.
 */
internal sealed interface StreamServiceRetryDecision {

    /** Schedule a reconnect [delayMs] from now; the caller owns the Handler. */
    data class RetryAfter(val delayMs: Long) : StreamServiceRetryDecision

    /** The window is spent. The give-up effect stays at the call site - here it stops a service. */
    object GiveUp : StreamServiceRetryDecision

    /** Nothing to say - the interface KDoc names the caller's default at each entry point. */
    object Ignore : StreamServiceRetryDecision
}

/**
 * The reconnect ladder of `AudioPlaybackService`, lifted out of Media3 and the Android clock (S1513).
 *
 * Behaviour moved verbatim, two-stage gate included: the entry gate accepts a failure while the
 * stream has ever played OR the connection is younger than [CONNECT_WINDOW_MS], while the fire-time
 * gate is judged against a different stamp per life stage - the connection stamp while the stream
 * never played, [RECONNECT_WINDOW_MS] from the *first* retry once it has (S1291). Collapsing the
 * pair into one clock would make a stream that played once give up when its connect window elapsed.
 * Time is a call parameter, never read inside (ADR-2), and the give-up effect stays at the call site
 * (ADR-3) precisely because there it means dropping a foreground service.
 */
internal class StreamServiceRetryPolicy(
    private val connectWindowMs: Long = CONNECT_WINDOW_MS,
    private val reconnectWindowMs: Long = RECONNECT_WINDOW_MS,
) {

    private var connectionStartedAtMs = 0L
    private var hasEverPlayed = false
    private var attempt = 0
    private var retryStartedAtMs = NOT_STAMPED

    /** A new media item is loading: the connect window restarts and every budget is refilled. */
    fun onStreamChanged(nowMs: Long) {
        connectionStartedAtMs = nowMs
        hasEverPlayed = false
        attempt = 0
        retryStartedAtMs = NOT_STAMPED
    }

    /**
     * The catalog answered whether this url ever played; the lookup lands after [onStreamChanged].
     * Assigns rather than accumulates, as the field did - a false answer overwrites a ready state
     * that arrived while the lookup was in flight.
     */
    fun onKnownPlayedBefore(playedBefore: Boolean) {
        hasEverPlayed = playedBefore
    }

    /** A confirmed ready state: refill the ladder and close the reconnect window. */
    fun onStreamReady() {
        attempt = 0
        retryStartedAtMs = NOT_STAMPED
    }

    /**
     * S1291: only a catalog stream earns the reconnect window. Set for every media item, this flag
     * made an ordinary file retry forever instead of taking the fatal path that stops the service.
     */
    fun onCatalogStreamPlaying() {
        hasEverPlayed = true
    }

    /** The user paused before the pending retry fired, so its window starts over on the next one. */
    fun onRetryDropped() {
        retryStartedAtMs = NOT_STAMPED
    }

    /** Judge one failure and, when it answers with a retry, spend an attempt and stamp the window. */
    fun onFailure(failure: StreamAudioFailure, nowMs: Long): StreamServiceRetryDecision {
        val entryWindowOpen = hasEverPlayed || nowMs - connectionStartedAtMs < connectWindowMs
        if (!failure.isRetryableAtAudioSite() || !entryWindowOpen) {
            return StreamServiceRetryDecision.Ignore
        }
        // The delay is read off the counter before it is raised, so the first failure of a
        // connection waits the base delay - the site's `BASE shl attempt` then `attempt++` order.
        val delayMs = StreamBackoff.exponentialDelayMs(
            attempt = attempt,
            baseMs = BASE_RETRY_DELAY_MS,
            capMs = MAX_RETRY_DELAY_MS,
            maxShift = MAX_BACKOFF_SHIFT,
        )
        attempt = (attempt + 1).coerceAtMost(MAX_BACKOFF_SHIFT)
        if (retryStartedAtMs == NOT_STAMPED) {
            retryStartedAtMs = nowMs
        }
        return StreamServiceRetryDecision.RetryAfter(delayMs)
    }

    /** Judge the pending retry at the moment it is due, against this stream's life stage. */
    fun onRetryDue(nowMs: Long): StreamServiceRetryDecision {
        val spent = if (hasEverPlayed) {
            retryStartedAtMs != NOT_STAMPED && nowMs - retryStartedAtMs >= reconnectWindowMs
        } else {
            nowMs - connectionStartedAtMs >= connectWindowMs
        }
        return if (spent) StreamServiceRetryDecision.GiveUp else StreamServiceRetryDecision.Ignore
    }

    companion object {

        /** `RadioStreamBufferConfig.DIALOG_TIMEOUT_MS` - how long a first connection may keep trying. */
        const val CONNECT_WINDOW_MS = 15_000L

        /**
         * S1291: how long reconnect attempts may continue after a stream that already played once
         * stops being reachable. Without this cap the loop ran until the process died, keeping a
         * foreground service with a wake mode alive all night.
         */
        const val RECONNECT_WINDOW_MS = 5 * 60 * 1000L

        const val BASE_RETRY_DELAY_MS = 2_000L
        const val MAX_RETRY_DELAY_MS = 8_000L

        private const val MAX_BACKOFF_SHIFT = 2
        private const val NOT_STAMPED = 0L
    }
}
