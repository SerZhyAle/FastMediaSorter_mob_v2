package com.sza.fastmediasorter.core.playback.resilience

/**
 * The outcomes the three stream retry ladders distinguish when a playback failure arrives (S1513).
 *
 * The class is the *observation*, not the decision: each call site keeps its own policy on top, so
 * this type must stay expressive enough for the strictest of them - the video helper, which retries
 * a bad HTTP status only for 429/5xx - without collapsing the cases the looser ones need.
 */
internal enum class StreamFailureClass {

    /** The sliding live window moved past the playhead. A desync, not an unreachable endpoint. */
    BEHIND_LIVE_WINDOW,

    /** Transport-level failure: timeout, connection, or an unspecified/other IO error. */
    TRANSIENT_NETWORK,

    /** The server answered, and the status it answered with is worth asking again. */
    RETRYABLE_HTTP_STATUS,

    /** Nothing observed justifies another attempt - the failure belongs in front of the user. */
    NOT_RETRYABLE,
    ;

    companion object {

        /** Mirrors `PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW`. */
        const val ERROR_CODE_BEHIND_LIVE_WINDOW = 1002

        /** Mirrors `PlaybackException.ERROR_CODE_TIMEOUT`. */
        const val ERROR_CODE_TIMEOUT = 1003

        /** Mirrors `PlaybackException.ERROR_CODE_IO_UNSPECIFIED` - first code of the IO block. */
        const val ERROR_CODE_IO_UNSPECIFIED = 2000

        /** Mirrors `PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED`. */
        const val ERROR_CODE_IO_NETWORK_CONNECTION_FAILED = 2001

        /** Mirrors `PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT`. */
        const val ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT = 2002

        /** Mirrors `PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS`. */
        const val ERROR_CODE_IO_BAD_HTTP_STATUS = 2004

        /** Mirrors `PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE` - last IO code. */
        const val ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE = 2008

        /** HTTP 429: the only non-5xx status the video helper treats as worth retrying. */
        const val HTTP_TOO_MANY_REQUESTS = 429

        private val RETRYABLE_SERVER_STATUSES = 500..599

        /**
         * The whole IO block the two audio sites accept, taken as a range rather than a code list
         * so a Media3 upgrade that adds a code inside it keeps the site's current behaviour.
         */
        private val IO_ERROR_CODES =
            ERROR_CODE_IO_UNSPECIFIED..ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE

        /**
         * Classifies a failure from the two numbers a call site can always produce.
         *
         * [httpStatus] is null when no status was observed - a code that is not a bad-HTTP-status
         * error, or one whose `cause` chain carried no response code. Per ADR-4 that absence is its
         * own answer and never stands in for a healthy status.
         */
        fun classify(errorCode: Int, httpStatus: Int?): StreamFailureClass = when {
            errorCode == ERROR_CODE_BEHIND_LIVE_WINDOW -> BEHIND_LIVE_WINDOW
            // Checked before the IO range that contains it: the status, not the code, decides here.
            errorCode == ERROR_CODE_IO_BAD_HTTP_STATUS -> classifyHttpStatus(httpStatus)
            // Of the four codes the video helper retries with no further evidence, three sit inside
            // the IO block the two audio sites retry, so the range below already covers them; only
            // the timeout code lives outside it. The three keep their names above as the mapping
            // table this file exists to hold, and the later phases read them by name.
            errorCode == ERROR_CODE_TIMEOUT || errorCode in IO_ERROR_CODES -> TRANSIENT_NETWORK
            else -> NOT_RETRYABLE
        }

        private fun classifyHttpStatus(httpStatus: Int?): StreamFailureClass {
            val status = httpStatus ?: return NOT_RETRYABLE
            val retryable = status == HTTP_TOO_MANY_REQUESTS || status in RETRYABLE_SERVER_STATUSES
            return if (retryable) RETRYABLE_HTTP_STATUS else NOT_RETRYABLE
        }
    }
}
