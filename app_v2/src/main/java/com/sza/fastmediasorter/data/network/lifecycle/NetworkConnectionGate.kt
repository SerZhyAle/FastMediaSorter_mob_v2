package com.sza.fastmediasorter.data.network.lifecycle

import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException

/**
 * Protocol-neutral gate that brokers network connection leases. S0067.
 *
 * Concrete implementations (`SmbConnectionGate`, `SftpConnectionGate`, `FtpConnectionGate`,
 * `CloudConnectionGate`) wrap their per-protocol pool/manager. The gate guarantees:
 *
 *  - [acquire] returns a connection that has passed a liveness probe.
 *  - [release] with `success = false` physically destroys the connection so the next
 *    [acquire] cannot return the same dead object.
 *  - [withRetry] performs at most one transparent retry on transient failures.
 *  - [closeFor] respects [ConsumerType.isUi] vs [ConsumerType.BACKGROUND_WORKER] semantics.
 */
interface NetworkConnectionGate<C : Any> {

    val protocol: NetworkProtocol

    /**
     * Lease a connection for [resourceKey] tagged by [consumer]. The returned object is
     * verified live (cheap socket check + idle-aware probe). Caller must always call
     * [release] when done.
     */
    suspend fun acquire(consumer: ConsumerType, resourceKey: String): C

    /**
     * Return a connection to the pool. On `success = false` the implementation must
     * physically destroy the underlying socket/client so it cannot be served again.
     */
    fun release(connection: C, success: Boolean)

    /**
     * Run [op] with at most one transparent retry on transient failures (see [TransientFailure]).
     * Default implementation handles the retry loop and connection lifecycle for the consumer.
     */
    suspend fun <R> withRetry(
        consumer: ConsumerType,
        resourceKey: String,
        op: suspend (C) -> R
    ): R {
        val first = acquire(consumer, resourceKey)
        try {
            val result = op(first)
            release(first, success = true)
            return result
        } catch (cancel: CancellationException) {
            release(first, success = false)
            throw cancel
        } catch (t: Throwable) {
            val transient = TransientFailure.classify(t)
            release(first, success = false)
            if (transient == null) throw t

            // One retry with a guaranteed-fresh connection.
            val second = acquire(consumer, resourceKey)
            try {
                val result = op(second)
                release(second, success = true)
                return result
            } catch (cancel: CancellationException) {
                release(second, success = false)
                throw cancel
            } catch (second_t: Throwable) {
                release(second, success = false)
                throw second_t
            }
        }
    }

    /**
     * Close every connection tagged with a [ConsumerType] satisfying [consumer]'s class —
     * UI types close on `onStop`; [ConsumerType.BACKGROUND_WORKER] is never closed by the
     * lifecycle hook.
     */
    fun closeFor(consumer: ConsumerType)

    /**
     * Timestamp (`System.currentTimeMillis()`) of the most recent forced recreate for
     * [resourceKey], or `null` if none recorded. Consulted by S0066 (transient thumbnail
     * classification) and by [ConnectionDiagnostics].
     */
    fun lastRecreateMs(resourceKey: String): Long?
}

/**
 * Classifier that maps a [Throwable] (and its `cause` chain, max depth 5) to a transient-failure
 * [Reason], or `null` if the failure is non-transient. S0067.
 */
object TransientFailure {

    enum class Reason {
        BROKEN_PIPE,
        CHANNEL_CLOSED,
        TOKEN_EXPIRED,
        RATE_LIMIT,
        TRANSPORT,
        TIMEOUT
    }

    fun classify(error: Throwable): Reason? {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 5) {
            classifyOne(current)?.let { return it }
            current = current.cause
            depth++
        }
        return null
    }

    private fun classifyOne(t: Throwable): Reason? {
        // Class-level checks first — message-based checks are heuristic backups.
        if (t is SocketTimeoutException) return Reason.TIMEOUT
        if (t is SocketException) return Reason.TRANSPORT

        val msg = (t.message ?: "").lowercase()
        return when {
            msg.contains("broken pipe")          -> Reason.BROKEN_PIPE
            msg.contains("channel is closed") ||
                msg.contains("session is closed") ||
                msg.contains("diskshare has already been closed") -> Reason.CHANNEL_CLOSED
            msg.contains("401") ||
                msg.contains("token expired") ||
                msg.contains("invalid_grant") -> Reason.TOKEN_EXPIRED
            msg.contains("429") ||
                msg.contains("rate limit") -> Reason.RATE_LIMIT
            msg.contains("timed out") -> Reason.TIMEOUT
            t is IOException -> Reason.TRANSPORT
            else -> null
        }
    }
}
