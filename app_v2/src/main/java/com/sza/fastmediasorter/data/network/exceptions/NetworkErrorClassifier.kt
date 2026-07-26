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

    data class ClassificationResult(
        val exception: NetworkException,
        val usedFallback: Boolean
    )

    /**
     * Map any [Throwable] to its closest [NetworkException] subtype.
     * If the throwable is already a [NetworkException] it is returned as-is.
     */
    fun classify(throwable: Throwable): NetworkException =
        classifyInternal(throwable, logUnclassified = true).exception

    /**
     * Same subtype mapping as [classify], but without emitting the fallback
     * "unclassified exception" warning as a side effect.
     */
    fun classifySilently(throwable: Throwable): NetworkException =
        classifyInternal(throwable, logUnclassified = false).exception

    /**
     * Silent classification plus a flag telling the caller whether the mapping
     * had to fall back to the generic recoverable bucket.
     */
    fun classifyDetailedSilently(throwable: Throwable): ClassificationResult =
        classifyInternal(throwable, logUnclassified = false)

    private fun classifyInternal(
        throwable: Throwable,
        logUnclassified: Boolean
    ): ClassificationResult {
        if (throwable is LocalNetworkPermissionDeniedException) {
            return ClassificationResult(throwable, usedFallback = false)
        }
        if (throwable is NetworkException) {
            return ClassificationResult(throwable, usedFallback = false)
        }

        // S1055: SSH-specific reconnect outcomes (host-key change / auth reject) short-circuit ahead of
        // the generic message heuristics so they are never folded into a transient, retryable outcome.
        sshOutcome(throwable)?.let { return ClassificationResult(it, usedFallback = false) }

        return when {
            // OS-level socket block for missing ACCESS_LOCAL_NETWORK (Android 17+)
            throwable is SecurityException &&
                    (throwable.message?.contains("ACCESS_LOCAL_NETWORK", ignoreCase = true) == true ||
                     throwable.message?.contains("local network permission", ignoreCase = true) == true) ->
                ClassificationResult(
                    LocalNetworkPermissionDeniedException(
                        "Local network access denied by OS: ${throwable.message}",
                        throwable
                    ),
                    usedFallback = false
                )

            throwable is SecurityException &&
                    (throwable.message?.contains("android.permission.ACCESS_LOCAL_NETWORK", ignoreCase = true) == true) ->
                ClassificationResult(
                    LocalNetworkPermissionDeniedException(
                        "Local network access denied by OS: ${throwable.message}",
                        throwable
                    ),
                    usedFallback = false
                )

            // Timeout
            throwable is SocketTimeoutException ->
                ClassificationResult(
                    NetworkTimeoutException("Connection timeout: ${throwable.message}", throwable),
                    usedFallback = false
                )

            // DNS resolution
            throwable is UnknownHostException ->
                ClassificationResult(
                    NetworkTimeoutException("Cannot resolve host: ${throwable.message}", throwable),
                    usedFallback = false
                )

            // Unreachable / refused
            throwable is ConnectException || throwable is NoRouteToHostException ->
                ClassificationResult(
                    NetworkTimeoutException("Server unreachable: ${throwable.message}", throwable),
                    usedFallback = false
                )

            // SSL/TLS
            throwable is SSLException ->
                ClassificationResult(
                    NetworkAccessDeniedException("SSL error: ${throwable.message}", throwable),
                    usedFallback = false
                )

            // File not found
            throwable is FileNotFoundException ->
                ClassificationResult(
                    NetworkFileNotFoundException(throwable.message ?: "File not found", throwable),
                    usedFallback = false
                )

            // SMB
            throwable.isSmbAccessDenied() ->
                ClassificationResult(
                    NetworkAccessDeniedException("SMB access denied: ${throwable.extractSmbStatus()}", throwable),
                    usedFallback = false
                )

            throwable.isSmbNotFound() ->
                ClassificationResult(
                    NetworkFileNotFoundException("SMB path not found: ${throwable.extractSmbStatus()}", throwable),
                    usedFallback = false
                )

            // Message-based heuristics (fallback)
            throwable.messageContains("access denied", "permission denied", "authentication", "STATUS_ACCESS_DENIED", "401", "403") ->
                ClassificationResult(
                    NetworkAccessDeniedException(throwable.message ?: "Access denied", throwable),
                    usedFallback = false
                )

            throwable.messageContains("not found", "STATUS_OBJECT_NAME_NOT_FOUND", "STATUS_OBJECT_PATH_NOT_FOUND", "404") ->
                ClassificationResult(
                    NetworkFileNotFoundException(throwable.message ?: "Not found", throwable),
                    usedFallback = false
                )

            throwable.messageContains("rate limit", "rate_limit", "too many requests", "429") ->
                ClassificationResult(
                    NetworkRateLimitException(
                        retryAfterSeconds = throwable.extractRetryAfter(),
                        message = throwable.message ?: "Rate limit exceeded",
                        cause = throwable
                    ),
                    usedFallback = false
                )

            throwable.extractHttpStatus() in 500..599 ->
                ClassificationResult(
                    NetworkServerErrorException(
                        statusCode = throwable.extractHttpStatus(),
                        message = throwable.message ?: "Server error",
                        cause = throwable
                    ),
                    usedFallback = false
                )

            throwable.messageContains("server error", "internal server error", "service unavailable", "bad gateway", "500", "502", "503", "504") ->
                ClassificationResult(
                    NetworkServerErrorException(message = throwable.message ?: "Server error", cause = throwable),
                    usedFallback = false
                )

            throwable.messageContains("timeout", "timed out") ->
                ClassificationResult(
                    NetworkTimeoutException(throwable.message ?: "Timeout", throwable),
                    usedFallback = false
                )

            throwable.messageContains("connection reset", "connection closed", "broken pipe", "connection lost") ->
                ClassificationResult(
                    NetworkConnectionLostException(throwable.message ?: "Connection lost", throwable),
                    usedFallback = false
                )

            throwable.messageContains("unsupported", "not implemented") ->
                ClassificationResult(
                    NetworkUnsupportedOperationException(throwable.message ?: "Unsupported operation", throwable),
                    usedFallback = false
                )

            // Recurse into cause chain - handles wrapped exceptions where a generic Exception
            // hides a typed one (e.g. IOException("Server unreachable", SocketTimeoutException))
            // so that the caller gets a specific subtype instead of the default fallback.
            throwable.cause.let { c -> c != null && c !== throwable } -> {
                val causeResult = classifyInternal(throwable.cause!!, logUnclassified)
                if (causeResult.exception !is NetworkConnectionLostException || !causeResult.usedFallback) {
                    // Cause yielded a more specific classification - use it
                    causeResult
                } else {
                    if (logUnclassified) {
                        Timber.w(throwable, "NetworkErrorClassifier: unclassified exception ${throwable.javaClass.simpleName}")
                    }
                    ClassificationResult(
                        NetworkConnectionLostException(
                            "Network error: ${throwable.message ?: throwable.javaClass.simpleName}",
                            throwable
                        ),
                        usedFallback = true
                    )
                }
            }

            // Default: wrap as connection-lost (safest recoverable assumption)
            else -> {
                if (logUnclassified) {
                    Timber.w(throwable, "NetworkErrorClassifier: unclassified exception ${throwable.javaClass.simpleName}")
                }
                ClassificationResult(
                    NetworkConnectionLostException(
                        "Network error: ${throwable.message ?: throwable.javaClass.simpleName}",
                        throwable
                    ),
                    usedFallback = true
                )
            }
        }
    }

    /**
     * S1055: recognise the two SSH-specific reconnect outcomes that must never be treated as transient -
     * a pinned host-key change (security event) and an authentication rejection (credentials no longer
     * valid, e.g. the share was deleted and re-created). Matches both the raw JSch verdict and the typed
     * [com.sza.fastmediasorter.data.remote.sftp.HostKeyMismatchException] message. Specific auth tokens
     * only ("auth fail" / "auth cancel" / "userauth") so a normal SFTP file "permission denied" status is
     * not swallowed. Returns null when the throwable is neither, so normal classification continues.
     */
    private fun sshOutcome(throwable: Throwable): NetworkException? = when {
        throwable.messageContains("hostkey", "host key", "host-key") -> {
            NetworkHostKeyChangedException("Server host key changed: ${throwable.message}", throwable)
        }
        throwable.messageContains("auth fail", "auth cancel", "userauth") -> {
            NetworkAccessDeniedException("SFTP auth failed: ${throwable.message}", throwable)
        }
        else -> null
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
