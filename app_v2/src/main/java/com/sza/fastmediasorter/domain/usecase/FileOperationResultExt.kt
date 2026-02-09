package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.core.util.FileOperationErrorFormatter
import com.sza.fastmediasorter.domain.model.AppSettings

/**
 * Extension function to format FileOperationResult errors in a user-friendly way.
 * Uses FileOperationErrorFormatter to clean technical details and provide clear messages.
 */
fun FileOperationResult.formatUserFriendlyMessage(
    context: Context,
    settings: AppSettings
): String {
    return when (this) {
        is FileOperationResult.Success -> {
            // Success messages don't need formatting
            when (operation) {
                is FileOperation.Copy -> context.getString(com.sza.fastmediasorter.R.string.copied_n_file_s, processedCount)
                is FileOperation.Move -> context.getString(com.sza.fastmediasorter.R.string.moved_n_file_s, processedCount)
                is FileOperation.Delete -> context.getString(com.sza.fastmediasorter.R.string.deleted_n_file_s, processedCount)
                is FileOperation.Rename -> context.getString(com.sza.fastmediasorter.R.string.file_renamed_successfully)
            }
        }
        
        is FileOperationResult.PartialSuccess -> {
            FileOperationErrorFormatter.formatMultipleErrors(
                context = context,
                totalCount = processedCount + failedCount,
                successCount = processedCount,
                errors = errors,
                showDetailedErrors = settings.showDetailedErrors
            )
        }
        
        is FileOperationResult.Failure -> {
            // Determine operation type from the error message or provide generic one
            val operationType = when {
                error.contains("copy", ignoreCase = true) -> "copy"
                error.contains("move", ignoreCase = true) -> "move"
                error.contains("delete", ignoreCase = true) -> "delete"
                error.contains("rename", ignoreCase = true) -> "rename"
                else -> "operation"
            }
            
            // Extract file name if present in error message
            val fileName = extractFileNameFromError(error)
            
            if (fileName != null) {
                FileOperationErrorFormatter.formatError(
                    context = context,
                    fileName = fileName,
                    operation = operationType,
                    errorMessage = error,
                    showDetailedErrors = settings.showDetailedErrors
                )
            } else {
                // No file name - just clean the error message
                if (settings.showDetailedErrors) {
                    "❌ ${error}"
                } else {
                    // Clean technical details from error
                    val cleanedError = FileOperationErrorFormatter.cleanErrorMessage(error)
                    "❌ ${cleanedError}"
                }
            }
        }
        
        is FileOperationResult.AuthenticationRequired -> {
            "🔐 Authentication required for $provider:\n\n$message"
        }
        
        is FileOperationResult.PermissionRequired -> {
            context.getString(com.sza.fastmediasorter.R.string.error_delete_permission_required)
        }
    }
}

/**
 * Try to extract file name from error message patterns.
 */
private fun extractFileNameFromError(error: String): String? {
    // Try to extract file name from common patterns:
    // "Failed to ... file.txt"
    // "File file.txt ..."
    // "... file: file.txt"
    
    val patterns = listOf(
        Regex("""File:\s*([^\n:]+)""", RegexOption.IGNORE_CASE),
        Regex("""file\s+([^\s:]+)""", RegexOption.IGNORE_CASE),
        Regex("""'([^']+)'"""),
        Regex("""\"([^\"]+)\""""),
        Regex("""([^\s/\\]+\.\w{2,4})""") // filename.ext pattern
    )
    
    for (pattern in patterns) {
        val match = pattern.find(error)
        if (match != null && match.groupValues.size > 1) {
            val fileName = match.groupValues[1].trim()
            // Validate it looks like a filename (has extension and not too long)
            if (fileName.length < 100 && fileName.contains('.')) {
                return fileName
            }
        }
    }
    
    return null
}

/**
 * Private helper to actually clean error messages.
 * This is a copy from FileOperationErrorFormatter made accessible here.
 */
private fun FileOperationErrorFormatter.cleanErrorMessage(message: String): String {
    var cleaned = message
    
    // Remove Java class references
    cleaned = cleaned.replace(Regex("using [a-zA-Z0-9.]+@[a-zA-Z0-9]+"), "")
    
    // Remove hex codes in parentheses
    cleaned = cleaned.replace(Regex("\\(0x[0-9a-fA-F]+\\)"), "")
    
    // Remove package names
    cleaned = cleaned.replace(Regex("[a-z0-9.]+\\.[A-Z][a-zA-Z0-9]+Exception:\\s*"), "")
    
    // Extract STATUS codes if present
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
