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
     */
    suspend fun testConnection(connectionInfo: SmbConnectionInfo): SmbResult<String> {
        return try {
            withConnection(connectionInfo) { share ->
                // Simply check if we can access the share
                share.folderExists("")
                SmbResult.Success("Connected successfully to ${connectionInfo.server}\\${connectionInfo.shareName}")
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
            
            // Get list of shares using TreeConnect
            try {
                // Connect to IPC$ share to list available shares
                val ipcShare = session.connectShare("IPC$")
                // Note: SMBJ doesn't provide direct API to list shares
                // This is a limitation - we'll need to try known share names
                // or use external tools
                ipcShare.close()
            } catch (e: Exception) {
                Timber.w(e, "Failed to enumerate shares via IPC$")
            }
            
            session.close()
            connection.close()
            
            // For now, return empty list - share enumeration requires additional implementation
            SmbResult.Success(shares)
        } catch (e: Exception) {
            Timber.e(e, "Failed to list SMB shares")
            SmbResult.Error("Failed to list shares: ${e.message}", e)
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
