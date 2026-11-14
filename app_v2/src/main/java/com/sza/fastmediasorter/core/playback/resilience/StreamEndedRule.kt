package com.sza.fastmediasorter.core.playback.resilience

/** What a playback site does when its player reaches the end of the media. */
internal enum class StreamEndedOutcome {

    /** The server closed a live stream: it is a lost connection, so reconnect through the ladder. */
    RECONNECT,

    /** The media really finished, or never played: the site's own end-of-track handling stands. */
    SETTLE,
}

/**
 * Tells a live stream closed by its server apart from a track that finished, at the moment the
 * player reports its end.
 *
 * A clean close (no length, no reset) reaches the end state with no player error, so neither audio
 * ladder ever runs and a live radio stops silently. The rule is pure: the two sites read their own
 * player and pass plain values in, and the existing ladders (2 s, 4 s, 8 s; the service's 5-minute
 * window) keep owning every delay and give-up.
 */
internal object StreamEndedRule {

    /**
     * The failure a clean server close is fed to the ladders as - the same classified failure a
     * dropped connection produces, so both ladders answer it exactly as they answer that one.
     */
    val CONNECTION_LOST = StreamAudioFailure(
        failureClass = StreamFailureClass.TRANSIENT_NETWORK,
        errorCode = StreamFailureClass.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    )

    /**
     * [hasPlayed] keeps a source that never produced sound out of the reconnect loop: its first
     * connection is bounded by the connect window and ends through the error path.
     *
     * [wasLiveOrDurationUnknown] must be read while the media plays, never at the end: a progressive
     * source learns a duration when its body ends, so by then every stream looks like a finite file.
     */
    fun decide(
        isBroadcast: Boolean,
        hasPlayed: Boolean,
        wasLiveOrDurationUnknown: Boolean,
    ): StreamEndedOutcome =
        if (!isBroadcast && hasPlayed && wasLiveOrDurationUnknown) {
            StreamEndedOutcome.RECONNECT
        } else {
            StreamEndedOutcome.SETTLE
        }
}
