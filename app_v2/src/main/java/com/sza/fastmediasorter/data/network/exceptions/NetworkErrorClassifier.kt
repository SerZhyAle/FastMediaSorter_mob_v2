package com.sza.fastmediasorter.data.network.exceptions

import timber.log.Timber
import java.io.FileNotFoundException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Classifies raw exceptions into typed [NetworkException] subclasses.
 *
 * Usage:
 * ```
 * try { … } catch (e: Exception) {
 *     throw NetworkErrorClassifier.classify(e)
 * }
 * ```
 */
object NetworkErrorClassifier {

    /**
     * Map any [Throwable] to its closest [NetworkException] subtype.
     * If the throwable is already a [NetworkException] it is returned as-is.
     */
    fun classify(throwable: Throwable): NetworkException {
        if (throwable is NetworkException) return throwable

        return when {
            // Timeout
            throwable is SocketTimeoutException ->
                NetworkTimeoutException("Connection timeout: ${throwable.message}", throwable)

            // DNS resolution
            throwable is UnknownHostException ->
                NetworkTimeoutException("Cannot resolve host: ${throwable.message}", throwable)

            // Unreachable / refused
            throwable is ConnectException || throwable is NoRouteToHostException ->
                NetworkTimeoutException("Server unreachable: ${throwable.message}", throwable)

            // SSL/TLS
            throwable is SSLException ->
                NetworkAccessDeniedException("SSL error: ${throwable.message}", throwable)

            // File not found
            throwable is FileNotFoundException ->
                NetworkFileNotFoundException(throwable.message ?: "File not found", throwable)

            // SMB
            throwable.isSmbAccessDenied() ->
                NetworkAccessDeniedException("SMB access denied: ${throwable.extractSmbStatus()}", throwable)

            throwable.isSmbNotFound() ->
                NetworkFileNotFoundException("SMB path not found: ${throwable.extractSmbStatus()}", throwable)

            // Message-based heuristics (fallback)
            throwable.messageContains("access denied", "permission denied", "authentication", "STATUS_ACCESS_DENIED", "401", "403") ->
                NetworkAccessDeniedException(throwable.message ?: "Access denied", throwable)

            throwable.messageContains("not found", "STATUS_OBJECT_NAME_NOT_FOUND", "STATUS_OBJECT_PATH_NOT_FOUND", "404") ->
                NetworkFileNotFoundException(throwable.message ?: "Not found", throwable)

            throwable.messageContains("timeout", "timed out") ->
                NetworkTimeoutException(throwable.message ?: "Timeout", throwable)

            throwable.messageContains("connection reset", "connection closed", "broken pipe", "connection lost") ->
                NetworkConnectionLostException(throwable.message ?: "Connection lost", throwable)

            throwable.messageContains("unsupported", "not implemented") ->
                NetworkUnsupportedOperationException(throwable.message ?: "Unsupported operation", throwable)

            // Default: wrap as connection-lost (safest recoverable assumption)
            else -> {
                Timber.w(throwable, "NetworkErrorClassifier: unclassified exception ${throwable.javaClass.simpleName}")
                NetworkConnectionLostException(
                    "Network error: ${throwable.message ?: throwable.javaClass.simpleName}",
                    throwable
                )
            }
        }
    }

    /**
     * Returns `true` when the error is transient and may succeed on retry.
     */
    fun isTransient(throwable: Throwable): Boolean {
        val classified = if (throwable is NetworkException) throwable else classify(throwable)
        return classified is NetworkTimeoutException || classified is NetworkConnectionLostException
    }

    // ── helpers ──────────────────────────────────────────────────────

    private fun Throwable.messageContains(vararg tokens: String): Boolean {
        val msg = message ?: return false
        return tokens.any { msg.contains(it, ignoreCase = true) }
    }

    private fun Throwable.isSmbAccessDenied(): Boolean {
        val msg = message ?: return false
        return msg.contains("STATUS_ACCESS_DENIED", ignoreCase = true) ||
                msg.contains("STATUS_LOGON_FAILURE", ignoreCase = true)
    }

    private fun Throwable.isSmbNotFound(): Boolean {
        val msg = message ?: return false
        return msg.contains("STATUS_OBJECT_NAME_NOT_FOUND", ignoreCase = true) ||
                msg.contains("STATUS_OBJECT_PATH_NOT_FOUND", ignoreCase = true)
    }

    private fun Throwable.extractSmbStatus(): String {
        val msg = message ?: return "unknown"
        val match = Regex("STATUS_([A-Z_]+)").find(msg)
        return match?.value ?: msg.take(80)
    }
}
