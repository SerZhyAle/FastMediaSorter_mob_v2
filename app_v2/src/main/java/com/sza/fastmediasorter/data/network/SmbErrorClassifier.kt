package com.sza.fastmediasorter.data.network

import timber.log.Timber
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Categories for SMB playback-path failures. Used to enrich log output so
 * post-mortem analysis can distinguish failure types without device access.
 *
 * STALE_POOL_CONNECTION  - watchdog fired on a VALIDATED connection (TCP was silently dropped)
 * NEW_CONNECTION_TIMEOUT - watchdog fired on a FRESH connection (network loss or very slow NAS)
 * TRANSPORT_FAILURE      - broken-pipe / socket error on openFile (stale SMBJ cache entry)
 * AUTH_CONFIG            - authentication, access-denied, or share-not-found error
 * UNKNOWN                - unclassified
 */
/**
 * S1617: what a single TCP connect actually answered.
 *
 * [REFUSED] is deliberately not a failure: something at that address answered the SYN, which is a
 * stronger proof of life than an open port on a host that ignores everything else.
 */
enum class TcpConnectOutcome {
    CONNECTED,
    REFUSED,
    TIMED_OUT,
    NAME_NOT_RESOLVED,
    NO_NETWORK,
    FAILED,
}

enum class SmbPlaybackErrorCategory {
    STALE_POOL_CONNECTION,
    NEW_CONNECTION_TIMEOUT,
    TRANSPORT_FAILURE,
    AUTH_CONFIG,
    UNKNOWN
}

/**
 * Pure helpers for SmbConnectionManager: classify exceptions into retry/non-retry buckets,
 * detect transport-level socket failures, and produce user-friendly error messages.
 *
 * Extracted to keep SmbConnectionManager below the 1000-line cap.
 */
object SmbErrorClassifier {

    /**
     * True when the exception identifies an unrecoverable error - auth, access-denied, share-not-found,
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

        // Share/path doesn't exist on server - retrying will never help
        val isShareNotFound = message.contains("STATUS_BAD_NETWORK_NAME", ignoreCase = true) ||
            message.contains("STATUS_BAD_NETWORK_PATH", ignoreCase = true) ||
            message.contains("STATUS_OBJECT_NAME_NOT_FOUND", ignoreCase = true)

        val isConfigError = message.contains("Unknown host", ignoreCase = true) ||
            message.contains("Connection refused", ignoreCase = true) ||
            message.contains("No such host", ignoreCase = true)

        // TCP pre-check timeouts (SocketTimeoutException / "Server unreachable") are transient -
        // brief latency spikes (ARP, NIC wake-up, NAT) can cause them even when the server is reachable.
        // Only hard config/auth/share errors are truly non-retriable.

        return isAuthError || isAccessError || isConfigError || isShareNotFound
    }

    /**
     * True when [e] is a broken-pipe or transport-level socket failure - used to detect a stale
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
                "Server is responding slowly. Directory listing timed out - try again or check server load."
            else -> e.message ?: "Unknown error"
        }
    }

    /**
     * Fast TCP pre-check before a full SMBJ connect. Protocol-agnostic - SFTP and FTP callers
     * (and the S1025 transfer pre-flight) reuse it, so the log line below must not name SMB.
     * @return `true` if the TCP socket connect to [host]:[port] succeeded within [timeoutMs];
     *   `false` if the host is unreachable / refused / timed out at the TCP layer.
     */
    fun checkConnectivity(host: String, port: Int, timeoutMs: Int): Boolean =
        classifyConnectivity(host, port, timeoutMs) == TcpConnectOutcome.CONNECTED

    /**
     * S1617: the same connect as [checkConnectivity], reporting *what* happened instead of only
     * whether it succeeded. A refused connection proves the host is alive at the IP layer, and a
     * boolean cannot say that - it reads identically to a host that is switched off, which is the
     * conflation the Monitor's reachability role exists to undo.
     */
    fun classifyConnectivity(host: String, port: Int, timeoutMs: Int): TcpConnectOutcome =
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            TcpConnectOutcome.CONNECTED
        } catch (e: SocketTimeoutException) {
            logConnectFailure(host, port, e)
            TcpConnectOutcome.TIMED_OUT
        } catch (e: UnknownHostException) {
            logConnectFailure(host, port, e)
            TcpConnectOutcome.NAME_NOT_RESOLVED
        } catch (e: IOException) {
            logConnectFailure(host, port, e)
            classifyConnectFailure(e)
        } catch (e: IllegalArgumentException) {
            // Kept because the catch this replaced was `catch (e: Exception)`: a port outside 0..65535
            // makes InetSocketAddress throw, and the three boolean callers have always been handed
            // `false` for it rather than an exception. Narrowing the catch without this line would
            // have changed their behaviour as a side effect of adding the classification.
            logConnectFailure(host, port, e)
            TcpConnectOutcome.FAILED
        }

    /**
     * `ConnectException` carries the distinction only in its message: the platform raises the same
     * type for an actively refused port and for a network the device cannot route to at all.
     */
    private fun classifyConnectFailure(e: IOException): TcpConnectOutcome {
        val message = e.message.orEmpty()
        return when {
            CONNECTION_REFUSED.containsMatchIn(message) -> TcpConnectOutcome.REFUSED
            NETWORK_UNREACHABLE.containsMatchIn(message) -> TcpConnectOutcome.NO_NETWORK
            else -> TcpConnectOutcome.FAILED
        }
    }

    // S1027: log the real cause (refused / no-route / timeout) instead of a fixed
    // "after Nms" text that misleads when the failure is an immediate active refusal.
    // S1320: said "SMB connectivity check" for every protocol, which sent a log reader
    // hunting an SMB defect while the failing destination was SFTP.
    private fun logConnectFailure(host: String, port: Int, e: Exception) {
        Timber.w("TCP connectivity check failed to $host:$port: ${e.javaClass.simpleName}: ${e.message}")
    }

    private val CONNECTION_REFUSED = Regex("""connection refused""", RegexOption.IGNORE_CASE)
    private val NETWORK_UNREACHABLE = Regex("""network is (unreachable|down)""", RegexOption.IGNORE_CASE)
}
