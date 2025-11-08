package com.sza.fastmediasorter_v2.data.network

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMB/CIFS client for network file operations using SMBJ library.
 * Provides connection management, authentication, file listing, and data transfer
 * capabilities for accessing remote SMB shares.
 * 
 * Supports SMB2/SMB3 protocols.
 */
@Singleton
class SmbClient @Inject constructor() {
    
    companion object {
        private const val CONNECTION_TIMEOUT_MS = 30000L
        private const val READ_TIMEOUT_MS = 30000L
        private const val WRITE_TIMEOUT_MS = 30000L
    }

    private val config = SmbConfig.builder()
        .withTimeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .withSoTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .withMultiProtocolNegotiate(true)
        .build()

    private val client = SMBClient(config)
    
    /**
     * Data class for SMB connection parameters
     */
    data class SmbConnectionInfo(
        val server: String,
        val shareName: String,
        val username: String = "",
        val password: String = "",
        val domain: String = "",
        val port: Int = 445
    )
    
    /**
     * Data class for file information
     */
    data class SmbFileInfo(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long
    )
    
    /**
     * Result wrapper for operations
     */
    sealed class SmbResult<out T> {
        data class Success<T>(val data: T) : SmbResult<T>()
        data class Error(val message: String, val exception: Exception? = null) : SmbResult<Nothing>()
    }

