package com.sza.fastmediasorter.core.util

import android.content.Context
import com.sza.fastmediasorter.R
import timber.log.Timber

/**
 * Utility for formatting file operation error messages in a user-friendly way.
 * Cleans technical details and provides clear, actionable feedback.
 */
object FileOperationErrorFormatter {

    /**
     * Format a file operation error message for display to the user.
     * 
     * @param context Android context for string resources
     * @param fileName Name of the file that failed
     * @param operation Operation type: "copy", "move", "delete"
     * @param errorMessage Raw error message from the operation
     * @param showDetailedErrors Whether to show detailed technical information
     * @param sourcePath Optional source path (shown only in detailed mode)
     * @param destPath Optional destination path (shown only in detailed mode)
     * @return User-friendly formatted error message
     */
    fun formatError(
        context: Context,
        fileName: String,
        operation: String,
        errorMessage: String,
        showDetailedErrors: Boolean = false,
        sourcePath: String? = null,
        destPath: String? = null
    ): String {
        // Clean the error message from technical junk
        val cleanMessage = cleanErrorMessage(errorMessage)
        
        // Extract key error type
        val errorType = detectErrorType(cleanMessage)
        
        // Build user-friendly message
        return buildString {
            // Main header with emoji for visual clarity
            append("❌ ")
            when (operation.lowercase()) {
                "copy" -> append(context.getString(R.string.error_operation_title_copy))
                "move" -> append(context.getString(R.string.error_operation_title_move))
                "delete" -> append(context.getString(R.string.error_operation_title_delete))
                else -> append(context.getString(R.string.error_operation_failed))
            }
            append("\n")
            
            // File name (always shown)
            append("📄 ").append(fileName).append("\n\n")
            
            // User-friendly reason
            append("💡 ").append(getUserFriendlyReason(context, errorType, cleanMessage))
            
            if (showDetailedErrors) {
                // Show paths if available
                if (sourcePath != null) {
                    append("\n\n📂 ${context.getString(R.string.source_path)}:\n")
                    append(shortenPath(sourcePath))
                }
                if (destPath != null) {
                    append("\n\n📁 ${context.getString(R.string.destination_path)}:\n")
                    append(shortenPath(destPath))
                }
                
                // Technical details (cleaned)
                append("\n\n🔧 ${context.getString(R.string.technical_details)}:\n")
                append(cleanMessage)
            }
        }
    }

    /**
     * Format multiple file operation errors into a summary.
     */
    fun formatMultipleErrors(
        context: Context,

        totalCount: Int,
        successCount: Int,
        errors: List<String>,
        showDetailedErrors: Boolean = false
    ): String {
        val failedCount = errors.size
        
        return buildString {
            if (failedCount == totalCount) {
                // All failed - more dramatic
                append("❌ ${context.getString(R.string.error_all_operations_failed)}\n\n")
            } else {
                // Partial success
                append("⚠️ ${context.getString(R.string.error_partial_success)}\n\n")
                append("✅ ${context.getString(R.string.success_count, successCount)}\n")
                append("❌ ${context.getString(R.string.failed_count, failedCount)}\n\n")
            }
            
            if (showDetailedErrors) {
                append("${context.getString(R.string.failed_files)}:\n")
                errors.take(10).forEach { error ->
                    append("\n• ").append(cleanErrorMessage(error))
                }
                if (errors.size > 10) {
                    append("\n\n... ${context.getString(R.string.and_more_errors, errors.size - 10)}")
                }
            } else {
                // Just show first few file names without details
                append("${context.getString(R.string.failed_files)}:\n")
                errors.take(5).forEach { error ->
                    // Extract just the file name
                    val fileName = error.lines().firstOrNull()?.trim() ?: error.take(50)
                    append("• ").append(fileName).append("\n")
                }
                if (errors.size > 5) {
                    append("\n${context.getString(R.string.and_more_files, errors.size - 5)}")
                }
            }
        }
    }

