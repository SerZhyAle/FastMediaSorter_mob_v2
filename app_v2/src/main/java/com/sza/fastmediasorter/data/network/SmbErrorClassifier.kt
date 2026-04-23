package com.sza.fastmediasorter.data.network

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import timber.log.Timber

/**
 * Pure helpers for SmbConnectionManager: classify exceptions into retry/non-retry buckets,
 * detect transport-level socket failures, and produce user-friendly error messages.
 *
 * Extracted to keep SmbConnectionManager below the 1000-line cap.
 */
object SmbErrorClassifier {

    /**
     * True when the exception identifies an unrecoverable error — auth, access-denied, share-not-found,
     * unknown host or refused connection. Transient socket timeouts are treated as retriable.
     */
    fun isNonRetriableConnectionError(e: Exception): Boolean {
        val message = buildString {
            append(e.message ?: "")
            append(' ')
            append(e.cause?.message ?: "")
            append(' ')
            append(e.cause?.cause?.message ?: "")
        }

        val isAuthError = message.contains("STATUS_LOGON_FAILURE", ignoreCase = true) ||
            message.contains("Authentication failed", ignoreCase = true) ||
            message.contains("Logon failure", ignoreCase = true) ||
            message.contains("wrong password", ignoreCase = true) ||
            message.contains("invalid credential", ignoreCase = true)

        val isAccessError = message.contains("STATUS_ACCESS_DENIED", ignoreCase = true) ||
            message.contains("Access denied", ignoreCase = true)

        // Share/path doesn't exist on server — retrying will never help
        val isShareNotFound = message.contains("STATUS_BAD_NETWORK_NAME", ignoreCase = true) ||
            message.contains("STATUS_BAD_NETWORK_PATH", ignoreCase = true) ||
            message.contains("STATUS_OBJECT_NAME_NOT_FOUND", ignoreCase = true)

        val isConfigError = message.contains("Unknown host", ignoreCase = true) ||
            message.contains("Connection refused", ignoreCase = true) ||
            message.contains("No such host", ignoreCase = true)

        // TCP pre-check timeouts (SocketTimeoutException / "Server unreachable") are transient —
        // brief latency spikes (ARP, NIC wake-up, NAT) can cause them even when the server is reachable.
        // Only hard config/auth/share errors are truly non-retriable.

        return isAuthError || isAccessError || isConfigError || isShareNotFound
    }

    /**
     * True when [e] is a broken-pipe or transport-level socket failure — used to detect a stale
     * SMBJ-cached Connection that must be evicted before a retry. SMBJ's isConnected flag does
     * not detect TCP-level silent drops, so we have to look at the exception chain.
     */
    fun isTransportOrBrokenPipe(e: Exception): Boolean {
        var current: Throwable? = e
        var depth = 0
        while (current != null && depth < 5) {
            if (current is java.net.SocketException ||
                current is com.hierynomus.protocol.transport.TransportException) {
                return true
            }
            val msg = current.message?.lowercase() ?: ""
            if (msg.contains("broken pipe") || msg.contains("connection reset") ||
                msg.contains("transport") && msg.contains("socket")) {
                return true
            }
            current = current.cause
            depth++
        }
        return false
    }

    /** Map an exception to a localized-style, user-friendly message. */
    fun getUserFriendlyMessage(e: Exception): String {
        val cause = e.cause
        return when {
            e.message?.contains("Server unreachable", ignoreCase = true) == true ||
                cause is SocketTimeoutException ||
                e is SocketTimeoutException ->
                "Server is not responding. Make sure the device is powered on and reachable."
            e.message?.contains("Unknown host", ignoreCase = true) == true ->
                "Cannot resolve server address. Check server name/IP."
            e.message?.contains("Connection refused", ignoreCase = true) == true ->
                "Connection refused. Check if the SMB/file sharing service is running."
            e.message?.contains("Connection timed out", ignoreCase = true) == true ->
                "Connection timed out. Check network and server availability."
            e.message?.contains("Authentication failed", ignoreCase = true) == true ->
                "Authentication failed. Check username and password."
            e.message?.contains("Access denied", ignoreCase = true) == true ->
                "Access denied. Check share permissions."
            e is kotlinx.coroutines.TimeoutCancellationException ->
                "Operation timed out. Server may be overloaded or network slow."
            // SMBJ-level transaction timeout: server did not reply to QUERY_DIRECTORY / READ_ANDX
            // within the configured per-request window (CONNECTION_TIMEOUT_DEGRADED_MS).
            e.toString().contains("TimeoutException", ignoreCase = true) ||
                e.cause?.toString()?.contains("TimeoutException", ignoreCase = true) == true ->
                "Server is responding slowly. Directory listing timed out — try again or check server load."
            else -> e.message ?: "Unknown error"
        }
    }

    /** Fast TCP pre-check before a full SMBJ connect. Throws IOException if [host]:[port] is unreachable. */
    fun checkConnectivity(host: String, port: Int, timeoutMs: Int) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
        } catch (e: Exception) {
            Timber.w("Fast connectivity check failed to $host:$port after ${timeoutMs}ms")
            throw IOException("Server unreachable ($host:$port)", e)
        }
    }
}
