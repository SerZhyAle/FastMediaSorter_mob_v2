package com.sza.fastmediasorter.util

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.ui.common.copy.UiMessageFamily
import com.sza.fastmediasorter.ui.common.copy.UiMessageSpec
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Utility for formatting connection errors in user-friendly way with localization
 */
object ConnectionErrorFormatter {
    
    /**
     * Format connection error with localized message and connection details
     * @param context Android context for string resources
     * @param resource Resource that failed to connect
     * @param error Original exception
     * @param showTechnicalDetails Whether to include technical details (from settings)
     * @return Pair of (user message, technical details or null)
     */
    fun formatConnectionError(
        context: Context,
        resource: MediaResource,
        error: Throwable,
        showTechnicalDetails: Boolean
    ): Pair<String, String?> {
        
        // Extract root cause
        val rootCause = generateSequence(error) { it.cause }.lastOrNull() ?: error
        
        // User-friendly message based on error type
        val userMessage = when {
            // Cloud authentication errors (Google Drive, OneDrive, Dropbox)
            resource.type == ResourceType.CLOUD && (
                rootCause.message?.contains("Not authenticated", ignoreCase = true) == true ||
                rootCause.message?.contains("Authentication expired", ignoreCase = true) == true ||
                rootCause.message?.contains("Token expired", ignoreCase = true) == true ||
                rootCause.message?.contains("401", ignoreCase = true) == true
            ) -> {
                // Specific cloud auth error with suggestion
                "${context.getString(R.string.error_cloud_auth_expired, resource.name)}\n\n" +
                context.getString(R.string.error_cloud_auth_suggestion)
            }
            // Cloud generic errors
            resource.type == ResourceType.CLOUD -> {
                context.getString(R.string.error_cloud_connection_failed, resource.name)
            }
            // SMB authentication errors
            rootCause.message?.contains("STATUS_LOGON_FAILURE", ignoreCase = true) == true ||
            rootCause.message?.contains("Authentication failed", ignoreCase = true) == true -> {
                context.getString(R.string.error_connection_auth, resource.name)
            }
            // SMB access errors
            rootCause.message?.contains("STATUS_ACCESS_DENIED", ignoreCase = true) == true -> {
                context.getString(R.string.error_connection_access, resource.name)
            }
            // SMB network errors
            rootCause.message?.contains("STATUS_BAD_NETWORK_NAME", ignoreCase = true) == true -> {
                context.getString(R.string.error_connection_share_not_found, resource.name)
            }
            // Generic network errors
            rootCause is SocketTimeoutException -> {
                context.getString(
                    R.string.error_connection_timeout,
                    resource.name,
                    extractServer(resource)
                )
            }
            rootCause is UnknownHostException -> {
                context.getString(
                    R.string.error_host_not_found,
                    resource.name,
                    extractServer(resource)
                )
            }
            rootCause is java.net.ConnectException -> {
                context.getString(
                    R.string.error_connection_refused,
                    resource.name,
                    extractServer(resource)
                )
            }
            else -> {
                context.getString(
                    R.string.error_connection_failed_generic,
                    resource.name
                )
            }
        }
        
        // Technical details (only if enabled)
        val technicalDetails = if (showTechnicalDetails) {
            buildTechnicalDetails(context, resource, rootCause)
        } else {
            null
        }
        
        return Pair(userMessage, technicalDetails)
    }

    /**
     * S0118: Wrap [formatConnectionError] output as a [UiMessageSpec] so callers can
     * route through [com.sza.fastmediasorter.ui.common.copy.UiMessageProjector] without
     * juggling a `Pair<String, String?>` at every error surface.
     */
    fun formatAsSpec(
        context: Context,
        resource: MediaResource,
        error: Throwable,
        showTechnicalDetails: Boolean,
    ): UiMessageSpec {
        val (short, detailed) = formatConnectionError(context, resource, error, showTechnicalDetails)
        return UiMessageSpec(
            family = UiMessageFamily.ERROR,
            shortMessage = short,
            detailedMessage = detailed,
        )
    }