    /**
     * Clean error message from technical junk while preserving useful information.
     */
    private fun cleanErrorMessage(message: String): String {
        var cleaned = message
        
        // Remove Java class references (e.g., "using com.hierynomus.smbj.auth.NtlmSealer@69fdbb2")
        cleaned = cleaned.replace(Regex("using [a-zA-Z0-9.]+@[a-zA-Z0-9]+"), "")
        
        // Remove hex codes in parentheses (e.g., "(0xc000006d)")
        cleaned = cleaned.replace(Regex("\\(0x[0-9a-fA-F]+\\)"), "")
        
        // Remove package names (e.g., "com.hierynomus.mssmb2.SMBApiException:")
        cleaned = cleaned.replace(Regex("[a-z0-9.]+\\.[A-Z][a-zA-Z0-9]+Exception:\\s*"), "")
        
        // Extract STATUS codes if present (e.g., "STATUS_LOGON_FAILURE")
        val statusMatch = Regex("STATUS_[A-Z_]+").find(cleaned)
        if (statusMatch != null) {
            cleaned = statusMatch.value.replace("_", " ").lowercase()
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
        
        // Clean up whitespace
        cleaned = cleaned.trim().replace(Regex("\\s+"), " ")
        
        return cleaned
    }

    /**
     * Detect error type from cleaned message.
     */
    private fun detectErrorType(cleanMessage: String): ErrorType {
        val lower = cleanMessage.lowercase()
        
        return when {
            lower.contains("authentication") || lower.contains("logon failure") -> ErrorType.AUTHENTICATION
            lower.contains("permission") || lower.contains("access denied") -> ErrorType.PERMISSION
            lower.contains("not found") || lower.contains("does not exist") -> ErrorType.NOT_FOUND
            lower.contains("exists") && lower.contains("already") -> ErrorType.ALREADY_EXISTS
            lower.contains("network") || lower.contains("connection") -> ErrorType.NETWORK
            lower.contains("timeout") || lower.contains("timed out") -> ErrorType.TIMEOUT
            lower.contains("disk") || lower.contains("space") -> ErrorType.DISK_SPACE
            lower.contains("read only") || lower.contains("readonly") -> ErrorType.READ_ONLY
            lower.contains("invalid") || lower.contains("illegal") -> ErrorType.INVALID_NAME
            else -> ErrorType.UNKNOWN
        }
    }

    /**
     * Get user-friendly explanation for the error type.
     */
    private fun getUserFriendlyReason(context: Context, errorType: ErrorType, cleanMessage: String): String {
        return when (errorType) {
            ErrorType.AUTHENTICATION -> context.getString(R.string.error_reason_authentication)
            ErrorType.PERMISSION -> context.getString(R.string.error_reason_permission)
            ErrorType.NOT_FOUND -> context.getString(R.string.error_reason_not_found)
            ErrorType.ALREADY_EXISTS -> context.getString(R.string.error_reason_already_exists)
            ErrorType.NETWORK -> context.getString(R.string.error_reason_network)
            ErrorType.TIMEOUT -> context.getString(R.string.error_reason_timeout)
            ErrorType.DISK_SPACE -> context.getString(R.string.error_reason_disk_space)
            ErrorType.READ_ONLY -> context.getString(R.string.error_reason_read_only)
            ErrorType.INVALID_NAME -> context.getString(R.string.error_reason_invalid_name)
            ErrorType.UNKNOWN -> {
                // If we have a cleaned message, show it, otherwise generic message
                if (cleanMessage.isNotBlank() && cleanMessage.length < 100) {
                    cleanMessage
                } else {
                    context.getString(R.string.error_reason_unknown)
                }
            }
        }
    }

    /**
     * Shorten long paths for display (show start and end).
     */
    private fun shortenPath(path: String, maxLength: Int = 80): String {
        if (path.length <= maxLength) return path
        
        val half = (maxLength - 3) / 2
        return path.take(half) + "..." + path.takeLast(half)
    }

    private enum class ErrorType {
        AUTHENTICATION,
        PERMISSION,
        NOT_FOUND,
        ALREADY_EXISTS,
        NETWORK,
        TIMEOUT,
        DISK_SPACE,
        READ_ONLY,
        INVALID_NAME,
        UNKNOWN
    }
}
