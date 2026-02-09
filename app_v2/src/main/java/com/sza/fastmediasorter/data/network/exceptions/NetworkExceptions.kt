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
