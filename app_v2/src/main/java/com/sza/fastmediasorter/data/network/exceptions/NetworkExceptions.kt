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
class NetworkConnectionLostException(message: String = "Connection lost", cause: Throwable? = null) : 
    NetworkException(message, cause)

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
 * HTTP 429 Rate limit exceeded; may carry Retry-After delay hint (seconds).
 */
class NetworkRateLimitException(
    val retryAfterSeconds: Long? = null,
    message: String = "Rate limit exceeded",
    cause: Throwable? = null
) : NetworkException(message, cause)
