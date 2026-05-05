package com.sza.fastmediasorter.data.network

import java.io.IOException
import java.net.SocketException

/**
 * Structured reason why a pooled SMB connection was declared dead.
 * Carried through logs and the UI-notification path so the right user message can be chosen.
 */
enum class DeadReason {
    TIMEOUT,
    BROKEN_PIPE,
    SERVER_RESET,
    AUTH_FAILED,
    SOCKET_CLOSED,
    UNKNOWN
}

/**
 * Pure inspector for pooled SMB connection liveness.
 *
 * No network I/O is performed — all checks are local state inspections on the SMBJ
 * object graph. This makes the hot path (isAlive on a healthy connection) essentially free.
 * The [classify] helper maps an exception to a [DeadReason] by traversing the cause chain
 * so callers can emit a single structured log line instead of a full stack trace.
 *
 * Created in S0061 Phase 01.
 */
class SmbConnectionHealthProbe {

    /**
     * Returns `true` when the pooled connection looks alive based on local state only.
     *
     * Checks (in order):
     * 1. Pool-level pending-close flag — if set, the connection is already being torn down.
     * 2. SMBJ `Connection.isConnected` — backed by the internal transport channel state.
     * 3. SMBJ `DiskShare.isConnected` — share-level connected flag.
     *
     * Limitations: SMBJ's `isConnected` is backed by `Socket.isClosed()`, which returns
     * `false` even after a server-side FIN (half-open state). The check here is therefore
     * a cheap pre-filter only; the definitive detection happens on the first write attempt
     * inside the callers' try/catch, which then triggers [classify] + purge.
     */
    fun isAlive(pooled: PooledConnection): Boolean {
        if (pooled.isPendingClose.get()) return false
        return try {
            pooled.connection.isConnected &&
                pooled.session.connection.isConnected &&
                pooled.share.isConnected
        } catch (_: Exception) {
            // Any exception accessing these properties means the object is in a bad state.
            false
        }
    }

    /**
     * Walk the cause chain of [throwable] (up to depth 8) and return the most specific
     * [DeadReason] that matches.
     *
     * Priority (first match wins):
     * - AUTH_FAILED  — `STATUS_LOGON_FAILURE` anywhere in message chain
     * - BROKEN_PIPE  — `SocketException` with "broken pipe" message
     * - SERVER_RESET — `SocketException` with "connection reset" / "connection abort"
     * - SOCKET_CLOSED — `IOException` with "socket closed" message, or `TransportException`
     *                   wrapping a socket-closed cause
     * - TIMEOUT      — `TimeoutException` / `TimeoutCancellationException`
     * - UNKNOWN      — anything else
     */
    fun classify(throwable: Throwable): DeadReason {
        var current: Throwable? = throwable
        var depth = 0
        while (current != null && depth < 8) {
            val msg = current.message?.lowercase() ?: ""

            // Auth failure — don't retry, surface immediately.
            if (msg.contains("status_logon_failure") ||
                msg.contains("authentication failed") ||
                msg.contains("logon failure")) {
                return DeadReason.AUTH_FAILED
            }

            if (current is SocketException) {
                return when {
                    msg.contains("broken pipe")                -> DeadReason.BROKEN_PIPE
                    msg.contains("connection reset")           -> DeadReason.SERVER_RESET
                    msg.contains("software caused connection abort") -> DeadReason.SERVER_RESET
                    msg.contains("socket closed")              -> DeadReason.SOCKET_CLOSED
                    else                                       -> DeadReason.SERVER_RESET
                }
            }

            if (current is IOException && msg.contains("socket closed")) {
                return DeadReason.SOCKET_CLOSED
            }

            // SMBJ TransportException wraps socket-level errors.
            if (current is com.hierynomus.protocol.transport.TransportException) {
                // Recurse into the cause for a more precise category before defaulting.
                val inner = current.cause
                if (inner != null) {
                    val innerMsg = inner.message?.lowercase() ?: ""
                    return when {
                        inner is SocketException && innerMsg.contains("broken pipe") ->
                            DeadReason.BROKEN_PIPE
                        inner is SocketException && (innerMsg.contains("connection reset") ||
                            innerMsg.contains("software caused")) ->
                            DeadReason.SERVER_RESET
                        inner is SocketException && innerMsg.contains("socket closed") ->
                            DeadReason.SOCKET_CLOSED
                        else -> DeadReason.SOCKET_CLOSED
                    }
                }
                return DeadReason.SOCKET_CLOSED
            }

            // Coroutine/Java timeouts.
            if (current is kotlinx.coroutines.TimeoutCancellationException ||
                current is java.util.concurrent.TimeoutException ||
                current.javaClass.simpleName.contains("TimeoutException")) {
                return DeadReason.TIMEOUT
            }

            current = current.cause
            depth++
        }
        return DeadReason.UNKNOWN
    }
}
