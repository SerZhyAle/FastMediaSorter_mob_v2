package com.sza.fastmediasorter.core.playback.resilience

/**
 * The failure class an inline audio dialog names in user words (STREAM-BANK 2.10, `06` section 2.5):
 * unreachable, refused by the server, untrusted certificate - never an exception text.
 *
 * Plain values, so the reason crosses the media session as its [name] (S4641).
 */
enum class StreamFailureReason {
    UNREACHABLE,
    REFUSED,
    UNTRUSTED_CERTIFICATE,
    ;

    companion object {

        /** Reads a [name] back; null for an absent or unknown value, which the dialog shows generically. */
        fun fromName(name: String?): StreamFailureReason? = entries.firstOrNull { it.name == name }
    }
}

/**
 * The words for a failure the ladder gave up on. A status the server answered with is a refusal; every
 * other given-up failure - a spent window, a transport error - is a host that did not answer.
 */
internal fun StreamAudioFailure.toReason(): StreamFailureReason = when {
    failureClass == StreamFailureClass.UNTRUSTED_CERTIFICATE -> StreamFailureReason.UNTRUSTED_CERTIFICATE
    failureClass == StreamFailureClass.NOT_RETRYABLE && httpStatus != null -> StreamFailureReason.REFUSED
    else -> StreamFailureReason.UNREACHABLE
}