    private fun buildTechnicalDetails(
        context: Context,
        resource: MediaResource,
        error: Throwable
    ): String {
        val sb = StringBuilder()
        
        // Resource info
        sb.append(context.getString(R.string.error_details_resource))
        sb.append(": ${resource.name} (${formatResourceType(context, resource.type)})\n")
        
        // Connection info
        sb.append(context.getString(R.string.error_details_path))
        sb.append(": ${resource.path}\n")
        
        // Protocol-specific details
        when (resource.type) {
            ResourceType.SMB -> {
                val server = extractServer(resource)
                val port = extractPort(resource) ?: 445
                sb.append(context.getString(R.string.error_details_server))
                sb.append(": $server\n")
                sb.append(context.getString(R.string.error_details_port))
                sb.append(": $port\n")
            }
            ResourceType.SFTP, ResourceType.FTP -> {
                val server = extractServer(resource)
                val port = extractPort(resource) ?: if (resource.type == ResourceType.SFTP) 22 else 21
                sb.append(context.getString(R.string.error_details_server))
                sb.append(": $server\n")
                sb.append(context.getString(R.string.error_details_port))
                sb.append(": $port\n")
            }
            else -> {}
        }
        
        // Error type
        sb.append("\n")
        sb.append(context.getString(R.string.error_details_type))
        sb.append(": ${error.javaClass.simpleName}\n")
        
        // Error message (cleaned up)
        error.message?.let { rawMessage ->
            sb.append(context.getString(R.string.error_details_message))
            sb.append(": ")
            sb.append(cleanErrorMessage(rawMessage))
            sb.append("\n")
        }
        
        // Timeout info if applicable
        if (error is SocketTimeoutException) {
            extractTimeout(error.message)?.let { timeout ->
                sb.append(context.getString(R.string.error_details_timeout))
                sb.append(": ${timeout}ms\n")
            }
        }
        
        return sb.toString().trim()
    }
    
    private fun formatResourceType(context: Context, type: ResourceType): String {
        return when (type) {
            ResourceType.LOCAL -> context.getString(R.string.resource_type_local)
            ResourceType.SMB -> "SMB"
            ResourceType.SFTP -> "SFTP"
            ResourceType.FTP -> "FTP"
            ResourceType.CLOUD -> context.getString(R.string.resource_type_cloud)
            ResourceType.HTTP_STREAM -> "HTTP"
            ResourceType.RTSP_STREAM -> "RTSP"
            ResourceType.WEAR_WATCH -> context.getString(R.string.resource_type_wear_watch)
        }
    }
    
    /**
     * Extract server/host from resource path
     * Examples:
     * - smb://192.168.1.112/share → 192.168.1.112
     * - sftp://user@example.com:22/path → example.com
     */
    private fun extractServer(resource: MediaResource): String {
        val path = resource.path
        
        // Handle protocol://server/path format
        val regex = """^[a-z]+://(?:[^@]+@)?([^:/]+)""".toRegex()
        val match = regex.find(path)
        return match?.groupValues?.get(1) ?: path
    }
    
    /**
     * Extract port from resource path or error message
     */
    private fun extractPort(resource: MediaResource): Int? {
        val path = resource.path
        
        // Try to extract from path like smb://server:445/share
        val regex = """^[a-z]+://(?:[^@]+@)?[^:/]+:(\d+)""".toRegex()
        val match = regex.find(path)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }
    
    /**
     * Extract timeout value from SocketTimeoutException message
     * Example: "failed to connect ... after 5000ms" → 5000
     */
    private fun extractTimeout(message: String?): Int? {
        if (message == null) return null
        
        val regex = """after (\d+)ms""".toRegex()
        val match = regex.find(message)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }
    
    /**
     * Clean up error message by removing technical Java details
     * Removes: class names with @hashcode, package names, stack trace elements
     */
    private fun cleanErrorMessage(rawMessage: String): String {
        var cleaned = rawMessage
        
        // Remove Java class references with memory addresses like "NtlmSealer@69fdbb2"
        cleaned = cleaned.replace(Regex("""using [a-zA-Z0-9.$]+@[a-f0-9]+"""), "")
        
        // Remove multiple spaces and trim
        cleaned = cleaned.replace(Regex("""\s+"""), " ").trim()
        
        // Extract just the status code and main message for SMB errors
        if (cleaned.contains("STATUS_")) {
            val statusMatch = Regex("""(STATUS_\w+)\s*\(0x[0-9a-f]+\):\s*(.+?)(?:\s+using)?""").find(cleaned)
            if (statusMatch != null) {
                val statusCode = statusMatch.groupValues[1]
                val message = statusMatch.groupValues[2]
                
                // Human-readable status messages
                cleaned = when (statusCode) {
                    "STATUS_LOGON_FAILURE" -> "Authentication failed (incorrect username or password)"
                    "STATUS_ACCESS_DENIED" -> "Access denied (check permissions)"
                    "STATUS_BAD_NETWORK_NAME" -> "Share not found"
                    else -> "$statusCode: $message"
                }
            }
        }
        
        return cleaned
    }
}
