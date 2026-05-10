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
