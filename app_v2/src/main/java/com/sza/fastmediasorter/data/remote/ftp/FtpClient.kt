@file:Suppress("DEPRECATION")

package com.sza.fastmediasorter.data.remote.ftp

import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Low-level FTP client wrapper using Apache Commons Net
 * Handles FTP connection, authentication and file operations with passive mode
 * 
 * Thread-safe: Uses mutex for synchronized access to FTPClient instance.
 * Supports connection pooling for ExoPlayer DataSource via getConnectionForExoPlayer().
 */
@Singleton
class FtpClient @Inject constructor(
    private val reachabilityGate: com.sza.fastmediasorter.core.network.NetworkReachabilityGate
) {

    private var ftpClient: FTPClient? = null
    private val mutex = Any() // Synchronization lock for FTPClient operations

    companion object {
        private const val CONNECT_TIMEOUT = 10000 // 10 seconds (reduced from 15s for faster error feedback)
        private const val SOCKET_TIMEOUT = 30000 // 30 seconds
        private const val KEEPALIVE_TIMEOUT = 15L // 15 seconds
        private const val MAX_CONCURRENT_CONNECTIONS = 10 // Max pooled connections for ExoPlayer
        private const val IDLE_TIMEOUT_MS = 25000L // 25 seconds idle timeout
    }

    // ExoPlayer connection management lives in FtpExoPlayerPool. Type aliases keep
    // call sites referencing the unqualified names without churn.
    private val exoPlayerPool = FtpExoPlayerPool()

    @Throws(IOException::class)
    fun getConnectionForExoPlayer(connectionInfo: FtpExoPlayerPool.FtpConnectionInfo): FtpExoPlayerPool.ExoPlayerFtpConnection {
        reachabilityGate.requireAnyNetwork("FTP")
        return exoPlayerPool.getConnectionForExoPlayer(connectionInfo)
    }

    fun releaseExoPlayerConnection(client: FTPClient?) =
        exoPlayerPool.releaseExoPlayerConnection(client)

    private fun cleanupIdleFtpConnections() = exoPlayerPool.cleanupIdleFtpConnections()

    /**
     * Connect to FTP server with password authentication
     * @param host Server IP address
     * @param port Server port (default 21)
     * @param username Username for authentication
     * @param password Password for authentication
     * @return Result with Unit on success or exception on failure
     */
    suspend fun connect(
        host: String,
        port: Int = 21,
        username: String,
        password: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            reachabilityGate.requireAnyNetwork("FTP")
            disconnect() // Ensure clean state
            
            val client = FTPClient()
            
            // Set connection and socket timeout to 30/60 seconds for unreliable networks
            client.connectTimeout = CONNECT_TIMEOUT
            client.defaultTimeout = SOCKET_TIMEOUT
            client.setDataTimeout(SOCKET_TIMEOUT)
            client.controlKeepAliveTimeout = Duration.ofSeconds(KEEPALIVE_TIMEOUT).seconds
            
            client.connect(host, port)
            
            val replyCode = client.replyCode
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                client.disconnect()
                return@withContext Result.failure(
                    IOException("FTP server refused connection. Reply code: $replyCode")
                )
            }
            
            if (!client.login(username, password)) {
                client.disconnect()
                return@withContext Result.failure(
                    IOException("FTP authentication failed")
                )
            }
            
            // Enable passive mode (required for most modern FTP servers behind NAT/firewall)
            client.enterLocalPassiveMode()
            
            // Set binary mode for file transfers
            client.setFileType(FTP.BINARY_FILE_TYPE)
            client.controlEncoding = "UTF-8" // Ensure non-ASCII filenames are not garbled
            
            ftpClient = client
            
            Timber.d("FTP connected to $host:$port (hasUser=${username.isNotBlank()}, passive mode)")
            Result.success(Unit)
        } catch (e: IOException) {
            Timber.e(e, "FTP connection failed: $host:$port")
            disconnect()
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "FTP connection error: $host:$port")
            disconnect()
            Result.failure(e)
        }
    }

    /**
     * List files and directories in remote path
     * @param remotePath Remote directory path (default "/")
     * @return Result with list of file names (not full paths) or exception on failure
     */
    /**
     * List files with metadata (name, size, timestamp) in remote directory.
     * Returns FTPFile objects with full attributes.
     * @param recursive If true, scans all subdirectories recursively
     */
    suspend fun listFilesWithMetadata(
        remotePath: String = "/",
        recursive: Boolean = true
    ): Result<List<FTPFile>> = withContext(Dispatchers.IO) {
        synchronized(mutex) {
            try {
                val client = ftpClient ?: return@withContext Result.failure(
                    IllegalStateException("Not connected. Call connect() first.")
                )
                
                val allFiles = mutableListOf<FTPFile>()
                
                if (recursive) {
                    listFilesWithMetadataRecursive(client, remotePath, allFiles)
                } else {
                    listFilesWithMetadataSingleLevel(client, remotePath, allFiles)
                }
                
                Timber.d("FTP listed ${allFiles.size} files with metadata in $remotePath (recursive=$recursive)")
                Result.success(allFiles)
            } catch (e: IOException) {
                Timber.e(e, "FTP list files with metadata failed: $remotePath")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "FTP list files with metadata error: $remotePath")
                Result.failure(e)
            }
        }
    }

    /**
     * List files with metadata using offset/limit pagination.
     * Supports early-stop recursive traversal to avoid loading full directory trees.
     */
    suspend fun listFilesWithMetadataPaged(
        remotePath: String = "/",
        offset: Int = 0,
        limit: Int = 50,
        recursive: Boolean = true
    ): Result<List<FTPFile>> = withContext(Dispatchers.IO) {
        synchronized(mutex) {
            try {
                val client = ftpClient ?: return@withContext Result.failure(
                    IllegalStateException("Not connected. Call connect() first.")
                )

                if (limit <= 0) {
                    return@withContext Result.success(emptyList())
                }

                val safeOffset = offset.coerceAtLeast(0)
                val results = mutableListOf<FTPFile>()

                if (recursive) {
                    val pagingState = FtpDirectoryScanner.MetadataPagingState(offset = safeOffset, limit = limit)
                    listFilesWithMetadataRecursivePaged(client, remotePath, results, pagingState)
                } else {
                    val allFiles = mutableListOf<FTPFile>()
                    listFilesWithMetadataSingleLevel(client, remotePath, allFiles)
                    allFiles.drop(safeOffset).take(limit).forEach { results.add(it) }
                }

                Timber.d(
                    "FTP listFilesWithMetadataPaged: path=$remotePath, offset=$safeOffset, limit=$limit, recursive=$recursive, returned=${results.size}"
                )
                Result.success(results)
            } catch (e: IOException) {
                Timber.e(e, "FTP paged list files with metadata failed: $remotePath")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "FTP paged list files with metadata error: $remotePath")
                Result.failure(e)
            }
        }
    }
    
    private fun listFilesWithMetadataSingleLevel(client: FTPClient, remotePath: String, results: MutableList<FTPFile>) =
        FtpDirectoryScanner.listFilesWithMetadataSingleLevel(client, remotePath, results)
    
    private fun listFilesWithMetadataRecursive(client: FTPClient, remotePath: String, results: MutableList<FTPFile>) =
        FtpDirectoryScanner.listFilesWithMetadataRecursive(client, remotePath, results)

    private fun listFilesWithMetadataRecursivePaged(client: FTPClient, remotePath: String, results: MutableList<FTPFile>, paging: FtpDirectoryScanner.MetadataPagingState) =
        FtpDirectoryScanner.listFilesWithMetadataRecursivePaged(client, remotePath, results, paging)

    suspend fun listFiles(remotePath: String = "/"): Result<List<String>> = withContext(Dispatchers.IO) {
        synchronized(mutex) {
            try {
                val client = ftpClient ?: return@withContext Result.failure(
                    IllegalStateException("Not connected. Call connect() first.")
                )
                
                // Try passive mode first, fallback to active mode on timeout
                val files = try {
                    Timber.d("FTP listing files in passive mode: $remotePath")
                    val ftpFiles = client.listFiles(remotePath)
                    
                    // Filter out . and .. entries
                    ftpFiles.mapNotNull { ftpFile ->
                        if (ftpFile.name == "." || ftpFile.name == "..") null else ftpFile.name
                    }
                } catch (e: SocketTimeoutException) {
                    Timber.w(e, "FTP passive mode timeout, switching to active mode")
                    
                    // Switch to active mode and retry
                    client.enterLocalActiveMode()
                    Timber.d("FTP retrying listFiles in active mode: $remotePath")
                    
                    val ftpFiles = try {
                        client.listFiles(remotePath)
                    } finally {
                        // Switch back to passive for future operations
                        try { 
                            client.enterLocalPassiveMode() 
                            Timber.d("FTP switched back to passive mode")
                        } catch (ignored: Exception) {
                            Timber.w(ignored, "Failed to switch back to passive mode")
                        }
                    }
                    
                    // Filter out . and .. entries
                    ftpFiles.mapNotNull { ftpFile ->
                        if (ftpFile.name == "." || ftpFile.name == "..") null else ftpFile.name
                    }
                }
                
                Timber.d("FTP listed ${files.size} files in $remotePath")
                Result.success(files)
            } catch (e: IOException) {
                Timber.e(e, "FTP list files failed: $remotePath")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "FTP list files error: $remotePath")
                Result.failure(e)
            }
        }
    }

    /**
     * Test connection to FTP server
     * @param host Server IP address
     * @param port Server port (default 21)
     * @param username Username for authentication
     * @param password Password for authentication
     * @return Result with true on success or exception on failure
     */
    suspend fun testConnection(host: String, port: Int = 21, username: String, password: String): Result<Boolean> =
        FtpStandaloneOperations.testConnection(host, port, username, password)

    /**
     * Read file bytes from FTP server (useful for thumbnails and image loading)
     * @param remotePath Full path to remote file
     * @param maxBytes Maximum bytes to read (default: read entire file)
     * @return Result with ByteArray or exception on failure
     */
    suspend fun readFileBytes(
        remotePath: String,
        maxBytes: Long = Long.MAX_VALUE
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        synchronized(mutex) {
            try {
                val client = ftpClient ?: return@withContext Result.failure(
                    IllegalStateException("Not connected. Call connect() first.")
                )
                
                // Try passive mode first, fallback to active mode on timeout
                // For downloads/reads, we might need multiple retries if connection dropped
                val bytes = try {
                    client.retrieveFileStream(remotePath)?.use { inputStream ->
                        val bytes = if (maxBytes < Long.MAX_VALUE) {
                            val maxBytesInt = maxBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                            val allBytes = inputStream.readBytes()
                            if (allBytes.size > maxBytesInt) allBytes.copyOf(maxBytesInt) else allBytes
                        } else {
                            inputStream.readBytes()
                        }
                        
                        // Must complete transfer
                        if (!client.completePendingCommand()) {
                            return@withContext Result.failure(
                                IOException("FTP command failed after retrieving file")
                            )
                        }
                        
                        Timber.d("FTP read ${bytes.size} bytes from $remotePath")
                        bytes
                    } ?: return@withContext Result.failure(IOException("Failed to open file stream: $remotePath"))
                } catch (e: SocketTimeoutException) {
                    Timber.w(e, "FTP passive mode timeout during read, switching to active mode")
                    
                    // Switch to active mode and retry
                    client.enterLocalActiveMode()
                    Timber.d("FTP retrying read in active mode: $remotePath")
                    
                    try {
                        client.retrieveFileStream(remotePath)?.use { inputStream ->
                            val bytes = if (maxBytes < Long.MAX_VALUE) {
                                val maxBytesInt = maxBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                                val allBytes = inputStream.readBytes()
                                if (allBytes.size > maxBytesInt) allBytes.copyOf(maxBytesInt) else allBytes
                            } else {
                                inputStream.readBytes()
                            }
                            
                            if (!client.completePendingCommand()) {
                                return@withContext Result.failure(
                                    IOException("FTP command failed after retrieving file (active mode)")
                                )
                            }
                            bytes
                        } ?: return@withContext Result.failure(IOException("Failed to open file stream (active mode): $remotePath"))
                    } finally {
                        // Switch back to passive for future operations
                        try { 
                            client.enterLocalPassiveMode() 
                            Timber.d("FTP switched back to passive mode")
                        } catch (ignored: Exception) {
                            Timber.w(ignored, "Failed to switch back to passive mode")
                        }
                    }
                }
                
                Result.success(bytes)
            } catch (e: IOException) {
                Timber.e(e, "FTP read file bytes failed: $remotePath")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "FTP read file bytes error: $remotePath")
                Result.failure(e)
            }
        }
    }
    
    /**
     * Read byte range from FTP file (for sparse video reading).
     * Uses REST command to resume from offset.
     */
    suspend fun readFileBytesRange(
        remotePath: String,
        offset: Long,
        length: Long
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        synchronized(mutex) {
            try {
                val client = ftpClient ?: return@withContext Result.failure(
                    IllegalStateException("Not connected. Call connect() first.")
                )
                
                // Try passive mode first, fallback to active mode on timeout
                val bytes = try {
                    // Use REST command to start reading from offset
                    client.setRestartOffset(offset)
                    
                    client.retrieveFileStream(remotePath)?.use { inputStream ->
                        val buffer = ByteArray(length.toInt())
                        var totalRead = 0
                        
                        while (totalRead < length) {
                            val read = inputStream.read(buffer, totalRead, (length - totalRead).toInt())
                            if (read == -1) break
                            totalRead += read
                        }
                        
                        // Must complete transfer
                        if (!client.completePendingCommand()) {
                            return@withContext Result.failure(
                                IOException("FTP command failed after retrieving range")
                            )
                        }
                        
                        // Return only bytes read
                        if (totalRead < length) {
                            buffer.copyOf(totalRead)
                        } else {
                            buffer
                        }
                    } ?: return@withContext Result.failure(IOException("Failed to open file stream: $remotePath"))
                } catch (e: SocketTimeoutException) {
                    Timber.w(e, "FTP passive mode timeout during range read, switching to active mode")
                    
                    // Switch to active mode and retry
                    client.enterLocalActiveMode()
                    Timber.d("FTP retrying range read in active mode: $remotePath")
                    
                    try {
                        // Reset restart offset for retry
                        client.setRestartOffset(offset)
                        
                        client.retrieveFileStream(remotePath)?.use { inputStream ->
                            val buffer = ByteArray(length.toInt())
                            var totalRead = 0
                            
                            while (totalRead < length) {
                                val read = inputStream.read(buffer, totalRead, (length - totalRead).toInt())
                                if (read == -1) break
                                totalRead += read
                            }
                            
                            if (!client.completePendingCommand()) {
                                return@withContext Result.failure(
                                    IOException("FTP command failed after retrieving range (active mode)")
                                )
                            }
                            
                            if (totalRead < length) {
                                buffer.copyOf(totalRead)
                            } else {
                                buffer
                            }
                        } ?: return@withContext Result.failure(IOException("Failed to open file stream (active mode): $remotePath"))
                    } finally {
                        // Switch back to passive
                        try { 
                            client.enterLocalPassiveMode() 
                            Timber.d("FTP switched back to passive mode")
                        } catch (ignored: Exception) {
                            Timber.w(ignored, "Failed to switch back to passive mode")
                        }
                    }
                }
                
                Result.success(bytes)
            } catch (e: IOException) {
                Timber.e(e, "FTP read bytes range failed: $remotePath offset=$offset length=$length")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "FTP read bytes range error: $remotePath offset=$offset length=$length")
                Result.failure(e)
            }
        }
    }

    /**
     * Download file from FTP server to OutputStream
     * @param remotePath Full path to remote file
     * @param outputStream OutputStream to write downloaded data
     * @param fileSize Size of the file to download (for progress tracking), 0 if unknown
     * @param progressCallback Optional callback for tracking download progress
     * @return Result with Unit on success or exception on failure
     */
    suspend fun downloadFile(
        remotePath: String,
        outputStream: OutputStream,
        fileSize: Long = 0L,
        @Suppress("UNUSED_PARAMETER") progressCallback: ByteProgressCallback? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        synchronized(mutex) {
            try {
                val client = ftpClient ?: return@withContext Result.failure(
                    IllegalStateException("Not connected. Call connect() first.")
                )
                
                Timber.d("FTP downloading: $remotePath (size=$fileSize bytes)")
                
                // Try passive mode first, fallback to active mode on timeout
                val success = try {
                    client.retrieveFile(remotePath, outputStream)
                } catch (e: SocketTimeoutException) {
                    Timber.w(e, "FTP passive mode timeout, switching to active mode for download")
                    
                    // Switch to active mode and retry
                    client.enterLocalActiveMode()
                    Timber.d("FTP retrying download in active mode: $remotePath")
                    
                    try {
                        client.retrieveFile(remotePath, outputStream)
                    } finally {
                        // Switch back to passive for future operations
                        try {
                            client.enterLocalPassiveMode()
                            Timber.d("FTP switched back to passive mode")
                        } catch (ignored: Exception) {
                            Timber.w(ignored, "Failed to switch back to passive mode")
                        }
                    }
                } catch (e: Exception) {
                    // Handle any other exception during retrieveFile (including NPE from internal FTPClient issues)
                    Timber.e(e, "FTP download error during retrieveFile: $remotePath")
                    return@withContext Result.failure(
                        IOException("FTP download failed: ${e.message}", e)
                    )
                }
                
                if (!success) {
                    return@withContext Result.failure(
                        IOException("FTP download failed: ${client.replyString}")
                    )
                }
                
                Timber.i("FTP download success: $remotePath")
                Result.success(Unit)
            } catch (e: IOException) {
                Timber.e(e, "FTP download failed: $remotePath")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "FTP download error: $remotePath")
                Result.failure(e)
            }
        }
    }

    /**
     * Upload file to FTP server from InputStream
     * @param remotePath Full path where file should be uploaded
     * @param inputStream InputStream to read data from
     * @param fileSize Size of the file to upload (for progress tracking), 0 if unknown
     * @param progressCallback Optional callback for tracking upload progress
     * @return Result with Unit on success or exception on failure
     */
    suspend fun uploadFile(
        remotePath: String,
        inputStream: InputStream,
        fileSize: Long = 0L,
        @Suppress("UNUSED_PARAMETER") progressCallback: ByteProgressCallback? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = ftpClient ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            Timber.d("FTP uploading: $remotePath (size=$fileSize bytes)")
            
            // Ensure parent directory exists
            val parentDir = remotePath.substringBeforeLast('/')
            if (parentDir.isNotEmpty() && parentDir != remotePath) {
                try {
                    if (!client.changeWorkingDirectory(parentDir)) {
                        Timber.d("FTP: Creating parent directory: $parentDir")
                        client.makeDirectory(parentDir)
                    }
                    // Change back to root
                    client.changeWorkingDirectory("/")
                } catch (e: Exception) {
                    Timber.w(e, "FTP: Failed to create parent dir, trying upload anyway")
                }
            }
            
            val success = client.storeFile(remotePath, inputStream)
            if (!success) {
                return@withContext Result.failure(
                    IOException("FTP upload failed: ${client.replyString}")
                )
            }
            
            Timber.i("FTP upload success: $remotePath")
            Result.success(Unit)
        } catch (e: IOException) {
            Timber.e(e, "FTP upload failed: $remotePath")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "FTP upload error: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Delete file on FTP server
     * @param remotePath Full path to file to delete
     * @return Result with Unit on success or exception on failure
     */
    suspend fun deleteFile(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = ftpClient ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            Timber.d("FTP deleting: $remotePath")
            
            val success = client.deleteFile(remotePath)
            if (!success) {
                return@withContext Result.failure(
                    IOException("FTP delete failed: ${client.replyString}")
                )
            }
            
            Timber.i("FTP delete success: $remotePath")
            Result.success(Unit)
        } catch (e: IOException) {
            Timber.e(e, "FTP delete failed: $remotePath")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "FTP delete error: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Delete directory recursively on FTP server
     * @param remotePath Full path to directory to delete
     * @return Result with Unit on success or exception on failure
     */
    suspend fun deleteDirectory(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = ftpClient ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            Timber.d("FTP deleting directory: $remotePath")
            
            // List directory contents
            val files = client.listFiles(remotePath)
            
            // Delete all files and subdirectories recursively
            files.forEach { file ->
                val fullPath = "$remotePath/${file.name}"
                if (file.isDirectory) {
                    deleteDirectory(fullPath).getOrThrow()
                } else {
                    deleteFile(fullPath).getOrThrow()
                }
            }
            
            // Delete the directory itself
            val success = client.removeDirectory(remotePath)
            if (!success) {
                return@withContext Result.failure(
                    IOException("FTP remove directory failed: ${client.replyString}")
                )
            }
            
            Timber.i("FTP delete directory success: $remotePath")
            Result.success(Unit)
        } catch (e: IOException) {
            Timber.e(e, "FTP delete directory failed: $remotePath")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "FTP delete directory error: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Rename file on FTP server
     * @param oldPath Current file path
     * @param newName New filename (without path)
     * @return Result with Unit on success or exception on failure
     */
    suspend fun renameFile(oldPath: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = ftpClient ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            // Extract directory and construct new path
            val directory = oldPath.substringBeforeLast('/', "")
            val newPath = when {
                directory.isNotEmpty() -> "$directory/$newName"
                oldPath.startsWith("/") -> "/$newName"
                else -> newName
            }
            
            Timber.d("FTP renaming: $oldPath → $newPath")
            
            val success = client.rename(oldPath, newPath)
            if (!success) {
                return@withContext Result.failure(
                    IOException("FTP rename failed: ${client.replyString}")
                )
            }
            
            Timber.i("FTP rename success: $newPath")
            Result.success(Unit)
        } catch (e: IOException) {
            Timber.e(e, "FTP rename failed: $oldPath")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "FTP rename error: $oldPath")
            Result.failure(e)
        }
    }
    
    /**
     * Move/rename file with full paths (server-side operation)
     * @param oldPath Full path to source file
     * @param newPath Full path to destination file
     * @return Result with Unit or exception on failure
     */
    suspend fun moveFile(oldPath: String, newPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = ftpClient ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            Timber.d("FTP moving: $oldPath → $newPath")
            
            val success = client.rename(oldPath, newPath)
            if (!success) {
                return@withContext Result.failure(
                    IOException("FTP move failed: ${client.replyString}")
                )
            }
            
            Timber.i("FTP move success: $newPath")
            Result.success(Unit)
        } catch (e: IOException) {
            Timber.e(e, "FTP move failed: $oldPath → $newPath")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "FTP move error: $oldPath → $newPath")
            Result.failure(e)
        }
    }
    
    /**
     * Create directory on FTP server
     * @param remotePath Full path to directory to create
     * @return Result with Unit or exception on failure
     */
    suspend fun createDirectory(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = ftpClient ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            Timber.d("FTP creating directory: $remotePath")
            val success = client.makeDirectory(remotePath)
            if (!success) {
                return@withContext Result.failure(
                    IOException("FTP create directory failed: ${client.replyString}")
                )
            }
            
            Timber.i("FTP directory created: $remotePath")
            Result.success(Unit)
        } catch (e: IOException) {
            Timber.e(e, "FTP create directory failed: $remotePath")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "FTP create directory error: $remotePath")
            Result.failure(e)
        }
    }
    
    /**
     * Check if directory exists on FTP server using current connection
     * @param remotePath Full path to directory to check
     * @return Result with Boolean (true if directory exists)
     */
    suspend fun directoryExists(remotePath: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val client = ftpClient ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            // Try to change to the directory - if it succeeds, it exists
            val currentDir = client.printWorkingDirectory()
            val success = client.changeWorkingDirectory(remotePath)
            
            // Change back to original directory
            if (success) {
                client.changeWorkingDirectory(currentDir)
            }
            
            Result.success(success)
        } catch (e: IOException) {
            Timber.w(e, "FTP directory exists check failed: $remotePath")
            Result.success(false)
        } catch (e: Exception) {
            Timber.w(e, "FTP directory exists check error: $remotePath")
            Result.success(false)
        }
    }

    /**
     * Disconnect from FTP server and cleanup resources
     * Uses short timeout for logout to avoid SocketTimeoutException delays
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            ftpClient?.let { client ->
                if (client.isConnected) {
                    // Set short timeout for logout - don't wait for server response
                    val originalTimeout = client.soTimeout
                    try {
                        client.soTimeout = 1000 // 1 second instead of default 30
                        client.logout()
                    } catch (e: java.net.SocketTimeoutException) {
                        // Ignore logout timeout - server will close connection anyway
                        Timber.d("FTP logout timeout (ignored)")
                    } catch (e: Exception) {
                        // Other logout errors also non-critical
                        Timber.d(e, "FTP logout error (ignored)")
                    } finally {
                        // Restore original timeout before disconnect (if socket still active)
                        try {
                            client.soTimeout = originalTimeout
                        } catch (e: Exception) {
                            // Socket may be null/closed - ignore
                        }
                    }
                    // Always close socket regardless of logout success
                    client.disconnect()
                }
            }
            Timber.d("FTP disconnected")
        } catch (e: Exception) {
            Timber.w(e, "FTP disconnect error (non-critical)")
        } finally {
            ftpClient = null
        }
    }

    // Check if currently connected
    fun isConnected(): Boolean {
        return ftpClient?.isConnected == true
    }

    /**
     * Upload file using a temporary FTP connection (for parallel uploads)
     * Creates a new connection, uploads the file, and closes immediately
     * 
     * @param host Server IP address
     * @param port Server port
     * @param username Username for authentication
     * @param password Password for authentication
     * @param remotePath Full path where file should be uploaded
     * @param inputStream InputStream to read data from
     * @param fileSize Size of the file to upload (for progress tracking), 0 if unknown
     * @param progressCallback Optional callback for tracking upload progress
     * @return Result with Unit on success or exception on failure
     */
    suspend fun uploadFileWithNewConnection(
        host: String,
        port: Int,
        username: String,
        password: String,
        remotePath: String,
        inputStream: InputStream,
        fileSize: Long = 0L,
        progressCallback: ByteProgressCallback? = null
    ): Result<Unit> = FtpStandaloneOperations.uploadFile(
        host, port, username, password, remotePath, inputStream, fileSize, progressCallback
    )

    suspend fun deleteFileWithNewConnection(
        host: String, port: Int, username: String, password: String, remotePath: String
    ): Result<Unit> = FtpStandaloneOperations.deleteFile(host, port, username, password, remotePath)

    suspend fun renameFileWithNewConnection(
        host: String, port: Int, username: String, password: String, oldPath: String, newName: String
    ): Result<Unit> = FtpStandaloneOperations.renameFile(host, port, username, password, oldPath, newName)

    suspend fun createDirectoryWithNewConnection(
        host: String, port: Int, username: String, password: String, remotePath: String
    ): Result<Unit> = FtpStandaloneOperations.createDirectory(host, port, username, password, remotePath)

    suspend fun existsWithNewConnection(
        host: String, port: Int, username: String, password: String, remotePath: String
    ): Result<Boolean> = FtpStandaloneOperations.exists(host, port, username, password, remotePath)

    suspend fun readFileBytesWithNewConnection(
        host: String,
        port: Int,
        username: String,
        password: String,
        remotePath: String,
        maxBytes: Long = Long.MAX_VALUE
    ): Result<ByteArray> = FtpStandaloneOperations.readFileBytes(
        host, port, username, password, remotePath, maxBytes
    )

    suspend fun downloadFileWithNewConnection(
        host: String,
        port: Int,
        username: String,
        password: String,
        remotePath: String,
        outputStream: OutputStream,
        fileSize: Long = 0L,
        progressCallback: ByteProgressCallback? = null
    ): Result<Unit> = FtpStandaloneOperations.downloadFile(
        host, port, username, password, remotePath, outputStream, fileSize, progressCallback
    )

    suspend fun openInputStream(
        host: String, port: Int, username: String, password: String, remotePath: String
    ): Result<InputStream> = FtpStandaloneOperations.openInputStream(host, port, username, password, remotePath)
}