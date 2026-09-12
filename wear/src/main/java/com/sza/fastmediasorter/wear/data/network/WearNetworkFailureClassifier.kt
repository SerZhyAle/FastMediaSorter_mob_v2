package com.sza.fastmediasorter.wear.data.network

import com.sza.fastmediasorter.wear.domain.model.WearNetworkFailure
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

/**
 * S2488: turns the exception a protocol library threw into the one thing the wearer can act on.
 *
 * The cause chain is walked rather than the top throwable inspected: JSch wraps the socket failure,
 * so a check that stopped at the top would classify every SFTP failure as OTHER and leave the screen
 * exactly as it was.
 */
class WearNetworkFailureClassifier @Inject constructor() {

    fun classify(error: Throwable): WearNetworkFailure {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            val verdict = classifyOne(current)
            if (verdict != null) return verdict
            current = current.cause
            depth++
        }
        return WearNetworkFailure.OTHER
    }

    private fun classifyOne(error: Throwable): WearNetworkFailure? = when {
        error is UnknownHostException -> WearNetworkFailure.UNKNOWN_HOST
        error is SocketTimeoutException -> WearNetworkFailure.TIMEOUT
        error is ConnectException -> WearNetworkFailure.CONNECTION_REFUSED
        else -> classifyByMessage(error.message)
    }

    /**
     * JSch and Commons Net report authentication and timeout as plain library exceptions carrying no
     * dedicated type - `JSchException("Auth fail")`, `JSchException("timeout: socket is not
     * established")` - so the message is the only thing that distinguishes them. Matched
     * case-insensitively, and only after every typed test above has already declined.
     */
    private fun classifyByMessage(message: String?): WearNetworkFailure? {
        val text = message?.lowercase() ?: return null
        return when {
            text.contains("auth fail") || text.contains("auth cancel") ||
                text.contains("authentication fail") -> WearNetworkFailure.AUTH_REJECTED
            text.contains("timeout") -> WearNetworkFailure.TIMEOUT
            text.contains("connection refused") -> WearNetworkFailure.CONNECTION_REFUSED
            text.contains("unknownhost") -> WearNetworkFailure.UNKNOWN_HOST
            else -> null
        }
    }

    private companion object {
        /** A cause chain this deep is a cycle or a wrapper storm, not a diagnosis. */
        const val MAX_CAUSE_DEPTH = 10
    }
}