    /**
     * Test connection to SMB server
     * - If shareName is empty: tests server accessibility and lists available shares
     * - If shareName is provided: tests share accessibility and provides folder/file statistics
     */
    suspend fun testConnection(connectionInfo: SmbConnectionInfo): SmbResult<String> {
        return try {
            if (connectionInfo.shareName.isEmpty()) {
                // Test server only - list available shares
                val sharesResult = listShares(
                    connectionInfo.server,
                    connectionInfo.username,
                    connectionInfo.password,
                    connectionInfo.domain,
                    connectionInfo.port
                )
                
                when (sharesResult) {
                    is SmbResult.Success -> {
                        val sharesList = sharesResult.data.joinToString("\n• ", prefix = "• ")
                        val message = """
                            |✓ Server accessible: ${connectionInfo.server}
                            |
                            |Available shares (${sharesResult.data.size}):
                            |$sharesList
                        """.trimMargin()
                        SmbResult.Success(message)
                    }
                    is SmbResult.Error -> sharesResult
                }
            } else {
                // Test specific share - provide detailed statistics
                withConnection(connectionInfo) { share ->
                    // Count folders and media files in root
                    val files = share.list("").filter { !it.fileName.startsWith(".") }
                    val folders = files.count { (it.fileAttributes and 0x10L) != 0L }
                    val mediaFiles = files.filter { file ->
                        val ext = file.fileName.substringAfterLast('.', "").lowercase()
                        ext in setOf("jpg", "jpeg", "png", "gif", "bmp", "webp",
                                     "mp4", "mov", "avi", "mkv", "wmv", "flv", "webm",
                                     "mp3", "wav", "aac", "flac", "ogg", "m4a")
                    }
                    
                    val message = """
                        |✓ Share accessible: ${connectionInfo.server}\${connectionInfo.shareName}
                        |
                        |Share statistics:
                        |• Subfolders: $folders
                        |• Media files in root: ${mediaFiles.size}
                        |• Total items: ${files.size}
                    """.trimMargin()
                    
                    SmbResult.Success(message)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SMB connection test failed")
            SmbResult.Error(
                buildDiagnosticMessage(e, connectionInfo),
                e
            )
        }
    }

    /**
     * List files and folders in SMB directory
     */
    suspend fun listFiles(
        connectionInfo: SmbConnectionInfo,
        remotePath: String = ""
    ): SmbResult<List<SmbFileInfo>> {
        return try {
            withConnection(connectionInfo) { share ->
                val files = mutableListOf<SmbFileInfo>()
                val dirPath = if (remotePath.isEmpty()) "" else remotePath.trim('/', '\\')
                
                for (fileInfo in share.list(dirPath)) {
                    if (fileInfo.fileName == "." || fileInfo.fileName == "..") continue
                    
                    val fullPath = if (dirPath.isEmpty()) {
                        fileInfo.fileName
                    } else {
                        "$dirPath/${fileInfo.fileName}"
                    }
                    
                    files.add(
                        SmbFileInfo(
                            name = fileInfo.fileName,
                            path = fullPath,
                            isDirectory = fileInfo.fileAttributes and 0x10 != 0L, // FILE_ATTRIBUTE_DIRECTORY = 0x10
                            size = fileInfo.allocationSize,
                            lastModified = fileInfo.lastWriteTime.toEpochMillis()
                        )
                    )
                }
                SmbResult.Success(files)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to list SMB files")
            SmbResult.Error("Failed to list files: ${e.message}", e)
        }
    }

    /**
     * Scan SMB folder for media files (recursive)
     */
    suspend fun scanMediaFiles(
        connectionInfo: SmbConnectionInfo,
        remotePath: String = "",
        extensions: Set<String> = setOf("jpg", "jpeg", "png", "gif", "mp4", "mov", "avi", "mp3", "wav")
    ): SmbResult<List<SmbFileInfo>> {
        return try {
            val mediaFiles = mutableListOf<SmbFileInfo>()
            
            withConnection(connectionInfo) { share ->
                scanDirectoryRecursive(share, remotePath, extensions, mediaFiles)
                SmbResult.Success(mediaFiles)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to scan SMB media files")
            SmbResult.Error("Failed to scan media files: ${e.message}", e)
        }
    }

    private fun scanDirectoryRecursive(
        share: DiskShare,
        path: String,
        extensions: Set<String>,
        results: MutableList<SmbFileInfo>
    ) {
        try {
            val dirPath = path.trim('/', '\\')
            
            for (fileInfo in share.list(dirPath)) {
                if (fileInfo.fileName == "." || fileInfo.fileName == "..") continue
                
                val fullPath = if (dirPath.isEmpty()) {
                    fileInfo.fileName
                } else {
                    "$dirPath/${fileInfo.fileName}"
                }
                
                val isDirectory = fileInfo.fileAttributes and 0x10 != 0L // FILE_ATTRIBUTE_DIRECTORY = 0x10
                
                if (isDirectory) {
                    // Recursively scan subdirectories
                    scanDirectoryRecursive(share, fullPath, extensions, results)
                } else {
                    // Check if file has media extension
                    val extension = fileInfo.fileName.substringAfterLast('.', "").lowercase()
                    if (extension in extensions) {
                        results.add(
                            SmbFileInfo(
                                name = fileInfo.fileName,
                                path = fullPath,
                                isDirectory = false,
                                size = fileInfo.allocationSize,
                                lastModified = fileInfo.lastWriteTime.toEpochMillis()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to scan directory: $path")
        }
    }

    /**
     * List available shares on SMB server
     * 
     * SMBJ library limitations:
     * - No direct API for share enumeration
     * - Cannot use IPC$ administrative share to list shares (requires admin rights)
     * - Must use trial connection approach or RAP/DCE-RPC protocols (not exposed by SMBJ)
     * 
     * Current implementation tries common share names, which may miss custom-named shares.
     * This is a known limitation of SMBJ library v0.12.1.
     * 
     * Alternative solutions:
     * 1. Use jCIFS library (older, but has share enumeration)
     * 2. Use RAP protocol via custom implementation
     * 3. Ask user to enter share names manually
     */
    suspend fun listShares(
        server: String,
        username: String = "",
        password: String = "",
        domain: String = "",
        port: Int = 445
    ): SmbResult<List<String>> {
        return try {
            val connection = client.connect(server, port)
            val authContext = if (username.isEmpty()) {
                AuthenticationContext.anonymous()
            } else {
                AuthenticationContext(username, password.toCharArray(), domain)
            }
            
            val session = connection.authenticate(authContext)
            val shares = mutableListOf<String>()
            
            try {
                // Attempt 1: Try to list shares using IPC$ administrative share
                // This works if user has proper permissions
                try {
                    val ipcShare = session.connectShare("IPC$")
                    // Try to get share list through IPC$
                    // Note: SMBJ doesn't expose direct API for this, but connection success indicates permissions
                    ipcShare.close()
                    Timber.d("IPC$ connection successful - user may have admin rights")
                    
                    // Try to use ServerService to enumerate shares (if available in SMBJ)
                    // This is a best-effort attempt
                    try {
                        // SMBJ doesn't expose RAP or SRVSVC directly, so we fall back to trial method
                        Timber.d("Share enumeration via IPC$ not directly supported by SMBJ")
                    } catch (e: Exception) {
                        Timber.d("Share enumeration through IPC$ failed: ${e.message}")
                    }
                } catch (e: Exception) {
                    Timber.d("IPC$ access denied or not available: ${e.message}")
                }
                
                // Attempt 2: Try common and typical share names (extended list)
                // This is the main workaround for SMBJ's lack of share enumeration API
                val commonShareNames = listOf(
                    // Standard Windows shares
                    "Public", "Users", "Documents", "Downloads",
                    "Pictures", "Photos", "Images",
                    "Videos", "Movies", "Media",
                    "Music", "Audio",
                    // Common custom names
                    "Shared", "Share", "Data", "Files", 
                    "Transfer", "Common", "Backup",
                    // NAS typical names
                    "home", "public", "web", "multimedia",
                    // Work/Personal variations
                    "Work", "Personal", "Private", "Projects",
                    // Archive/Storage variations
                    "Archive", "Storage", "Repository", "Vault",
                    // Year-based (try recent years)
                    "2024", "2025", "Archive2024",
                    // Department names
                    "IT", "Finance", "HR", "Sales",
                    // Media server names
                    "Plex", "Media", "Library", "Content",
                    // Admin shares (usually hidden, but try)
                    "C$", "D$", "E$", "ADMIN$", "IPC$"
                )
                
                Timber.d("Scanning for shares using trial connection method (${commonShareNames.size} attempts)...")
                
                for (shareName in commonShareNames) {
                    try {
                        val share = session.connectShare(shareName)
                        // If connection successful, share exists and is accessible
                        shares.add(shareName)
                        Timber.d("Found accessible share: $shareName")
                        share.close()
                    } catch (e: Exception) {
                        // Share doesn't exist, not accessible, or hidden - skip silently
                        // This is expected behavior for non-existent shares
                    }
                }
                
                Timber.i("Found ${shares.size} accessible shares on $server using trial method")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to enumerate shares")
                session.close()
                connection.close()
                return SmbResult.Error(
                    "Share enumeration failed. SMBJ library limitation: cannot list shares automatically. " +
                    "Please enter share name manually. Technical details: ${e.message}", 
                    e
                )
            }
            
            session.close()
            connection.close()
            
            if (shares.isEmpty()) {
                return SmbResult.Error(
                    "No accessible shares found using trial method.\n\n" +
                    "SMBJ library limitation: Cannot automatically discover all shares.\n\n" +
                    "Tried multiple common share names, but none were accessible.\n\n" +
                    "Your shares may have custom names. Please enter share name manually.\n\n" +
                    "To find share names on Windows:\n" +
                    "1. Open File Explorer on server computer\n" +
                    "2. Right-click shared folder → Properties → Sharing tab\n" +
                    "3. Look for 'Network Path' (e.g., \\\\ServerName\\ShareName)\n" +
                    "4. Use the ShareName part in the app\n\n" +
                    "Or use 'net share' command in Windows Command Prompt to list all shares.",
                    null
                )
            }
            
            // Return found shares with helpful message if only few found
            val result = SmbResult.Success(shares)
            if (shares.size < 3) {
                Timber.w("Only ${shares.size} share(s) found. There may be more shares with custom names.")
            }
            
            result
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect to SMB server for share enumeration")
            SmbResult.Error("Connection failed: ${e.message}. Please verify server address and credentials.", e)
        }
    }

    /**
     * Copy file from SMB to local
     */
    suspend fun downloadFile(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        localOutputStream: OutputStream
    ): SmbResult<Unit> {
        return try {
            withConnection(connectionInfo) { share ->
                val file = share.openFile(
                    remotePath,
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )
                
                file.use { smbFile ->
                    smbFile.inputStream.use { input ->
                        input.copyTo(localOutputStream)
                    }
                }
                SmbResult.Success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to download file from SMB")
            SmbResult.Error("Failed to download file: ${e.message}", e)
        }
    }

    /**
     * Upload file from local to SMB
     */
    suspend fun uploadFile(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        localInputStream: InputStream
    ): SmbResult<Unit> {
        return try {
            withConnection(connectionInfo) { share ->
                val file = share.openFile(
                    remotePath,
                    EnumSet.of(AccessMask.GENERIC_WRITE),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OVERWRITE_IF,
                    null
                )
                
                file.use { smbFile ->
                    smbFile.outputStream.use { output ->
                        localInputStream.copyTo(output)
                    }
                }
                SmbResult.Success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload file to SMB")
            SmbResult.Error("Failed to upload file: ${e.message}", e)
        }
    }

    /**
     * Delete file on SMB share
     */
    suspend fun deleteFile(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<Unit> {
        return try {
            withConnection(connectionInfo) { share ->
                share.rm(remotePath)
                SmbResult.Success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete file from SMB")
            SmbResult.Error("Failed to delete file: ${e.message}", e)
        }
    }

    /**
     * Create directory on SMB share
     */
    suspend fun createDirectory(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<Unit> {
        return try {
            withConnection(connectionInfo) { share ->
                share.mkdir(remotePath)
                SmbResult.Success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to create directory on SMB")
            SmbResult.Error("Failed to create directory: ${e.message}", e)
        }
    }

    /**
     * Check if path exists on SMB share
     */
    suspend fun exists(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<Boolean> {
        return try {
            withConnection(connectionInfo) { share ->
                val exists = share.fileExists(remotePath)
                SmbResult.Success(exists)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check if path exists on SMB")
            SmbResult.Error("Failed to check path: ${e.message}", e)
        }
    }

    /**
     * Helper function to manage connection lifecycle
     */
    private suspend fun <T> withConnection(
        connectionInfo: SmbConnectionInfo,
        block: suspend (DiskShare) -> SmbResult<T>
    ): SmbResult<T> {
        var connection: Connection? = null
        var session: Session? = null
        var share: DiskShare? = null
        
        return try {
            connection = client.connect(connectionInfo.server, connectionInfo.port)
            
            val authContext = if (connectionInfo.username.isEmpty()) {
                AuthenticationContext.anonymous()
            } else {
                AuthenticationContext(
                    connectionInfo.username,
                    connectionInfo.password.toCharArray(),
                    connectionInfo.domain
                )
            }
            
            session = connection.authenticate(authContext)
            share = session.connectShare(connectionInfo.shareName) as DiskShare
            
            block(share)
        } catch (e: Exception) {
            Timber.e(e, "SMB connection error")
            SmbResult.Error("Connection error: ${e.message}", e)
        } finally {
            try {
                share?.close()
                session?.close()
                connection?.close()
            } catch (e: Exception) {
                Timber.w(e, "Error closing SMB connection")
            }
        }
    }

    /**
     * Build diagnostic message for connection errors
     */
    private fun buildDiagnosticMessage(
        exception: Exception,
        connectionInfo: SmbConnectionInfo
    ): String {
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
     * Close client and cleanup resources
     */
    fun close() {
        try {
            client.close()
        } catch (e: Exception) {
            Timber.w(e, "Error closing SMB client")
        }
    }
}
