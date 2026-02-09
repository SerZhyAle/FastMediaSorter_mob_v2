package com.sza.fastmediasorter.domain.transfer

/**
 * Utility object for formatting file operation error messages consistently across all handlers.
 * 
 * Provides standardized error message formatting for copy, move, and delete operations.
 * All error messages follow the format:
 * ```
 * fileName
 *   From: sourcePath
 *   To: destinationPath
 *   Error: errorMessage
 * ```
 */
object FileOperationError {
    
    /**
     * Formats an error message for copy or move operation failures.
     * 
     * @param fileName The name of the file being operated on
     * @param sourcePath The source file path or URI
     * @param destinationPath The destination file path or URI
     * @param errorMessage The specific error message describing what went wrong
     * @return A formatted multi-line error string
     */
    fun formatTransferError(
        fileName: String,
        sourcePath: String,
        destinationPath: String,
        errorMessage: String
    ): String = buildString {
        append(fileName)
        append("\n  From: ")
        append(sourcePath)
        append("\n  To: ")
        append(destinationPath)
        append("\n  Error: ")
        append(errorMessage)
    }
    
    /**
     * Formats an error message for delete operation failures.
     * 
     * @param fileName The name of the file being deleted
     * @param filePath The path or URI of the file
     * @param errorMessage The specific error message describing what went wrong
     * @return A formatted multi-line error string
     */
    fun formatDeleteError(
        fileName: String,
        filePath: String,
        errorMessage: String
    ): String = buildString {
        append(fileName)
        append("\n  Path: ")
        append(filePath)
        append("\n  Error: ")
        append(errorMessage)
    }
    
    /**
     * Extracts a clean error message from an exception.
     * Combines exception class name with message for better debugging.
     * 
     * @param exception The exception that occurred
     * @return A clean error message string
     */
    fun extractErrorMessage(exception: Exception): String {
        return "${exception.javaClass.simpleName} - ${exception.message ?: "Unknown error"}"
    }
}
