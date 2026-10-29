package com.sza.fastmediasorter.wear.domain.playback

import java.net.ConnectException

/** S4473: what a stream that stopped delivering means to the wearer, in the terms the screen words it in. */
enum class WearStreamFailure {
    /** The source did not answer or broke on the way; trying again may help. */
    FAILED,

    /** The producer is gone - a closed port or a removed stream - so only a new start brings it back. */
    ENDED
}

/**
 * S4473: tells a producer that stopped apart from a source that merely failed.
 *
 * A broadcast the phone stops closes its port or answers `404`/`410` afterwards (contract LIVE-BROADCAST
 * item T), which is a different sentence for the wearer than a timeout on a weak Wi-Fi. The inputs are
 * primitives so the rule stays free of the player library, like every other rule in this package.
 */
object WearStreamFailurePolicy {

    private const val HTTP_NOT_FOUND = 404
    private const val HTTP_GONE = 410

    /** A cause chain this deep is a cycle or a wrapper storm, not a diagnosis. */
    private const val MAX_CAUSE_DEPTH = 10

    /** The word Android's socket layer puts in a refused connect, both as `ECONNREFUSED` and in prose. */
    private const val REFUSED = "refused"

    /**
     * @param httpStatus the status of a rejected HTTP response, or null when the failure was not a response
     * @param cause the throwable behind the player error; its own causes are walked as well
     */
    fun ofError(httpStatus: Int?, cause: Throwable?): WearStreamFailure = when {
        httpStatus == HTTP_NOT_FOUND || httpStatus == HTTP_GONE -> WearStreamFailure.ENDED
        refusesConnection(cause) -> WearStreamFailure.ENDED
        else -> WearStreamFailure.FAILED
    }

    /**
     * Only a refusal counts: `ConnectException` also carries "network is unreachable", which says the watch
     * lost its Wi-Fi and not that the producer stopped.
     */
    private fun refusesConnection(cause: Throwable?): Boolean {
        var current = cause
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (current is ConnectException && current.message.orEmpty().contains(REFUSED, ignoreCase = true)) {
                return true
            }
            current = current.cause
            depth++
        }
        return false
    }
}
