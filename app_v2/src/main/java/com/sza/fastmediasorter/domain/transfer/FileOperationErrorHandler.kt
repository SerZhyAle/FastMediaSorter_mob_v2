package com.sza.fastmediasorter.domain.transfer

import android.content.Context
import com.sza.fastmediasorter.R
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized error handling for file operations.
 * Translates technical exceptions into user-friendly messages.
 */
@Singleton
class FileOperationErrorHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    /**
     * Convert exception to user-friendly error message with context.
     * 
     * @param throwable Exception to handle
     * @param operation Operation type (copy, move, delete, rename)
     * @param sourcePath Source file path (optional, for context)
     * @param destPath Destination path (optional, for context)
     * @return User-friendly error message
     */
    fun handleError(
        throwable: Throwable,
        operation: String,
        sourcePath: String? = null,
        destPath: String? = null
    ): String {
        val baseMessage = translateException(throwable, operation)
        
        // Add context if available
        val context = buildContextMessage(sourcePath, destPath)
        
        val fullMessage = if (context.isNotEmpty()) {
            "$baseMessage\n$context"
        } else {
            baseMessage
        }
        
        // Log detailed error
        Timber.e(throwable, "FileOperation error: $operation | Source: $sourcePath | Dest: $destPath")
        
        return fullMessage
    }
    
    /**
     * Translate exception to user-friendly message.
     */
    private fun translateException(throwable: Throwable, operation: String): String {
        return when (throwable) {
            // Network errors
            is UnknownHostException -> 
                "Network error: Cannot resolve host"
            
            is SocketTimeoutException -> 
                "Network error: Connection timeout"
            
            is IOException -> {
                if (throwable.message?.contains("No space left", ignoreCase = true) == true) {
                    "Insufficient disk space"
                } else if (throwable.message?.contains("Permission denied", ignoreCase = true) == true) {
                    "Permission denied"
                } else {
                    "Network error during $operation: ${throwable.message ?: "Unknown"}"
                }
            }
            
            // File system errors
            is FileNotFoundException -> 
                "File not found: ${throwable.message ?: "Unknown"}"
            
            is SecurityException -> 
                "Permission denied"
            
            // SMB-specific errors
            is Exception -> {
                when {
                    throwable.message?.contains("SMBApiException", ignoreCase = true) == true ->
                        "SMB protocol error: ${extractSmbError(throwable.message)}"
                    
                    throwable.message?.contains("Authentication", ignoreCase = true) == true ->
                        "Authentication failed"
                    
                    throwable.message?.contains("Connection", ignoreCase = true) == true ->
                        "Connection failed"
                    
                    else -> 
                        "Error during $operation: ${throwable.message ?: throwable.javaClass.simpleName}"
                }
            }
            
            else -> 
                "Unexpected error during $operation: ${throwable.message ?: throwable.javaClass.simpleName}"
        }
    }
    
    /**
     * Extract meaningful error from SMB exception message.
     */
    private fun extractSmbError(message: String?): String {
        if (message == null) return "Unknown"
        
        // Extract status code if present (e.g., "STATUS_ACCESS_DENIED")
        val statusMatch = Regex("STATUS_([A-Z_]+)").find(message)
        if (statusMatch != null) {
            val status = statusMatch.groupValues[1]
            return when (status) {
                "ACCESS_DENIED" -> "Access denied"
                "OBJECT_NAME_NOT_FOUND" -> "File or folder not found"
                "OBJECT_PATH_NOT_FOUND" -> "Path not found"
                "SHARING_VIOLATION" -> "File is in use"
                "DISK_FULL" -> "Disk full"
                else -> status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
            }
        }
        
        return message.take(100) // Truncate long messages
    }
    
    /**
     * Build context message from paths.
     */
    private fun buildContextMessage(sourcePath: String?, destPath: String?): String {
        val parts = mutableListOf<String>()
        
        sourcePath?.let { 
            val fileName = it.substringAfterLast('/')
            if (fileName.isNotEmpty()) {
                parts.add("File: $fileName")
            }
        }
        
        destPath?.let {
            val destName = it.substringAfterLast('/')
                .substringBeforeLast('/') // Get parent folder
            if (destName.isNotEmpty()) {
                parts.add("Destination: $destName")
            }
        }
        
        return parts.joinToString(" | ")
    }
    
    /**
     * Check if error is recoverable (can retry).
     */
    fun isRecoverableError(throwable: Throwable): Boolean {
        return when (throwable) {
            is SocketTimeoutException -> true
            is IOException -> {
                // Network I/O errors often recoverable
                throwable.message?.let { msg ->
                    !msg.contains("No space left", ignoreCase = true) &&
                    !msg.contains("Permission denied", ignoreCase = true)
                } ?: true
            }
            else -> false
        }
    }
    
    /**
     * Get suggested action for error.
     */
    fun getSuggestedAction(throwable: Throwable): String? {
        return when (throwable) {
            is UnknownHostException -> 
                "Check network connection and server address"
            
            is SocketTimeoutException -> 
                "Check network speed and try again"
            
            is SecurityException -> 
                "Grant required permissions in app settings"
            
            is IOException -> {
                when {
                    throwable.message?.contains("No space left", ignoreCase = true) == true ->
                        "Free up disk space and try again"
                    
                    throwable.message?.contains("Permission denied", ignoreCase = true) == true ->
                        "Check folder permissions"
                    
                    else -> null
                }
            }
            
            else -> null
        }
    }
}
