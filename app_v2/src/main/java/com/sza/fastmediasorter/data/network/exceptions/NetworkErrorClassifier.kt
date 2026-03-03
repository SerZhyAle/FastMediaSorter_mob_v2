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

            throwable.messageContains("rate limit", "rate_limit", "too many requests", "429") ->
                NetworkRateLimitException(
                    retryAfterSeconds = throwable.extractRetryAfter(),
                    message = throwable.message ?: "Rate limit exceeded",
                    cause = throwable
                )

            throwable.extractHttpStatus() in 500..599 ->
                NetworkServerErrorException(
                    statusCode = throwable.extractHttpStatus(),
                    message = throwable.message ?: "Server error",
                    cause = throwable
                )

            throwable.messageContains("server error", "internal server error", "service unavailable", "bad gateway", "500", "502", "503", "504") ->
                NetworkServerErrorException(message = throwable.message ?: "Server error", cause = throwable)

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
        return classified is NetworkTimeoutException ||
                classified is NetworkConnectionLostException ||
                classified is NetworkRateLimitException ||
                classified is NetworkServerErrorException
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
                msg.contains("STATUS_OBJECT_PATH_NOT_FOUND", ignoreCase = true) ||
                msg.contains("STATUS_BAD_NETWORK_NAME", ignoreCase = true) // SMB share not found on server
    }

    private fun Throwable.extractSmbStatus(): String {
        val msg = message ?: return "unknown"
        val match = Regex("STATUS_([A-Z_]+)").find(msg)
        return match?.value ?: msg.take(80)
    }

    /**
     * Extracts HTTP status code from exception message (e.g. "HTTP 429: ...", "Response code: 503").
     * Returns -1 if no HTTP status code is detected.
     */
    private fun Throwable.extractHttpStatus(): Int {
        val msg = message ?: return -1
        return Regex("""(?:HTTP|response code)[:\s]+(\d{3})""", RegexOption.IGNORE_CASE)
            .find(msg)?.groupValues?.get(1)?.toIntOrNull() ?: -1
    }

    /**
     * Extracts `Retry-After` hint (seconds) from exception message when available.
     */
    private fun Throwable.extractRetryAfter(): Long? {
        val msg = message ?: return null
        return Regex("""[Rr]etry-[Aa]fter[:\s]+(\d+)""").find(msg)?.groupValues?.get(1)?.toLongOrNull()
    }
}
