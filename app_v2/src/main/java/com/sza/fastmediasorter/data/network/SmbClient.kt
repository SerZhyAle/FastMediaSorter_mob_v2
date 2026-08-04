package com.sza.fastmediasorter.data.network

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.File
import com.sza.fastmediasorter.data.network.exceptions.HandledNetworkOutcomeLogger
import com.sza.fastmediasorter.data.network.helpers.SmbDirectoryScanner
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbFileInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.domain.model.MediaExtensions
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMB/CIFS client facade for network file operations using SMBJ library.
 * Delegates file operations to SmbFileOperations and connection management to SmbConnectionManager.
 * 
 * Responsibilities:
 * - Connection testing and share enumeration
 * - Directory scanning and media file discovery
 * - Delegation to SmbFileOperations for file CRUD
 * 
 * Supports SMB2/SMB3 protocols.
 * Uses connection pooling to reduce authentication overhead when loading multiple files.
 * 
 * IMPORTANT: All path arguments in public methods are automatically trimmed of leading slashes
 * to ensure compatibility with SMBJ and various SMB server implementations.
 */
@Singleton
class SmbClient @Inject constructor(
    internal val connectionManager: SmbConnectionManager, // Internal for SmbDataSource access
    private val fileOperations: SmbFileOperations,
    internal val playbackConnectionTracker: SmbPlaybackConnectionTracker
) {
    
    companion object {
        // Retry configuration for connection attempts
        private const val MAX_RETRY_ATTEMPTS = 3 // Try 3 times before giving up
        private const val RETRY_DELAY_MS = 1000L // Initial delay between retries (increases exponentially)
    }
    
    // Dedicated dispatcher for blocking SMB I/O operations
    private val smbDispatcher = Dispatchers.IO
    
    // Directory scanner helper
    private val directoryScanner = SmbDirectoryScanner(smbDispatcher)

    // Share discovery (SMBJ has no enumeration API - trial-connect common share names)
    private val shareDiscovery = SmbShareDiscoveryHelper(connectionManager)

    // Media scan + count (S0002 Wave 47 - extracted from SmbClient)
    private val mediaScan = SmbMediaScanCoordinator(connectionManager, directoryScanner)

    // Rename / move / mkdir-p (S0002 Wave 47 - extracted from SmbClient)
    private val mutations = SmbFileMutationCoordinator(connectionManager)


    /**
     * Test connection to SMB server with retry logic
     * - If shareName is empty: tests server accessibility and lists available shares
     * - If shareName is provided: tests share accessibility and provides folder/file statistics
     * - If path is provided: tests specific folder within the share
     * 
     * Automatically retries up to MAX_RETRY_ATTEMPTS times with exponential backoff on timeout errors.
     */
    suspend fun testConnection(connectionInfo: SmbConnectionInfo, path: String = ""): SmbResult<String> {
        var lastException: Exception? = null
        var attemptNumber = 1
        
        while (attemptNumber <= MAX_RETRY_ATTEMPTS) {
            try {
                if (attemptNumber == 1) {
                    Timber.d("SMB testConnection to ${connectionInfo.server}/${connectionInfo.shareName} (hasUser=${connectionInfo.username.isNotBlank()})")
                } else {
                    Timber.d("SMB testConnection retry attempt $attemptNumber/$MAX_RETRY_ATTEMPTS")
                }
                return performTestConnection(connectionInfo, path)
            } catch (e: Exception) {
                // CancellationException means an outer withTimeout/coroutine cancel fired.
                // Retrying is pointless (the scope is already cancelled) - always re-throw.
                if (e is CancellationException) throw e

                lastException = e

                // Check if this is a retriable error (timeout or connection reset).
                // Include kotlinx.coroutines.TimeoutCancellationException by class (already handled
                // above via re-throw, but kept here for SMBJ / java.util.concurrent variants).
                val isTimeout = e is java.util.concurrent.TimeoutException ||
                                e is kotlinx.coroutines.TimeoutCancellationException ||
                                e.cause is java.util.concurrent.TimeoutException ||
                                e.cause?.cause is java.util.concurrent.TimeoutException ||
                                e.message?.contains("Timed out", ignoreCase = true) == true ||
                                e.message?.contains("Timeout", ignoreCase = true) == true ||
                                e.cause?.message?.contains("Timeout", ignoreCase = true) == true
                
                val isConnectionReset = e.cause?.message?.contains("Connection reset", ignoreCase = true) == true ||
                                        e.cause?.cause?.message?.contains("Connection reset", ignoreCase = true) == true
                
                val isRetriable = isTimeout || isConnectionReset
                
                if (isRetriable && attemptNumber < MAX_RETRY_ATTEMPTS) {
                    val delay = RETRY_DELAY_MS * (1 shl (attemptNumber - 1)) // Exponential: 1s, 2s, 4s
                    val errorType = if (isTimeout) "timeout" else "connection reset"
                    Timber.w("SMB $errorType on attempt $attemptNumber, retrying after ${delay}ms...")
                    kotlinx.coroutines.delay(delay)
                    attemptNumber++
                } else {
                    // Non-retriable error or last attempt - fail immediately
                    if (!isRetriable) {
                        Timber.d("SMB connection failed with non-retriable error: ${e.javaClass.simpleName}")
                    }
                    break
                }
            }
        }
        
        // All attempts failed
        val finalMessage = if (attemptNumber > 1) {
            "SMB testConnection failed after $attemptNumber attempts"
        } else {
            "SMB testConnection failed"
        }
        val failure = lastException ?: Exception("Unknown error")
        HandledNetworkOutcomeLogger.logConnectionTestFailure(
            scope = "smb-test-connection",
            resourceLabel = "${connectionInfo.server}/${connectionInfo.shareName}",
            throwable = failure,
            message = finalMessage
        )
        return SmbResult.Error(
            getUserFriendlyMessage(failure),
            lastException
        )
    }
    
    private suspend fun performTestConnection(connectionInfo: SmbConnectionInfo, path: String = ""): SmbResult<String> =
        shareDiscovery.performTestConnection(connectionInfo, path)

    /**
     * List files and folders in SMB directory
     */
    suspend fun listFiles(
        connectionInfo: SmbConnectionInfo,
        remotePath: String = ""
    ): SmbResult<List<SmbFileInfo>> {
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
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
                            size = fileInfo.endOfFile,
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

    /** Scan SMB folder for media files (recursive). Delegates to SmbMediaScanCoordinator. */
    suspend fun scanMediaFiles(
        connectionInfo: SmbConnectionInfo,
        remotePath: String = "",
        extensions: Set<String>? = MediaExtensions.IMAGE + MediaExtensions.VIDEO + MediaExtensions.AUDIO,
        scanSubdirectories: Boolean = true,
        progressCallback: com.sza.fastmediasorter.domain.usecase.ScanProgressCallback? = null,
        includeDirectories: Boolean = false
    ): SmbResult<List<SmbFileInfo>> =
        mediaScan.scanMediaFiles(connectionInfo, remotePath, extensions, scanSubdirectories, progressCallback, includeDirectories)

    /** Scan SMB folder with limit (for lazy loading). Delegates to SmbMediaScanCoordinator. */
    suspend fun scanMediaFilesChunked(
        connectionInfo: SmbConnectionInfo,
        remotePath: String = "",
        extensions: Set<String>? = MediaExtensions.IMAGE + MediaExtensions.VIDEO + MediaExtensions.AUDIO,
        maxFiles: Int = 100,
        scanSubdirectories: Boolean = true
    ): SmbResult<List<SmbFileInfo>> =
        mediaScan.scanMediaFilesChunked(connectionInfo, remotePath, extensions, maxFiles, scanSubdirectories)

    /** Scan media files with pagination support. Delegates to SmbMediaScanCoordinator. */
    suspend fun scanMediaFilesPaged(
        connectionInfo: SmbConnectionInfo,
        remotePath: String = "",
        extensions: Set<String>? = MediaExtensions.IMAGE + MediaExtensions.VIDEO + MediaExtensions.AUDIO,
        offset: Int = 0,
        limit: Int = 50,
        scanSubdirectories: Boolean = true
    ): SmbResult<List<SmbFileInfo>> =
        mediaScan.scanMediaFilesPaged(connectionInfo, remotePath, extensions, offset, limit, scanSubdirectories)

    /** Count media files in SMB folder (recursive, optimized). Delegates to SmbMediaScanCoordinator. */
    suspend fun countMediaFiles(
        connectionInfo: SmbConnectionInfo,
        remotePath: String = "",
        extensions: Set<String>? = MediaExtensions.IMAGE + MediaExtensions.VIDEO + MediaExtensions.AUDIO,
        maxCount: Int = 1000, // Fast initial scan: stop at 1000 to return quickly
        scanSubdirectories: Boolean = true
    ): SmbResult<Int> =
        mediaScan.countMediaFiles(connectionInfo, remotePath, extensions, maxCount, scanSubdirectories)



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
    ): SmbResult<List<String>> = shareDiscovery.listShares(server, username, password, domain, port)

    /**
     * Download file from SMB to local output stream.
     * Delegates to SmbFileOperations.
     */
    suspend fun downloadFile(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        localOutputStream: OutputStream,
        fileSize: Long = 0L,
        progressCallback: ByteProgressCallback? = null
    ): SmbResult<Unit> {
        // fileSize is unused by fileOperations but kept for API compatibility
        if (fileSize > 0) Timber.v("SmbClient.downloadFile: hinted size=$fileSize")
        return fileOperations.downloadFile(connectionInfo, remotePath, localOutputStream, progressCallback)
    }

    /**
     * Read file bytes from SMB (useful for thumbnails and image loading).
     * Delegates to SmbFileOperations.
     */
    suspend fun readFileBytes(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        maxBytes: Long = Long.MAX_VALUE
    ): SmbResult<ByteArray> {
        return fileOperations.readFileBytes(connectionInfo, remotePath, maxBytes)
    }

    /**
     * Read partial file bytes from SMB (useful for optimized video thumbnail extraction).
     * Delegates to SmbFileOperations.
     */
    suspend fun readPartialFile(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        offset: Long,
        length: Int
    ): SmbResult<ByteArray> {
        return fileOperations.readPartialFile(connectionInfo, remotePath, offset, length)
    }
    
    /**
     * Read specific byte range from file.
     * Delegates to SmbFileOperations.
     */
    suspend fun readFileBytesRange(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        offset: Long,
        length: Long,
        allowRetry: Boolean = true
    ): SmbResult<ByteArray> {
        return fileOperations.readFileBytesRange(connectionInfo, remotePath, offset, length, allowRetry = allowRetry)
    }

    /**
     * Upload file from local input stream to SMB.
     * Delegates to SmbFileOperations.
     */
    suspend fun uploadFile(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        localInputStream: InputStream,
        fileSize: Long = 0L,
        progressCallback: ByteProgressCallback? = null
    ): SmbResult<Unit> {
        return fileOperations.uploadFile(connectionInfo, remotePath, localInputStream, fileSize, progressCallback)
    }

    /**
     * Delete file on SMB share
     */
    suspend fun deleteFile(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<Unit> {
        Timber.d("SmbClient.deleteFile: START - remotePath='$remotePath'")
        Timber.d("SmbClient.deleteFile: Connection - server=${connectionInfo.server}, share=${connectionInfo.shareName}, port=${connectionInfo.port}")
        Timber.d("SmbClient.deleteFile: Credentials - username=${connectionInfo.username}, domain=${connectionInfo.domain}")
        
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                Timber.d("SmbClient.deleteFile: Share connected, checking if file exists...")
                
                // Check if file exists before deleting
                val exists = try {
                    share.fileExists(remotePath.trim('/', '\\'))
                } catch (e: Exception) {
                    Timber.w(e, "SmbClient.deleteFile: Failed to check file existence")
                    false
                }
                
                if (!exists) {
                    Timber.e("SmbClient.deleteFile: File does not exist: $remotePath")
                    return@withConnection SmbResult.Error("File not found: $remotePath", Exception("File does not exist"))
                }
                
                Timber.d("SmbClient.deleteFile: File exists, attempting to delete...")
                
                try {
                    share.rm(remotePath.trim('/', '\\'))
                    Timber.i("SmbClient.deleteFile: SUCCESS - File deleted: $remotePath")
                    SmbResult.Success(Unit)
                } catch (deleteEx: Exception) {
                    Timber.e(deleteEx, "SmbClient.deleteFile: FAILED - Exception during rm() call")
                    Timber.e("SmbClient.deleteFile: Delete error type: ${deleteEx.javaClass.name}")
                    Timber.e("SmbClient.deleteFile: Delete error message: ${deleteEx.message}")
                    deleteEx.cause?.let { cause ->
                        Timber.e("SmbClient.deleteFile: Cause: ${cause.javaClass.name} - ${cause.message}")
                    }
                    SmbResult.Error("Delete operation failed: ${deleteEx.message}", deleteEx)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SmbClient.deleteFile: EXCEPTION - Failed to establish connection or execute delete")
            Timber.e("SmbClient.deleteFile: Exception type: ${e.javaClass.name}")
            Timber.e("SmbClient.deleteFile: Exception message: ${e.message}")
            e.cause?.let { cause ->
                Timber.e("SmbClient.deleteFile: Cause: ${cause.javaClass.name} - ${cause.message}")
            }
            SmbResult.Error("Failed to delete file: ${e.message}", e)
        }
    }

    /**
     * Delete directory recursively on SMB share
     */
    suspend fun deleteDirectory(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<Unit> {
        Timber.d("SmbClient.deleteDirectory: START - remotePath='$remotePath'")
        
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                if (!share.fileExists(remotePath.trim('/', '\\'))) {
                    Timber.w("SmbClient.deleteDirectory: Directory does not exist: $remotePath")
                    return@withConnection SmbResult.Success(Unit)
                }
                
                try {
                    share.rmdir(remotePath.trim('/', '\\'), true)
                    Timber.i("SmbClient.deleteDirectory: SUCCESS - Directory deleted: $remotePath")
                    SmbResult.Success(Unit)
                } catch (deleteEx: Exception) {
                    Timber.e(deleteEx, "SmbClient.deleteDirectory: FAILED - ${deleteEx.message}")
                    SmbResult.Error("Delete directory failed: ${deleteEx.message}", deleteEx)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SmbClient.deleteDirectory: EXCEPTION - ${e.message}")
            SmbResult.Error("Failed to delete directory: ${e.message}", e)
        }
    }

    /** Rename file on SMB share. Delegates to SmbFileMutationCoordinator. */
    suspend fun renameFile(
        connectionInfo: SmbConnectionInfo,
        oldPath: String,
        newName: String
    ): SmbResult<Unit> = mutations.renameFile(connectionInfo, oldPath, newName)

    /** Move file on SMB share (copy + delete). Delegates to SmbFileMutationCoordinator. */
    suspend fun moveFile(
        connectionInfo: SmbConnectionInfo,
        sourcePath: String,
        destinationPath: String
    ): SmbResult<Unit> = mutations.moveFile(connectionInfo, sourcePath, destinationPath)

    /**
     * Create directory on SMB share (recursively).
     * Race-tolerant mkdir-p - delegates to SmbFileMutationCoordinator.
     */
    suspend fun createDirectory(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<Unit> {
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                mutations.ensureSmbDirectoryExists(share, remotePath.trim('/', '\\'))
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
            connectionManager.withConnection(connectionInfo) { share ->
                val exists = share.fileExists(remotePath.trim('/', '\\'))
                SmbResult.Success(exists)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check if path exists on SMB")
            SmbResult.Error("Failed to check path: ${e.message}", e)
        }
    }

    /**
     * Retrieve file metadata for a single SMB path without listing entire directories.
     * Used as a fallback when cached lists are out of sync with remote storage.
     */
    suspend fun getFileInfo(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<SmbFileInfo> {
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                val fixedPath = remotePath.trim('/', '\\')
                if (!share.fileExists(fixedPath)) {
                    Timber.w("SmbClient.getFileInfo: File not found: $fixedPath")
                    return@withConnection SmbResult.Error("File not found: $fixedPath")
                }

                val info = try {
                    share.getFileInformation(fixedPath)
                } catch (infoError: Exception) {
                    Timber.e(infoError, "SmbClient.getFileInfo: Failed to read metadata for $remotePath")
                    return@withConnection SmbResult.Error(
                        "Failed to read file info: ${infoError.message}",
                        infoError
                    )
                }

                val name = remotePath.substringAfterLast('/').ifEmpty { remotePath }
                val size = info.standardInformation?.endOfFile ?: 0L
                val lastModified = info.basicInformation?.lastWriteTime?.toEpochMillis()
                    ?: System.currentTimeMillis()

                SmbResult.Success(
                    SmbFileInfo(
                        name = name,
                        path = remotePath,
                        isDirectory = false,
                        size = size,
                        lastModified = lastModified
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get SMB file info for $remotePath")
            SmbResult.Error("Failed to get file info: ${e.message}", e)
        }
    }

    private fun getUserFriendlyMessage(exception: Exception): String =
        SmbClientErrorFormatter.getUserFriendlyMessage(exception)
    
    private fun buildDiagnosticMessage(exception: Exception, connectionInfo: SmbConnectionInfo): String =
        SmbClientErrorFormatter.buildDiagnosticMessage(exception, connectionInfo)

    /**
     * Check write permission by attempting to create and write a test file.
     * Creates .fms_write_test_<timestamp>.tmp in the specified path, then deletes it.
     * 
     * @param connectionInfo SMB connection parameters
     * @param remotePath Path within the share to test (empty string for share root)
     * @return SmbResult.Success(true) if write operations succeed, Success(false) or Error otherwise
     */
    suspend fun checkWritePermission(
        connectionInfo: SmbConnectionInfo,
        remotePath: String = ""
    ): SmbResult<Boolean> {
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                // Create test file name with timestamp to avoid conflicts
                val testFileName = ".fms_write_test_${System.currentTimeMillis()}.tmp"
                val testFilePath = if (remotePath.isEmpty()) {
                    testFileName
                } else {
                    "${remotePath.trimEnd('/')}/$testFileName"
                }
                
                Timber.d("Testing write permission: $testFilePath")
                
                var file: File? = null
                val canWrite = try {
                    // Test 1: Try to create the test file
                    file = share.openFile(
                        testFilePath,
                        EnumSet.of(AccessMask.GENERIC_WRITE),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_CREATE,
                        null
                    )
                    
                    // Test 2: Try to write some data to verify write access
                    file.outputStream.use { output ->
                        output.write("test".toByteArray())
                        output.flush()
                    }
                    
                    Timber.d("Write test successful")
                    true
                } catch (e: Exception) {
                    Timber.w("Write test failed: ${e.message}")
                    false
                } finally {
                    // Test 3: Try to delete the test file (cleanup)
                    try {
                        file?.close()
                        share.rm(testFilePath)
                        Timber.d("Test file cleaned up")
                    } catch (e: Exception) {
                        Timber.w("Failed to cleanup test file: ${e.message}")
                    }
                }
                
                SmbResult.Success(canWrite)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking write permission")
            SmbResult.Error("Failed to check write permission: ${e.message}", e)
        }
    }

    /**
     * Close client and cleanup resources
     */
    fun close() {
        connectionManager.close()
    }
    
    /**
     * Clear connection pool (used when refreshing resources or on connection issues)
     */
    fun clearConnectionPool() {
        connectionManager.clearConnectionPool()
    }
    
    /**
     * Force full reset: close all connections and reset clients
     * Used when user manually refreshes or encounters persistent issues
     */
    fun forceFullReset() {
        connectionManager.forceFullReset()
    }

    /**
     * Open InputStream for reading file from SMB.
     * Caller is responsible for closing the stream.
     * The stream wrapper ensures the underlying SMB file handle is closed.
     */
    suspend fun openInputStream(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<InputStream> {
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                val file = share.openFile(
                    remotePath,
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )
                
                // Return wrapper that closes the file when stream is closed
                val inputStream = object : java.io.FilterInputStream(file.inputStream) {
                    override fun close() {
                        try {
                            super.close()
                        } catch (e: Exception) {
                            Timber.w(e, "Error closing SMB input stream")
                        } finally {
                            try {
                                // CRITICAL FIX: Clear interruption status before closing file handle.
                                // If the thread is interrupted (e.g. Coil cancellation), smbj will fail to send
                                // the Close packet and might tear down the connection.
                                // We save the status to restore it later if needed, but for now we want the Close to succeed.
                                val interrupted = Thread.interrupted()
                                file.close()
                                if (interrupted) {
                                    Thread.currentThread().interrupt() // Restore status
                                }
                            } catch (e: Exception) {
                                Timber.w(e, "Error closing SMB file handle")
                            }
                        }
                    }
                }
                
                SmbResult.Success(inputStream)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to open SMB input stream")
            SmbResult.Error("Failed to open stream: ${e.message}", e)
        }
    }
}
