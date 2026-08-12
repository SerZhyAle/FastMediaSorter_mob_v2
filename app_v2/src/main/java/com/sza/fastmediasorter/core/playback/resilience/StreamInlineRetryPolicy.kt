package com.sza.fastmediasorter.core.playback.resilience

/**
 * The three answers the inline mini-player ladder can give.
 *
 * Unlike the two other sites this one also routes, which is why [OwnedByService] exists: with
 * background playback on, the same stream is already being retried by the foreground service, and
 * the manager must schedule nothing rather than run a second ladder against the same server.
 */
internal sealed interface StreamInlineRetryDecision {

    /** Schedule a reconnect of the in-app player [delayMs] from now; the caller owns the Handler. */
    data class RetryAfter(val delayMs: Long) : StreamInlineRetryDecision

    /** Nothing left to spend. The effect stays at the call site - here it stops the mini-control. */
    object GiveUp : StreamInlineRetryDecision

    /** Not this site's failure: `AudioPlaybackService` owns the stream and runs its own ladder. */
    object OwnedByService : StreamInlineRetryDecision
}

/**
 * The reconnect ladder of `StreamInlineAudioManager`, lifted out of Media3 and the Android clock
 * (S1513).
 *
 * Behaviour moved verbatim, including the two ways this ladder differs from its foreground-service
 * twin even though both compute the same delay off the same constants:
 * - one gate shape serves both the schedule moment and the fire moment, where the service has two;
 * - once the stream has played, there is no upper bound at all - not a count, not a window, only
 *   the delay plateau. Strategic §1 names that as a symptom and §2 forbids fixing it here, so
 *   `StreamInlineRetryPolicyTest` pins it as the current truth.
 *
 * `hasEverPlayed` is an input rather than state, unlike at the service, because the manager's stall
 * timeout co-owns that flag and stays out of this phase's scope: a second copy here could drift.
 */
internal class StreamInlineRetryPolicy(
    private val connectWindowMs: Long = CONNECT_WINDOW_MS,
) {

    private var connectionStartedAtMs = 0L
    private var attempt = 0

    /** A new stream is starting: the connect window restarts and the ladder is refilled. */
    fun onStreamStarted(nowMs: Long) {
        connectionStartedAtMs = nowMs
        attempt = 0
    }

    /** Audio is flowing again - refill the ladder without touching the connect window. */
    fun onPlaybackHealthy() {
        attempt = 0
    }

    /**
     * Playback was torn down. The connection stamp is zeroed rather than re-taken, verbatim: the
     * manager always re-stamps in `play()` before the gate is consulted again.
     */
    fun onCleared() {
        connectionStartedAtMs = 0L
        attempt = 0
    }

    /**
     * Judge one failure. A closed gate answers before the routing flag is read, as the site did, and
     * a stream the service owns spends no attempt - the counter belongs to the in-app player, which
     * is not the one that failed.
     */
    fun onFailure(
        failure: StreamAudioFailure,
        hasEverPlayed: Boolean,
        usingService: Boolean,
        nowMs: Long,
    ): StreamInlineRetryDecision = when {
        !failure.isRetryableAtAudioSite() || !mayReconnect(hasEverPlayed, nowMs) ->
            StreamInlineRetryDecision.GiveUp
        usingService -> StreamInlineRetryDecision.OwnedByService
        else -> StreamInlineRetryDecision.RetryAfter(spendAttempt())
    }

    /**
     * The gate, asked again when the pending retry is due.
     *
     * A closed gate at that moment has no effect at this site - the manager simply does not
     * reconnect - so this stays a predicate rather than a decision, which would advertise a give-up
     * consequence the fire path does not have.
     */
    fun mayReconnect(hasEverPlayed: Boolean, nowMs: Long): Boolean =
        hasEverPlayed || nowMs - connectionStartedAtMs < connectWindowMs

    /**
     * The delay for the current attempt, after which the counter is raised - the site's
     * `BASE shl attempt` then `attempt++` order, which is what makes the first failure of a
     * connection wait the base delay.
     */
    private fun spendAttempt(): Long {
        val delayMs = StreamBackoff.exponentialDelayMs(
            attempt = attempt,
            baseMs = BASE_RETRY_DELAY_MS,
            capMs = MAX_RETRY_DELAY_MS,
            maxShift = MAX_BACKOFF_SHIFT,
        )
        attempt = (attempt + 1).coerceAtMost(MAX_BACKOFF_SHIFT)
        return delayMs
    }

    companion object {

        /** `RadioStreamBufferConfig.DIALOG_TIMEOUT_MS` - the only bound a stream that never played gets. */
        const val CONNECT_WINDOW_MS = 15_000L

        const val BASE_RETRY_DELAY_MS = 2_000L

        const val MAX_RETRY_DELAY_MS = 8_000L

        private const val MAX_BACKOFF_SHIFT = 2
    }
}
