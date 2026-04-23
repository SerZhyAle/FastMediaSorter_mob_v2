package com.sza.fastmediasorter.data.network

import com.hierynomus.smbj.share.DiskShare
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import timber.log.Timber

/**
 * Pure helpers for SmbClient: classify SMB exceptions into user-friendly messages,
 * build a one-shot diagnostic blob with connection details, and create the parent chain
 * of directories on a DiskShare in a race-tolerant way.
 *
 * Extracted to keep SmbClient below the 1000-line cap.
 */
object SmbClientErrorFormatter {

    /** Map an SMB exception to a multi-line user-facing message; falls back to a generic line. */
    fun getUserFriendlyMessage(exception: Exception): String {
        val message = exception.message ?: ""
        val causeMessage = exception.cause?.message ?: ""
        val rootCauseMessage = exception.cause?.cause?.message ?: ""

        return when {
            // Connection reset errors
            message.contains("Connection reset", ignoreCase = true) ||
                causeMessage.contains("Connection reset", ignoreCase = true) ||
                rootCauseMessage.contains("Connection reset", ignoreCase = true) ->
                """Connection interrupted by server. This is usually temporary.
                |
                |Possible causes:
                |• Server restarted or network equipment reset
                |• Firewall dropped the connection
                |• Too many simultaneous connections
                |• SMB protocol version mismatch
                |
                |Try:
                |• Wait a moment and try again
                |• Check if server is accessible from other devices
                |• Verify SMB settings on server""".trimMargin()

            // Timeout errors
            message.contains("TimeoutException", ignoreCase = true) ||
                message.contains("Timeout expired", ignoreCase = true) ||
                causeMessage.contains("TimeoutException", ignoreCase = true) ||
                causeMessage.contains("Timeout expired", ignoreCase = true) ->
                """Connection timeout. Server not responding or network is very slow.
                |
                |This can happen with:
                |• Slow network connection
                |• Server under heavy load
                |• Firewall blocking traffic
                |• Wrong server address
                |
                |Try:
                |• Check network connection
                |• Verify server is online
                |• Wait a moment and try again""".trimMargin()

            // Network errors
            message.contains("STATUS_BAD_NETWORK_NAME", ignoreCase = true) ->
                """Share not found on server.
                |
                |Possible reasons:
                |• Share name is incorrect or doesn't exist
                |• Share was renamed or removed
                |• Share is hidden (hidden$ shares need exact name)
                |
                |Try:
                |• Use 'Discover SMB Resources' to see available shares
                |• Check share name on the server
                |• Verify share is enabled and visible""".trimMargin()
            message.contains("STATUS_LOGON_FAILURE", ignoreCase = true) ->
                "Authentication failed. Check username and password."
            message.contains("STATUS_ACCESS_DENIED", ignoreCase = true) ->
                "Access denied. Check share permissions."
            message.contains("ConnectException", ignoreCase = true) ||
                message.contains("NoRouteToHostException", ignoreCase = true) ->
                "Cannot reach server. Check network connection."
            message.contains("SocketTimeoutException", ignoreCase = true) ->
                "Connection timeout. Server not responding."
            message.contains("UnknownHostException", ignoreCase = true) ->
                "Server address not found. Check server name/IP."

            else -> "Resource unavailable. Check connection settings."
        }
    }

    /** Diagnostic blob with connection details + exception class/message + common solutions. */
    fun buildDiagnosticMessage(exception: Exception, connectionInfo: SmbConnectionInfo): String {
        val sb = StringBuilder()
        sb.append("=== SMB CONNECTION DIAGNOSTIC ===\n")
        sb.append("Server: ${connectionInfo.server}:${connectionInfo.port}\n")
        sb.append("Share: ${connectionInfo.shareName}\n")
        sb.append("Username: ${if (connectionInfo.username.isEmpty()) "anonymous" else connectionInfo.username}\n")
        sb.append("\nError: ${exception.javaClass.simpleName}\n")
        sb.append("Message: ${exception.message}\n")

        sb.append("\nCommon solutions:\n")
        sb.append("• Verify server address is correct\n")
        sb.append("• Check network connectivity\n")
        sb.append("• Ensure SMB port ${connectionInfo.port} is not blocked\n")
        sb.append("• Verify username and password\n")
        sb.append("• Check share name and permissions\n")
        sb.append("• Ensure SMB2/SMB3 is enabled on server\n")

        return sb.toString()
    }

    /**
     * Recursively create the parent chain of [path] on [share]. Tolerates races where another
     * process creates the directory between folderExists() and mkdir() (re-checks after exception).
     */
    fun ensureSmbDirectoryExists(share: DiskShare, path: String) {
        val parts = path.replace('\\', '/').split('/').filter { it.isNotEmpty() }
        var currentPath = ""
        for (part in parts) {
            currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
            if (!share.folderExists(currentPath)) {
                Timber.d("ensureSmbDirectoryExists: Creating $currentPath")
                try {
                    share.mkdir(currentPath)
                    Timber.d("ensureSmbDirectoryExists: Successfully created $currentPath")
                } catch (e: Exception) {
                    // Race: directory may have been created by another process between check and mkdir.
                    if (!share.folderExists(currentPath)) {
                        Timber.e(e, "ensureSmbDirectoryExists: Failed to create $currentPath")
                        throw e
                    } else {
                        Timber.d("ensureSmbDirectoryExists: Directory $currentPath already exists")
                    }
                }
            }
        }
    }
}
