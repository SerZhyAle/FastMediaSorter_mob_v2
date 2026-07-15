package com.sza.fastmediasorter.data.network.exceptions

import java.io.IOException

/**
 * Base exception for all network-related errors
 */
sealed class NetworkException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Authentication/authorization errors (401, 403, wrong credentials)
 */
class NetworkAccessDeniedException(message: String = "Access denied", cause: Throwable? = null) :
    NetworkException(message, cause)

/**
 * S1055 - the pinned server host key no longer matches the one recorded at pairing (possible
 * impersonation / MITM). Security-critical: a direct [NetworkException] subtype (never a
 * [NetworkConnectionLostException]), so it is non-transient by construction and is never auto-retried
 * or auto-accepted. Surfaced to the user as a security warning, not a routine connectivity error.
 */
class NetworkHostKeyChangedException(message: String = "Server host key changed", cause: Throwable? = null) :
    NetworkException(message, cause)

/**
 * Connection timeout or unreachable server
 */
class NetworkTimeoutException(message: String = "Connection timeout", cause: Throwable? = null) : 
    NetworkException(message, cause)

/**
 * File not found on remote server (404)
 */
class NetworkFileNotFoundException(message: String = "File not found", cause: Throwable? = null) : 
    NetworkException(message, cause)

/**
 * Connection lost during operation
 */
open class NetworkConnectionLostException(message: String = "Connection lost", cause: Throwable? = null) :
    NetworkException(message, cause)

/**
 * Thrown by [com.sza.fastmediasorter.core.network.NetworkReachabilityGate.requireWifi] when a
 * Wi-Fi (or ethernet) transport is required but not currently active. Distinct from a general
 * [NetworkConnectionLostException]: the Wi-Fi gate fires *before* any socket attempt, meaning
 * the server was never contacted. UI should explain the Wi-Fi requirement, not a generic outage.
 */
class WifiRequiredException(
    message: String = "Wi-Fi required",
    cause: Throwable? = null
) : NetworkConnectionLostException(message, cause)

/**
 * Unsupported protocol or operation
 */
class NetworkUnsupportedOperationException(message: String = "Unsupported operation", cause: Throwable? = null) : 
    NetworkException(message, cause)

/**
 * HTTP 5xx server-side errors
 */
class NetworkServerErrorException(val statusCode: Int = 500, message: String = "Server error", cause: Throwable? = null) :
    NetworkException("HTTP $statusCode: $message", cause)

/**
 * OS blocked the socket because ACCESS_LOCAL_NETWORK permission is not granted (Android 17+).
 * Distinct from [NetworkAccessDeniedException] which covers auth/ACL failures.
 */
class LocalNetworkPermissionDeniedException(
    message: String = "Local network access permission not granted",
    cause: Throwable? = null
) : NetworkException(message, cause)

/**
 * HTTP 429 Rate limit exceeded; may carry Retry-After delay hint (seconds).
 */
class NetworkRateLimitException(
    val retryAfterSeconds: Long? = null,
    message: String = "Rate limit exceeded",
    cause: Throwable? = null
) : NetworkException(message, cause)

/**
 * A media-folder scan exceeded its application-level watchdog budget and was force-aborted
 * (the underlying socket was closed to unblock a parked listing). Distinct from
 * [NetworkTimeoutException] (a per-connection timeout): this is the whole-scan backstop. The
 * "timed out" substring is intentional so message-based error mappers still classify it as a
 * timeout if a typed branch is missed.
 */
class ScanTimeoutException(
    resourceName: String,
    cause: Throwable? = null
) : NetworkException("Scan for '$resourceName' timed out", cause)
